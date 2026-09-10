create schema if not exists private;
comment on schema private is 'Server-only objects that are not exposed through the Data API';
revoke all on schema private from public, anon, authenticated;

create table private.devices_legacy_20260904 as
select * from public.devices;
alter table private.devices_legacy_20260904
  add column archived_at timestamptz not null default now();
comment on table private.devices_legacy_20260904 is
  'Recoverable snapshot of public.devices before the 2026-09-04 ownership migration';
revoke all on table private.devices_legacy_20260904 from public, anon, authenticated;

truncate table
  public.online_devices,
  public.user_feedbacks,
  public.analytics_events,
  public.app_crash_logs,
  public.devices
restart identity;

delete from public.config
where config_key in ('gitee_access_token', 'music_api_auth');

delete from public.config
where config_key is null or btrim(config_key) = '';

update public.config
set config_value = ''
where config_value is null;

alter table public.config
  alter column config_key set not null,
  alter column config_value set not null,
  add constraint config_config_key_key unique (config_key),
  add constraint config_key_length_check
    check (char_length(config_key) between 1 and 128),
  add constraint config_value_length_check
    check (char_length(config_value) <= 4096);

alter table public.devices
  add column auth_user_id uuid not null default auth.uid()
    references auth.users(id) on delete cascade,
  add constraint devices_device_id_length_check
    check (char_length(device_id) between 1 and 128),
  add constraint devices_text_lengths_check
    check (
      char_length(coalesce(name, '')) <= 256
      and char_length(coalesce(model, '')) <= 256
      and char_length(coalesce(oem_name, '')) <= 256
      and char_length(coalesce(os_name, '')) <= 128
      and char_length(coalesce(os_version, '')) <= 128
      and char_length(coalesce(app_version, '')) <= 64
      and char_length(coalesce(app_build, '')) <= 128
      and char_length(coalesce(app_namespace, '')) <= 256
      and char_length(coalesce(build_id, '')) <= 256
      and char_length(coalesce(cpu_arch, '')) <= 64
      and char_length(coalesce(locale, '')) <= 64
      and char_length(coalesce(os_api, '')) <= 64
      and char_length(coalesce(timezone_offset, '')) <= 32
      and char_length(coalesce(carrier_name, '')) <= 256
      and char_length(coalesce(carrier_country, '')) <= 16
      and char_length(coalesce(screen_size, '')) <= 64
    );

alter table public.online_devices
  drop constraint online_devices_device_id_fkey,
  add column auth_user_id uuid not null default auth.uid()
    references auth.users(id) on delete cascade;

alter table public.user_feedbacks
  drop constraint user_feedbacks_device_id_fkey,
  add column auth_user_id uuid not null default auth.uid()
    references auth.users(id) on delete cascade,
  alter column device_id set not null,
  add constraint user_feedbacks_content_length_check
    check (char_length(content) between 1 and 5000),
  add constraint user_feedbacks_type_length_check
    check (char_length(feedback_type) between 1 and 64),
  add constraint user_feedbacks_contact_length_check
    check (
      char_length(coalesce(contact_email, '')) <= 320
      and char_length(coalesce(contact_phone, '')) <= 64
      and char_length(coalesce(attachment_url, '')) <= 2048
    );

alter table public.analytics_events
  drop constraint analytics_events_device_id_fkey,
  add column auth_user_id uuid not null default auth.uid()
    references auth.users(id) on delete cascade,
  alter column device_id set not null,
  add constraint analytics_events_name_length_check
    check (char_length(event_name) between 1 and 128),
  add constraint analytics_events_type_length_check
    check (char_length(event_type) between 1 and 64),
  add constraint analytics_events_text_lengths_check
    check (
      char_length(coalesce(distribution_group_id, '')) <= 128
      and char_length(coalesce(data_residency_region, '')) <= 64
    ),
  add constraint analytics_events_properties_size_check
    check (pg_column_size(coalesce(properties, '{}'::jsonb)) <= 32768),
  add constraint analytics_events_typed_properties_size_check
    check (pg_column_size(coalesce(typed_properties, '[]'::jsonb)) <= 65536);

alter table public.app_crash_logs
  drop constraint app_crash_logs_device_id_fkey,
  add column auth_user_id uuid not null default auth.uid()
    references auth.users(id) on delete cascade;

drop trigger if exists "NewDevice" on public.devices;
drop trigger if exists "NewDeviceToKiki" on public.devices;
drop trigger if exists "NewDeviceToLark" on public.devices;
drop trigger if exists trigger_auto_create_event_name on public.analytics_events;
drop trigger if exists trigger_sync_build_type on public.analytics_events;

drop function if exists public.auto_create_event_name();
drop function if exists public.sync_build_type();

create or replace function public.update_device_timestamp()
returns trigger
language plpgsql
set search_path = ''
as $function$
begin
  new.updated_at = now();
  return new;
end;
$function$;

