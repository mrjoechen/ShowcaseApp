-- Run with the Showcase project's privileged SQL connection. Every fixture is rolled back,
-- including when an assertion fails; existing users and their rows are never touched.
do $tests$
declare
  user_a uuid := gen_random_uuid();
  user_b uuid := gen_random_uuid();
  device_a text := gen_random_uuid()::text;
  device_b text := gen_random_uuid()::text;
  unknown_device text := gen_random_uuid()::text;
  visible_count integer;
begin
  begin
    insert into auth.users(id, aud, role, is_anonymous)
      values (user_a, 'authenticated', 'authenticated', true),
             (user_b, 'authenticated', 'authenticated', true);
    perform set_config('request.jwt.claims', json_build_object('sub', user_a, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';

    perform public.bind_device(device_a, 'debug');
    perform public.bind_device(device_a, 'debug');
    select count(*) into visible_count from public.devices where device_id = device_a and auth_user_id = user_a;
    if visible_count <> 1 then raise exception 'Same UID must reuse one bound device'; end if;

    -- Both RPC signatures remain callable; Web intentionally reports no CPU architecture.
    perform public.register_device(device_a, 'Web', '', 'Web', '', 'en', 'test', 'showcase', 'test', 'debug', '', null);
    perform public.register_device('Web', '', 'Web', '', 'en', 'test', 'showcase', 'test', 'debug', '', null);
    perform public.register_device('Web', '', 'Web', '', 'en', 'test', 'showcase', 'test', 'debug', '', null);
    select count(*) into visible_count from public.devices where device_id = user_a::text and auth_user_id = user_a;
    if visible_count <> 1 then raise exception 'Legacy registration must reuse its UID-shaped device'; end if;
    insert into public.user_feedbacks(feedback_type, content)
      values ('user_feedback', 'Legacy default device fixture');

    insert into public.analytics_events(device_id, event_name, event_type, session_id, build_type)
      values (device_a, 'binding_test', 'event', gen_random_uuid(), 'debug');
    insert into public.user_feedbacks(device_id, feedback_type, content)
      values (device_a, 'user_feedback', 'Binding test fixture');
    begin
      insert into public.user_feedbacks(device_id, feedback_type, content)
        values (unknown_device, 'user_feedback', 'Must be rejected');
      raise exception 'Unregistered device was accepted';
    exception when insufficient_privilege or foreign_key_violation then null;
    end;

    perform set_config('request.jwt.claims', json_build_object('sub', user_b, 'role', 'authenticated')::text, true);
    perform public.bind_device(device_b, 'debug');
    select count(*) into visible_count from public.devices where device_id = device_a;
    if visible_count <> 0 then raise exception 'Other owner device is visible'; end if;
    begin
      perform public.bind_device(device_a, 'debug');
      raise exception 'Another UID claimed the device';
    exception when unique_violation then null;
    end;
    begin
      perform public.register_device(device_a, 'Web', '', 'Web', '', 'en', 'test', 'showcase', 'test', 'debug', '', null);
      raise exception 'Another UID updated device metadata';
    exception when unique_violation then null;
    end;
    begin
      insert into public.analytics_events(device_id, event_name, event_type, session_id, build_type)
        values (device_a, 'forbidden', 'event', gen_random_uuid(), 'debug');
      raise exception 'Another UID wrote device state';
    exception when insufficient_privilege or foreign_key_violation then null;
    end;
    begin
      insert into public.user_feedbacks(device_id, auth_user_id, feedback_type, content)
        values (device_a, user_a, 'user_feedback', 'Forged owner');
      raise exception 'Client specified an owner';
    exception when insufficient_privilege then null;
    end;
    begin
      perform public.bind_device(user_a::text, 'debug');
      raise exception 'Another user reserved a legacy UID device';
    exception when unique_violation then null;
    end;
    begin
      perform public.bind_device('invalid-device', 'debug');
      raise exception 'Malformed ID was accepted';
    exception when invalid_parameter_value then null;
    end;

    execute 'reset role';
    begin
      update public.devices set auth_user_id = user_b where device_id = device_a;
      raise exception 'Existing ownership was mutated';
    exception when insufficient_privilege then null;
    end;
    begin
      insert into public.analytics_events(device_id, auth_user_id, event_name, event_type, session_id, build_type)
        values (device_a, user_b, 'forbidden_admin_pair', 'event', gen_random_uuid(), 'debug');
      raise exception 'Composite ownership FK was bypassed';
    exception when foreign_key_violation then null;
    end;

    perform set_config('request.jwt.claims', '{}', true);
    execute 'set local role anon';
    begin
      perform public.bind_device(unknown_device, 'debug');
      raise exception 'Unauthenticated registration was accepted';
    exception when insufficient_privilege then null;
    end;
    execute 'reset role';

    -- Intentional subtransaction rollback: removes users, devices, events and feedback fixtures.
    raise exception using errcode = 'P0004', message = 'ROLLBACK_BINDING_TEST_FIXTURES';
  exception when sqlstate 'P0004' then null;
  end;
end
$tests$;
select 'durable device ownership checks passed; fixtures rolled back' as result;
