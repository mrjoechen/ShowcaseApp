# ShowcaseApp Supabase integration

This app uses **Showcase** (`yoqtbavkkrruavbdujjh`), not Showcase-android. The
initialization and configuration-fetch contract follows the Android reference
implementation in `../showcase/app/src/main/java/com/alpha/showcase/track/Supabase.kt`.
The reference repository was inspected read-only; its project/credentials were not copied.

## Initialization and requests

- One lazily initialized client, a 60-second request timeout, and Auth/Postgrest/Functions.
- Wait for SDK session restoration before deciding to sign in anonymously; serialize
  that decision so concurrent callers do not create multiple users. Later requests can
  retry failed sign-in. An SDK `RefreshFailure` does not count as a signed-out user:
  requests fail closed while SDK recovery retains the original identity. Postgrest
  and Functions require a valid session.
- The local 60-second restoration deadline is an ordinary authentication failure;
  it does not cancel the caller or create a replacement user. Cancellation from the
  caller still propagates, and a later request can reuse the recovered session.
- Native session storage retains the SDK's project-specific key; unavailable storage
  falls back to memory. Web uses tab-scoped `sessionStorage` with a memory fallback,
  not persistent `localStorage`. Startup removes legacy browser tokens, but preserves
  the non-secret durable device UUID in `localStorage`.
- Ordinary and critical configuration use separate, in-memory, per-auth-user caches:
  success (including a missing value) lasts five minutes; failure retries after 30 seconds.
  `forceRefresh = true` bypasses the TTL. Cancellation propagates. An observed 401/403
  clears the old value instead of returning stale credentials; other failures can use
  the last value. A cached value is not a guarantee of immediate server-side revocation.
- Configuration access does not enable optional telemetry. Consent continues to control
  device reporting and analytics independently of authentication. Opt-in activates the
  local collectors without waiting for network authentication; one background auth
  attempt is shared while pending. Opt-out cancels that attempt without waiting for it.
  Device reporting still requires both consent and an authenticated identity, so SDK
  session recovery can trigger reporting without another consent toggle. Protected
  uploads always authenticate and verify device ownership before sending state.

## Configuration boundaries

| Access | Contract |
| --- | --- |
| Ordinary config | Authenticated direct reads of only `github_proxy`, `music_api_baseurl`, `online_track_interval` |
| Critical config | No direct client table access; authenticated `get-critical-config` calls only |
| Device/state writes | Persistent client `device_id`, server-derived `auth_user_id`, immutable binding and owner-scoped RLS |

The Edge Function accepts `POST {"keys":["music_api_auth","api_proxy_token"]}` and
returns `{"values":{},"missing":["music_api_auth","api_proxy_token"]}` for an empty table.
Keys are deduplicated, limited to eight per request, and must match `[a-z0-9_]{1,64}`.
Only the two explicitly allowlisted keys can be returned. A valid anonymous Auth user
is accepted; the static public/anon API key alone is **not** a logged-in user.

The previous `{"config_key":"music_api_auth"}` request remains supported, returning
`{"config_value":"..."}` or 404 when missing. Responses are `no-store`; CORS is restricted
to the existing production origin and localhost. JWT verification remains enabled,
and the function also checks the current user with Auth before reading the table.

Any value returned by this endpoint is visible to its authenticated recipient,
including anonymous users. This is **not** storage for server-only credentials.
Service-role keys and upstream credentials that must remain secret belong behind a
server-side proxy, never in this response allowlist. Web album requests deliberately
do not fetch or send `music_api_auth`; protected Web upstream APIs need a server proxy.
Native album requests use the critical endpoint without falling back to public config.

## Earlier configuration alignment (2026-09-06, Asia/Shanghai)

`get-critical-config` version 3 was deployed to Showcase with JWT verification enabled.
Its CORS preflight permits the pinned Kotlin SDK's `x-region` and
`x-supabase-client-platform` headers in addition to authorization/API-key/content headers.
No database schema/policy changes or user-data migration were needed for this alignment.
The critical table was empty at verification time; no secret values were copied or inserted.
The app itself has not been published by this change.

Verified live with a temporary anonymous account: batch missing=200, legacy missing=404,
forbidden key=403, malformed request=400, no user JWT=401, static anon key=401,
critical-table direct read=403, authenticated ordinary config=200, unauthenticated config=401,
and production-origin CORS. The test session was signed out and the account deleted.
Existing test users were not changed.

On app upgrade, legacy Web `localStorage` sessions are intentionally discarded; a new
anonymous identity may be created, so records belonging to the old identity are not
automatically adopted. Current tab-scoped sessions and native SDK storage keys are
preserved. Backend protocol compatibility does not bypass device ownership checks.