create or replace function public.update_modified_column()
returns trigger
language plpgsql
set search_path = ''
as $function$
begin
  new.updated_at = now();
  return new;
end;
$function$;

revoke all on function public.update_device_timestamp() from public, anon, authenticated;
revoke all on function public.update_modified_column() from public, anon, authenticated;

create or replace function private.can_insert_feedback()
returns boolean
language sql
stable
security definer
set search_path = ''
as $function$
  select
    (select auth.uid()) is not null
    and (
      select count(*)
      from public.user_feedbacks feedback
      where feedback.auth_user_id = (select auth.uid())
        and feedback.created_at >= now() - interval '1 day'
    ) < 5;
$function$;

create or replace function private.can_insert_analytics_event()
returns boolean
language sql
stable
security definer
set search_path = ''
as $function$
  select
    (select auth.uid()) is not null
    and (
      select count(*)
      from public.analytics_events event
      where event.auth_user_id = (select auth.uid())
        and event.created_at >= now() - interval '1 day'
    ) < 500;
$function$;

revoke all on function private.can_insert_feedback() from public, anon, authenticated;
revoke all on function private.can_insert_analytics_event() from public, anon, authenticated;
grant usage on schema private to authenticated;
grant execute on function private.can_insert_feedback() to authenticated;
grant execute on function private.can_insert_analytics_event() to authenticated;

do $block$
declare
  policy_row record;
begin
  for policy_row in
    select tablename, policyname
    from pg_policies
    where schemaname = 'public'
  loop
    execute format(
      'drop policy %I on public.%I',
      policy_row.policyname,
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
alter table public.proxy_config enable row level security;
alter table public.user_feedbacks enable row level security;

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

create policy "read countries"
on public.countries
for select
to anon, authenticated
using (true);

create policy "read event names"
on public.event_names
for select
to anon, authenticated
using (true);

create policy "read hello values"
on public.hello
for select
to anon, authenticated
using (true);

create policy "read own device"
on public.devices
for select
to authenticated
using (
  (select auth.uid()) is not null
  and auth_user_id = (select auth.uid())
  and device_id = (select auth.uid())::text
);

create policy "insert own device"
on public.devices
for insert
to authenticated
with check (
  (select auth.uid()) is not null
  and auth_user_id = (select auth.uid())
  and device_id = (select auth.uid())::text
);

create policy "update own device"
on public.devices
for update
to authenticated
using (
  (select auth.uid()) is not null
  and auth_user_id = (select auth.uid())
  and device_id = (select auth.uid())::text
)
with check (
  (select auth.uid()) is not null
  and auth_user_id = (select auth.uid())
  and device_id = (select auth.uid())::text
);

create policy "insert own feedback within quota"
on public.user_feedbacks
for insert
to authenticated
with check (
  (select auth.uid()) is not null
  and auth_user_id = (select auth.uid())
  and device_id = (select auth.uid())::text
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
  and private.can_insert_analytics_event()
);

revoke all on all tables in schema public from anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;

grant select on public.config to anon, authenticated;
grant select on public.countries to anon, authenticated;
grant select on public.event_names to anon, authenticated;
grant select on public.hello to anon, authenticated;

grant select on public.devices to authenticated;
grant insert (
  device_id,
  model,
  os_name,
  os_version,
  app_version,
  build_type,
  app_build,
  app_namespace,
  build_id,
  cpu_arch,
  locale,
  name,
  oem_name,
  os_api,
  timezone_offset,
  carrier_name,
  carrier_country,
  screen_size
) on public.devices to authenticated;
grant update (
  device_id,
  model,
  os_name,
  os_version,
  app_version,
  build_type,
  app_build,
  app_namespace,
  build_id,
  cpu_arch,
  locale,
  name,
  oem_name,
  os_api,
  timezone_offset,
  carrier_name,
  carrier_country,
  screen_size
) on public.devices to authenticated;

grant insert (
  device_id,
  feedback_type,
  content,
  rating,
  attachment_url,
  contact_email,
  contact_phone
) on public.user_feedbacks to authenticated;
grant usage, select on sequence public.user_feedbacks_id_seq to authenticated;

grant insert (
  id,
  event_name,
  event_type,
  session_id,
  distribution_group_id,
  device_id,
  data_residency_region,
  build_type,
  properties,
  typed_properties
) on public.analytics_events to authenticated;

alter default privileges for role postgres in schema public
  revoke all on tables from anon, authenticated, service_role;
alter default privileges for role postgres in schema public
  revoke all on sequences from anon, authenticated, service_role;
alter default privileges for role postgres in schema public
  revoke all on functions from public, anon, authenticated, service_role;

comment on column public.devices.auth_user_id is
  'Supabase Auth owner; device_id must equal this UUID in client-originated rows';
comment on column public.user_feedbacks.auth_user_id is
  'Supabase Auth owner set by the database default and enforced by RLS';
comment on column public.analytics_events.auth_user_id is
  'Supabase Auth owner set by the database default and enforced by RLS';
