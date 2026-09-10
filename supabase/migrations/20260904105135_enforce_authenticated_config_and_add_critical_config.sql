-- Ordinary service configuration is readable only after Supabase Auth has issued a user JWT.
-- Anonymous sign-ins use the authenticated database role and are intentionally included.
drop policy if exists "read allowlisted public config" on public.config;

revoke all privileges on table public.config from public, anon, authenticated;

create policy "authenticated users read allowlisted config"
on public.config
for select
to authenticated
using (
  (select auth.uid()) is not null
  and config_key in (
    'github_proxy',
    'music_api_baseurl',
    'online_track_interval'
  )
);

grant select (config_key, config_value)
on public.config
to authenticated;

-- Make the authenticated user UUID the canonical device ID for every client-originated table.
-- This is enforced structurally as well as in RLS/RPC checks so future policies cannot detach a
-- record from its owner accidentally.
update public.devices
set device_id = auth_user_id::text
where device_id is distinct from auth_user_id::text;

update public.online_devices
set device_id = auth_user_id::text
where device_id is distinct from auth_user_id::text;

update public.analytics_events
set device_id = auth_user_id::text
where device_id is distinct from auth_user_id::text;

update public.app_crash_logs
set device_id = auth_user_id::text
where device_id is distinct from auth_user_id::text;

update public.user_feedbacks
set device_id = auth_user_id::text
where device_id is distinct from auth_user_id::text;

alter table public.devices
  alter column device_id set default (auth.uid())::text,
  add constraint devices_owner_device_match_check
    check (device_id = auth_user_id::text);

alter table public.online_devices
  alter column device_id set default (auth.uid())::text,
  add constraint online_devices_owner_device_match_check
    check (device_id = auth_user_id::text);

alter table public.analytics_events
  add constraint analytics_events_owner_device_match_check
    check (device_id = auth_user_id::text);

alter table public.app_crash_logs
  alter column device_id set default (auth.uid())::text,
  alter column device_id set not null,
  add constraint app_crash_logs_owner_device_match_check
    check (device_id = auth_user_id::text);

alter table public.user_feedbacks
  add constraint user_feedbacks_owner_device_match_check
    check (device_id = auth_user_id::text);

comment on column public.devices.device_id is
  'Canonical device ID derived from the authenticated Supabase user UUID';
comment on column public.online_devices.device_id is
  'Canonical device ID derived from the authenticated Supabase user UUID';
comment on column public.analytics_events.device_id is
  'Canonical device ID derived from the authenticated Supabase user UUID';
comment on column public.app_crash_logs.device_id is
  'Canonical device ID derived from the authenticated Supabase user UUID';
comment on column public.user_feedbacks.device_id is
  'Canonical device ID derived from the authenticated Supabase user UUID';

-- Critical configuration deliberately mirrors public.config, but has no browser/mobile Data API
-- privileges or RLS policies. The authenticated Edge Function reads it with its server-only role.
create table public.critical_config (
  id uuid not null default gen_random_uuid(),
  created_at timestamp with time zone not null default now(),
  config_key text not null default ''::text,
  config_value text not null default ''::text,
  constraint critical_config_pkey primary key (id),
  constraint critical_config_config_key_key unique (config_key),
  constraint critical_config_key_length_check
    check (char_length(config_key) between 1 and 128),
  constraint critical_config_value_length_check
    check (char_length(config_value) <= 4096)
);

alter table public.critical_config enable row level security;

revoke all privileges on table public.critical_config
from public, anon, authenticated;
grant select on table public.critical_config to service_role;

comment on table public.critical_config is
  'Server-managed critical configuration; client roles have no direct access and values are returned only by an authenticated Edge Function';
