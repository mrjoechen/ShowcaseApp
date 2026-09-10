alter table private.devices_legacy_20260904
  add constraint devices_legacy_20260904_pkey primary key (device_id);

create index devices_auth_user_id_idx
  on public.devices (auth_user_id);

create index online_devices_auth_user_id_idx
  on public.online_devices (auth_user_id);

create index user_feedbacks_auth_user_created_at_idx
  on public.user_feedbacks (auth_user_id, created_at desc);

create index analytics_events_auth_user_created_at_idx
  on public.analytics_events (auth_user_id, created_at desc);

create index app_crash_logs_auth_user_id_idx
  on public.app_crash_logs (auth_user_id);