Local results: 54 selected desktop tests, 32 JS browser tests, 32 Wasm browser tests,
and 12 Edge Function handler tests passed, with zero failures or skipped tests.
JS and Wasm test compilations and Android debug compilation also passed.
Android/iOS device flows were not run.

Reproducible local checks:

```sh
node --test supabase/functions/get-critical-config/handler.test.mjs
./gradlew :composeApp:desktopTest --tests '*Supabase*' --tests '*AnonymousUsage*' --tests '*UserFeedback*' --tests '*Album*'
./gradlew :composeApp:compileTestKotlinJs :composeApp:compileTestKotlinWasmJs
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :composeApp:jsBrowserTest --tests '*Supabase*' --tests '*AlbumRequestBackendWebTest*'
./gradlew :composeApp:wasmJsBrowserTest --tests '*Supabase*' --tests '*AlbumRequestBackendWebTest*'
```

Browser tests require Chrome (set `CHROME_BIN` if it is not discovered automatically).
The endpoint tests exercise the actual request handler with the external Auth/database
dependencies substituted; the live checks separately verify those integration boundaries.

## Durable device restoration (2026-09-06, Asia/Shanghai)

The ownership model follows `../showcase/app/src/main/java/com/alpha/showcase/track/Analytics.kt`
and the reference `register-device` function. `device_id` is a random, persistent UUIDv4;
`auth_user_id` is the verified Supabase UID (the reference calls it `owner_id`).
They are separate identifiers, not interchangeable authentication credentials.

- Restore existing storage: Android SharedPreferences, iOS Keychain, desktop
  `.showcase/device_id`, and JS/Wasm `showcase_durable_device_id` localStorage.
  The shared provider creates and saves a missing/invalid UUID. Unavailable storage
  falls back to one in-memory ID for the current process, without broadening access.
- Before a state upload, authenticate and call `bind_device`; only after success save
  `(deviceId, ownerId)` as `owned_device_binding_v2`. Concurrent in-process uploads share
  one registration, and a restarted process revalidates ownership with the server.
- Reuse the UUID for the same UID. A changed UID, or a server ownership conflict when
  no reliable local binding remains, generates a new UUID. Conflict retry is limited to
  one attempt. Network/auth failures do not authorize uploads or claim an old device.
- Web keeps its tab-scoped authentication policy: a new anonymous UID can cause device
  rotation even though a previous UUID remains in localStorage. Knowing or editing a
  local UUID does not restore a lost Auth account or grant access to its records.
- Configuration-only requests still do not register devices or enable optional telemetry.

Migration `20260905234749_restore_durable_device_ownership.sql` was applied only to
Showcase. It replaces `device_id = auth_user_id` checks with immutable device ownership
and indexed composite foreign keys on all four device-state tables. Owner IDs are
derived by the server; clients cannot supply them. The new registration overload takes
an explicit device ID; the original eleven-argument RPC remains for old clients.
Old clients must still register before writing state with their default UID-shaped ID.
Existing rows were retained, not reassigned or deleted. No app release was deployed.

Clients can read only their own device binding columns, not device metadata or other
users' bindings. Feedback/analytics allow only inserts associated with the caller's
registered device and retain existing quotas and field restrictions. Other previously
closed tables remain closed. Ordinary/critical configuration permissions were unchanged.

Verification: 57 selected desktop tests and 55 tests on each JS/Wasm browser target
passed with zero failures/skips; Android debug and iOS Simulator compilation passed.
The actual iOS String/NSData encoding helpers also passed a standalone Kotlin/Native
round-trip on macOS; this did not access Keychain records. Android/iOS on-device
persistence flows were not run. Independent review
found and corrected Android's missing first-run persistence. The SQL regression in
`tests/durable_device_ownership.sql` exercises authenticated/anonymous roles, old/new
registration signatures, own/foreign access, owner spoofing, immutable ownership and
foreign keys; its fixtures are rolled back, including on failure. A proposed real HTTP
test was blocked by automatic safety review because it would create persistent temporary
accounts/data; it did not execute. HTTP routing and cross-session contention were not
verified by that proposed test. The database upsert enforces claims atomically.

Advisor notices are not an all-clear security certificate. Signed-in
[`SECURITY DEFINER` RPC warnings](https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable)
are expected for these deliberately exposed, authenticated, fixed-search-path ownership
operations; anonymous Auth access is intentional, not unauthenticated `anon` access.
Closed tables intentionally have RLS without policies. Existing instance follow-ups remain:
[Postgres security-patch upgrade](https://supabase.com/docs/guides/platform/upgrading) and
[leaked-password protection](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection)
if password authentication is used. Neither instance setting was changed for this restoration.
