-- Applied to Showcase as migration 20260905234749. Preserve old rows and the existing 11-argument register_device RPC.
-- device_id is a persistent UUID; auth_user_id is its immutable Supabase Auth owner.
set lock_timeout = '5s';
set statement_timeout = '120s';

alter table public.devices drop constraint devices_owner_device_match_check;
alter table public.online_devices drop constraint online_devices_owner_device_match_check;
alter table public.analytics_events drop constraint analytics_events_owner_device_match_check;
alter table public.user_feedbacks drop constraint user_feedbacks_owner_device_match_check;
alter table public.app_crash_logs drop constraint app_crash_logs_owner_device_match_check;

alter table public.devices add constraint devices_device_owner_key unique (device_id, auth_user_id);
alter table public.online_devices add constraint online_devices_bound_owner_fkey
  foreign key (device_id, auth_user_id) references public.devices(device_id, auth_user_id) on delete cascade;
alter table public.analytics_events add constraint analytics_events_bound_owner_fkey
  foreign key (device_id, auth_user_id) references public.devices(device_id, auth_user_id) on delete cascade;
alter table public.user_feedbacks add constraint user_feedbacks_bound_owner_fkey
  foreign key (device_id, auth_user_id) references public.devices(device_id, auth_user_id) on delete cascade;
alter table public.app_crash_logs add constraint app_crash_logs_bound_owner_fkey
  foreign key (device_id, auth_user_id) references public.devices(device_id, auth_user_id) on delete cascade;

create index online_devices_bound_owner_idx on public.online_devices(device_id, auth_user_id);
create index analytics_events_bound_owner_idx on public.analytics_events(device_id, auth_user_id);
create index user_feedbacks_bound_owner_idx on public.user_feedbacks(device_id, auth_user_id);
create index app_crash_logs_bound_owner_idx on public.app_crash_logs(device_id, auth_user_id);

create function private.enforce_durable_device_owner()
returns trigger language plpgsql security invoker set search_path = '' as $function$
begin
  if new.device_id is distinct from old.device_id or new.auth_user_id is distinct from old.auth_user_id then
    raise exception 'Device identity and ownership are immutable' using errcode = '42501';
  end if;
  return new;
end;
$function$;
revoke all on function private.enforce_durable_device_owner() from public, anon, authenticated;
create trigger enforce_durable_device_owner before update on public.devices
  for each row execute function private.enforce_durable_device_owner();

-- A client may propose a UUID but cannot supply/transfer its owner. The unique key and
-- conditional upsert make concurrent claims atomic, rather than trusting client storage.
create function public.bind_device(p_device_id text, p_build_type text)
returns void language plpgsql security definer set search_path = '' as $function$
declare
  owner_uid uuid := auth.uid();
  affected_rows integer;
begin
  if owner_uid is null then raise exception 'Authentication required' using errcode = '42501'; end if;
  if p_device_id is null or p_device_id !~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' then
    raise exception 'A UUIDv4 device_id is required' using errcode = '22023';
  end if;
  if p_build_type is null or p_build_type not in ('debug', 'beta', 'release') then
    raise exception 'Invalid build type' using errcode = '22023';
  end if;
  -- UID-shaped legacy IDs remain reserved for that user even before their first registration.
  if p_device_id <> owner_uid::text and exists(select 1 from auth.users where id = p_device_id::uuid) then
    raise exception 'Device ownership conflict' using errcode = '23505';
  end if;
  insert into public.devices(device_id, auth_user_id, build_type)
    values(p_device_id, owner_uid, p_build_type::public.build_type)
  on conflict(device_id) do update set device_id = excluded.device_id
    where public.devices.auth_user_id = owner_uid;
  get diagnostics affected_rows = row_count;
  if affected_rows <> 1 then raise exception 'Device ownership conflict' using errcode = '23505'; end if;
end;
$function$;
revoke all on function public.bind_device(text, text) from public, anon, authenticated;
grant execute on function public.bind_device(text, text) to authenticated;

