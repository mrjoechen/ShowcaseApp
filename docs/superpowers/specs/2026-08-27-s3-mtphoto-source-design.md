# S3 Presentation and MTPhoto Source Design

## Scope

Implement the Amazon S3 presentation change from reference commit
`ccee64b104208fa6e97c5587f0601054d6f3d949` and port the complete MTPhoto
source workflow into the Kotlin Multiplatform application.

The implementation must preserve unrelated uncommitted work already present in
the repository. It must support the platforms on which the existing HTTP-backed
sources are available: Android, iOS, desktop, JS, and Wasm.

## S3 compatibility boundary

`S3.typeName` remains `"Amazon S3"`. Existing cache keys, stored configuration,
and any protocol-facing logic continue to use `typeName`.

`StorageType` gains `displayName`, defaulting to `typeName`. S3 overrides only
the display value with `"S3 Compatible"`. Picker labels, accessibility text,
and configuration titles use `displayName`.

Replace the Amazon-branded icon with the neutral bucket vector from the
reference commit. Both the source picker and saved-source rows use the new
resource. The Chinese category label already says `其他内容`, so the existing
local edit is preserved rather than re-applied.

## MTPhoto model and persistence

Add the stable source type ID `112`, matching the reference application. The
serializable `MTPhotoSource` keeps the reference wire shape and serial name:

- `name`, `url`, `authType`
- `apiKey` for API-key authentication
- `user` and `pass` for password authentication
- `albumId` and `albumName` for the selected album

Register it in all integer/type/model mappings and in the polymorphic source
serializer. Add it to the album-services picker section and every platform's
HTTP-source allowlist.

API keys and passwords are sensitive. New and edited values are encrypted by
the same source-normalization path used by existing S3 and Immich credentials.
Legacy plaintext values are migrated idempotently when sources are loaded.
Editing a source without changing a secret preserves the existing ciphertext.

## API and authentication

Add a common Ktor MTPhoto API client under `showcase-api`. It implements the
reference endpoints:

- `GET api-album`
- `GET api-album/filesFlat/{albumId}`
- `POST auth/login`
- `POST auth/auth_code`
- `GET gateway/file/{fileId}/{md5}`

The API-key flow uses `x-api-key`. The password flow logs in and uses a Bearer
access token. Both obtain an `auth_code` for gateway media requests.

A common authentication manager is keyed by normalized server URL, auth type,
credential identity, source name, and album ID. It caches the access token and
`auth_code` for 23 hours, refreshes under a mutex, never puts secret material in
the key, and can invalidate a single source after an authorization failure.

## Repository and media loading

`MTPhotoSourceRepo` validates the source, authenticates, loads the flattened
album file list, and maps every response to an `MTPhotoFile`. The file model
contains stable media identity and metadata, builds gateway URLs only when an
`auth_code` is available, and exposes a cache key independent of expiring auth.

Register an MTPhoto Coil fetcher and keyer in the shared image-loader component
registry. The fetcher resolves current credentials at fetch time, supplies the
required header, retries once after a 401 by invalidating the cached auth, and
returns the downloaded bytes to Coil. Coil remains responsible for memory and
disk caching through the stable key.

The repository is routed through `RepoManager`. MTPhoto media is wrapped in the
existing `DataWithType` structure so existing MIME filtering, sorting, and image
rendering work without changing unrelated sources. The current KMP player does
not implement video rendering for any source; this feature preserves MTPhoto
video metadata and detection but does not introduce a new cross-platform video
player.

## Configuration UI

Add a Compose MTPhoto configuration page following the existing Immich and S3
patterns. It provides:

- source name and base URL
- API-key or username/password authentication
- connection test and album loading
- album selection with item counts
- add/edit support with locked existing secrets
- validation before connection testing or saving

Selecting MTPhoto opens this page through the existing configuration dialog
route. Saved sources display the MTPhoto icon.

## Error handling

Missing album, API key, username, password, invalid URL, unsupported auth type,
empty albums, HTTP failures, and malformed authentication responses produce
failed `Result` values or UI validation feedback. Cancellation is never wrapped
as an ordinary failure. Authentication state is invalidated after authorization
failures so the next media request can recover.

## Verification

Use test-driven coverage for:

- S3 legacy name, new display name, and icon consistency
- MTPhoto type mapping, platform visibility, category order, routing, icon, and
  polymorphic serialization
- secret preservation and plaintext-to-ciphertext normalization
- draft validation for both authentication modes
- API DTO decoding and URL construction
- auth cache separation, refresh, invalidation, and secret-free keys
- repository mapping/filtering and `RepoManager` routing
- media URL/cache-key behavior and Coil keying

Run the focused desktop tests first, then the full desktop test suite and the
desktop/common/iOS compilation targets that are available in this workspace.
