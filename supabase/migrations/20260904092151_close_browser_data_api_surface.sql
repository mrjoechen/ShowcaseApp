alter table private.devices_legacy_20260904 enable row level security;
revoke all on table private.devices_legacy_20260904 from public, anon, authenticated;

do $block$
declare
  policy_row record;
begin
  for policy_row in
    select schemaname, tablename, policyname
    from pg_policies
    where schemaname = 'public'
      and tablename = any (
        array[
          'analytics_events',
          'app_crash_logs',
          'config',
          'countries',
          'devices',
          'event_names',
          'hello',
          'online_devices',
          'user_feedbacks'
        ]
      )
  loop
    execute format(
      'drop policy %I on %I.%I',
      policy_row.policyname,
      policy_row.schemaname,
      policy_row.tablename
    );
  end loop;
end;
$block$;

alter table public.analytics_events enable row level security;
alter table public.app_crash_logs enable row level security;
alter table public.config enable row level security;
alter table public.countries enable row level security;
alter table public.devices enable row level security;
alter table public.event_names enable row level security;
alter table public.hello enable row level security;
alter table public.online_devices enable row level security;
alter table public.user_feedbacks enable row level security;

revoke all on table
  public.analytics_events,
  public.app_crash_logs,
  public.config,
  public.countries,
  public.devices,
  public.event_names,
  public.hello,
  public.online_devices,
  public.user_feedbacks
from anon, authenticated;

do $block$
declare
  privilege_row record;
begin
  for privilege_row in
    select
      grantee,
      table_schema,
      table_name,
      column_name,
      privilege_type
    from information_schema.column_privileges
    where grantee in ('anon', 'authenticated')
      and table_schema = 'public'
      and table_name = any (
        array[
          'analytics_events',
          'app_crash_logs',
          'config',
          'countries',
          'devices',
          'event_names',
          'hello',
          'online_devices',
          'user_feedbacks'
        ]
      )
  loop
    execute format(
      'revoke %s (%I) on table %I.%I from %I',
      privilege_row.privilege_type,
      privilege_row.column_name,
      privilege_row.table_schema,
      privilege_row.table_name,
      privilege_row.grantee
    );
  end loop;
end;
$block$;

revoke all on all sequences in schema public from anon, authenticated;

alter table public.user_feedbacks
  alter column device_id set default (auth.uid())::text;

alter table public.analytics_events
  alter column device_id set default (auth.uid())::text;

create policy "read allowlisted public config"
on public.config
for select
to anon, authenticated
using (
  config_key in (
    'github_proxy',
    'music_api_baseurl',
    'online_track_interval'
  )
);

create policy "insert own feedback within quota"
on public.user_feedbacks
for insert
to authenticated
with check (
  (select auth.uid()) is not null
  and auth_user_id = (select auth.uid())
  and device_id = (select auth.uid())::text
  and feedback_type = 'user_feedback'
  and private.can_insert_feedback()
);

create policy "insert own analytics within quota"
on public.analytics_events
for insert
to authenticated
with check (
  (select auth.uid()) is not null
  and auth_user_id = (select auth.uid())
  and device_id = (select auth.uid())::text
  and event_name ~ '^[A-Za-z0-9][A-Za-z0-9_.-]*$'
  and event_type ~ '^[A-Za-z0-9][A-Za-z0-9_.-]*$'
  and private.can_insert_analytics_event()
);

grant select (config_key, config_value)
on public.config
to anon, authenticated;

grant insert (
  feedback_type,
  content,
  rating,
  contact_email
)
on public.user_feedbacks
to authenticated;

grant usage
on sequence public.user_feedbacks_id_seq
to authenticated;

grant insert (
  event_name,
  event_type,
  session_id,
  build_type
)
on public.analytics_events
to authenticated;

create or replace function public.register_device(
  p_model text,
  p_oem_name text,
  p_os_name text,
  p_os_version text,
  p_locale text,
  p_app_version text,
  p_app_namespace text,
  p_app_build text,
  p_build_type text,
  p_os_api text,
  p_cpu_arch text
)
returns void
language plpgsql
security definer
set search_path = ''
as $function$
declare
  authenticated_user_id uuid := auth.uid();
  affected_rows integer;
begin
  if authenticated_user_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_build_type is null or p_build_type not in ('debug', 'beta', 'release') then
    raise exception 'Invalid build type' using errcode = '22023';
  end if;

  if
    char_length(coalesce(p_model, '')) > 256
    or char_length(coalesce(p_oem_name, '')) > 256
    or char_length(coalesce(p_os_name, '')) > 128
    or char_length(coalesce(p_os_version, '')) > 128
    or char_length(coalesce(p_locale, '')) > 16
    or char_length(coalesce(p_app_version, '')) > 64
    or char_length(coalesce(p_app_namespace, '')) > 256
    or char_length(coalesce(p_app_build, '')) > 128
    or char_length(coalesce(p_os_api, '')) > 64
    or char_length(coalesce(p_cpu_arch, '')) > 64
  then
    raise exception 'Device metadata is too long' using errcode = '22023';
  end if;

  insert into public.devices (
    device_id,
    auth_user_id,
    model,
    oem_name,
    os_name,
    os_version,
    locale,
    app_version,
    app_namespace,
    app_build,
    build_type,
    os_api,
    cpu_arch
  )
  values (
    authenticated_user_id::text,
    authenticated_user_id,
    nullif(btrim(p_model), ''),
    nullif(btrim(p_oem_name), ''),
    nullif(btrim(p_os_name), ''),
    nullif(btrim(p_os_version), ''),
    nullif(lower(btrim(p_locale)), ''),
    nullif(btrim(p_app_version), ''),
    nullif(btrim(p_app_namespace), ''),
    nullif(btrim(p_app_build), ''),
    p_build_type::public.build_type,
    nullif(btrim(p_os_api), ''),
    nullif(btrim(p_cpu_arch), '')
  )
  on conflict (device_id) do update
  set
    auth_user_id = excluded.auth_user_id,
    model = excluded.model,
    oem_name = excluded.oem_name,
    os_name = excluded.os_name,
    os_version = excluded.os_version,
    locale = excluded.locale,
    app_version = excluded.app_version,
    app_namespace = excluded.app_namespace,
    app_build = excluded.app_build,
    build_type = excluded.build_type,
    os_api = excluded.os_api,
    cpu_arch = excluded.cpu_arch,
    name = null,
    build_id = null,
    timezone_offset = null,
    carrier_name = null,
    carrier_country = null,
    screen_size = null
  where public.devices.auth_user_id = excluded.auth_user_id;

  get diagnostics affected_rows = row_count;
  if affected_rows <> 1 then
    raise exception 'Device ownership conflict' using errcode = '42501';
  end if;
end;
$function$;

revoke all on function public.register_device(
  text,
  text,
  text,
  text,
  text,
  text,
  text,
  text,
  text,
  text,
  text
) from public, anon, authenticated;

grant execute on function public.register_device(
  text,
  text,
  text,
  text,
  text,
  text,
  text,
  text,
  text,
  text,
  text
) to authenticated;

comment on function public.register_device(
  text,
  text,
  text,
  text,
  text,
  text,
  text,
  text,
  text,
  text,
  text
) is
  'Registers only allowlisted low-entropy metadata for auth.uid(); no direct devices table access is required';

comment on table private.devices_legacy_20260904 is
  'Recoverable server-only snapshot; RLS enabled with no client policies or grants';