-- New clients use this overload with an explicit bound device_id. All twelve arguments
-- are required, so it cannot be confused with the existing eleven-argument legacy RPC.
create function public.register_device(
  p_device_id text, p_model text, p_oem_name text, p_os_name text, p_os_version text,
  p_locale text, p_app_version text, p_app_namespace text, p_app_build text,
  p_build_type text, p_os_api text, p_cpu_arch text
)
returns void language plpgsql security definer set search_path = '' as $function$
begin
  perform public.bind_device(p_device_id, p_build_type);
  if
    char_length(coalesce(p_model, '')) > 256 or char_length(coalesce(p_oem_name, '')) > 256
    or char_length(coalesce(p_os_name, '')) > 128 or char_length(coalesce(p_os_version, '')) > 128
    or char_length(coalesce(p_locale, '')) > 16 or char_length(coalesce(p_app_version, '')) > 64
    or char_length(coalesce(p_app_namespace, '')) > 256 or char_length(coalesce(p_app_build, '')) > 128
    or char_length(coalesce(p_os_api, '')) > 64 or char_length(coalesce(p_cpu_arch, '')) > 64
  then raise exception 'Device metadata is too long' using errcode = '22023'; end if;
  update public.devices set
    model = nullif(btrim(p_model), ''), oem_name = nullif(btrim(p_oem_name), ''),
    os_name = nullif(btrim(p_os_name), ''), os_version = nullif(btrim(p_os_version), ''),
    locale = nullif(lower(btrim(p_locale)), ''), app_version = nullif(btrim(p_app_version), ''),
    app_namespace = nullif(btrim(p_app_namespace), ''), app_build = nullif(btrim(p_app_build), ''),
    build_type = p_build_type::public.build_type, os_api = nullif(btrim(p_os_api), ''),
    cpu_arch = nullif(btrim(p_cpu_arch), '')
  where device_id = p_device_id and auth_user_id = (select auth.uid());
end;
$function$;
revoke all on function public.register_device(text,text,text,text,text,text,text,text,text,text,text,text)
  from public, anon, authenticated;
grant execute on function public.register_device(text,text,text,text,text,text,text,text,text,text,text,text)
  to authenticated;

-- Only the ownership columns are readable, and only on the caller's own device rows.
-- No client INSERT/UPDATE/DELETE grant on devices, and no new state-table read grants.
create policy "read own device binding" on public.devices for select to authenticated
  using (auth_user_id = (select auth.uid()));
grant select(device_id, auth_user_id) on public.devices to authenticated;

alter policy "insert own feedback within quota" on public.user_feedbacks with check (
  (select auth.uid()) is not null and auth_user_id = (select auth.uid())
  and exists(select 1 from public.devices d where d.device_id = user_feedbacks.device_id and d.auth_user_id = (select auth.uid()))
  and feedback_type = 'user_feedback' and private.can_insert_feedback()
);
alter policy "insert own analytics within quota" on public.analytics_events with check (
  (select auth.uid()) is not null and auth_user_id = (select auth.uid())
  and exists(select 1 from public.devices d where d.device_id = analytics_events.device_id and d.auth_user_id = (select auth.uid()))
  and event_name ~ '^[A-Za-z0-9][A-Za-z0-9_.-]*$' and event_type ~ '^[A-Za-z0-9][A-Za-z0-9_.-]*$'
  and private.can_insert_analytics_event()
);
grant insert(device_id) on public.user_feedbacks, public.analytics_events to authenticated;

comment on column public.devices.device_id is 'Persistent client UUID, immutably bound to auth_user_id by bind_device; never an authentication credential';
comment on column public.devices.auth_user_id is 'Immutable verified Supabase Auth owner, equivalent to owner_id in the reference Android app';
comment on column public.online_devices.device_id is 'Registered device UUID; composite foreign key enforces owner association';
comment on column public.analytics_events.device_id is 'Registered device UUID; composite foreign key and RLS enforce owner association';
comment on column public.user_feedbacks.device_id is 'Registered device UUID; composite foreign key and RLS enforce owner association';
comment on column public.app_crash_logs.device_id is 'Registered device UUID; composite foreign key enforces owner association';

notify pgrst, 'reload schema';
