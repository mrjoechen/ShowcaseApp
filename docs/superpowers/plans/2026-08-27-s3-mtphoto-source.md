# S3 Presentation and MTPhoto Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give S3 a neutral compatible-storage presentation and add a complete, cross-platform MTPhoto source with authentication, album selection, persistence, repository loading, and authenticated media fetching.

**Architecture:** Preserve `StorageType.typeName` as the compatibility identifier and add a separate `displayName` for UI. Implement MTPhoto as a serializable `RemoteApi`, a Ktor API client, an injected/testable repository and authentication manager, and a Coil model/fetcher whose stable cache key excludes expiring credentials. Reuse the existing Compose configuration, `DataWithType`, source persistence, and platform allowlist patterns.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform resources/UI, kotlinx.serialization, Ktor 3, Coil 3, okio, kotlinx.coroutines, kotlin.test, Gradle.

**Spec:** `docs/superpowers/specs/2026-08-27-s3-mtphoto-source-design.md`

## Global Constraints

- Preserve `S3.typeName == "Amazon S3"`; use `S3.displayName == "S3 Compatible"` only for UI.
- Use MTPhoto source type ID `112` and serial name `MTPhoto`.
- Support Android, iOS, desktop, JS, and Wasm through common Ktor/Compose code.
- Encrypt MTPhoto API keys and passwords through `RConfig`; never place plaintext secrets in cache keys or logs.
- Preserve the repository's pre-existing uncommitted changes and do not stage or commit them.
- Do not add a new video-player subsystem; preserve MTPhoto video MIME metadata for the existing playback pipeline.

---

### Task 1: S3 compatibility-safe display name and icon

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/networkfile/storage/StorageType.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/networkfile/storage/remote/ExternalSource.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/ui/dialog/SourceTypeDialog.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/ui/config/ConfigScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/utils/RemoteStorageExt.kt`
- Create: `composeApp/src/commonMain/composeResources/drawable/ic_s3_bucket.xml`
- Delete: `composeApp/src/commonMain/composeResources/drawable/ic_amazon_s3.xml`
- Modify: `composeApp/src/commonTest/kotlin/com/alpha/showcase/common/networkfile/storage/S3RssSourceModelTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/alpha/showcase/common/ui/dialog/SourceTypeSectionsTest.kt`

**Interfaces:**
- Produces: `StorageType(typeName: String, type: Int, displayName: String = typeName)`.
- Produces: `S3.typeName == "Amazon S3"`, `S3.displayName == "S3 Compatible"`.
- Produces: `Res.drawable.ic_s3_bucket` for picker and configured-source rendering.

- [ ] **Step 1: Write failing presentation assertions**

```kotlin
assertEquals("Amazon S3", S3.typeName)
assertEquals("S3 Compatible", S3.displayName)
assertEquals(Res.drawable.ic_s3_bucket, s3Source.getIcon())
```

Update the section fixture so S3 expects `ic_s3_bucket`.

- [ ] **Step 2: Run focused tests and confirm the missing property/resource failures**

Run:

```bash
./gradlew :composeApp:desktopTest \
  --tests 'com.alpha.showcase.common.networkfile.storage.S3RssSourceModelTest' \
  --tests 'com.alpha.showcase.common.ui.dialog.SourceTypeSectionsTest' \
  --stacktrace
```

Expected: compilation fails because `displayName` and `ic_s3_bucket` do not exist.

- [ ] **Step 3: Implement the display boundary and resource replacement**

```kotlin
open class StorageType(
    val typeName: String = "UNKNOWN",
    val type: Int = TYPE_UNKNOWN,
    val displayName: String = typeName,
)

data object S3 : ExternalSource(
    typeName = "Amazon S3",
    type = TYPE_S3,
    displayName = "S3 Compatible",
)
```

Use `displayName` in picker text/content descriptions and config titles. Keep all protocol/cache code on `typeName`.

- [ ] **Step 4: Run the focused tests until green**

Run the command from Step 2. Expected: PASS.

### Task 2: MTPhoto source model, type routing, serialization, and secret normalization

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/networkfile/storage/remote/MTPhotoSource.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/networkfile/storage/remote/ExternalSource.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/networkfile/storage/StorageType.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/networkfile/util/StorageSourceSerializer.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/repo/SourceListRepo.kt`
- Create: `composeApp/src/commonTest/kotlin/com/alpha/showcase/common/networkfile/storage/MTPhotoSourceModelTest.kt`

**Interfaces:**
- Produces: `TYPE_MTPHOTO = 112`, `MTPHOTO`, `MTPHOTO_AUTH_TYPE_API_KEY`, `MTPHOTO_AUTH_TYPE_PASSWORD`.
- Produces: `@SerialName("MTPhoto") data class MTPhotoSource(...) : RemoteApi`.
- Produces: `getType(TYPE_MTPHOTO) == MTPHOTO` and `MTPhotoSource.getType() == TYPE_MTPHOTO`.

- [ ] **Step 1: Add failing model tests**

Cover integer/type mapping, polymorphic JSON round-trip, API-key/password fields, and idempotent normalization:

```kotlin
val decoded = StorageSourceSerializer.sourceJson.decodeFromString(
    StorageSources.serializer(),
    StorageSourceSerializer.sourceJson.encodeToString(StorageSources.serializer(), original),
)
assertIs<MTPhotoSource>(decoded.sources.single())
assertEquals(TYPE_MTPHOTO, source.getType())
```

Use test encryption functions that add/remove `test-encrypted:` and assert both `apiKey` and `pass` are encrypted exactly once.

- [ ] **Step 2: Run the model test and confirm it fails**

```bash
./gradlew :composeApp:desktopTest \
  --tests 'com.alpha.showcase.common.networkfile.storage.MTPhotoSourceModelTest' \
  --stacktrace
```

Expected: compilation fails because MTPhoto model symbols do not exist.

- [ ] **Step 3: Implement model and persistence mappings**

```kotlin
@Serializable
@SerialName("MTPhoto")
data class MTPhotoSource(
    override val name: String,
    val url: String,
    val authType: String = MTPHOTO_AUTH_TYPE_API_KEY,
    val apiKey: String? = null,
    val user: String? = null,
    val pass: String? = null,
    val albumId: Int? = null,
    val albumName: String? = null,
) : RemoteApi
```

Register the serializer and normalize only the secret used by the selected auth mode while preserving unrelated fields.

- [ ] **Step 4: Run the model test until green**

Run the command from Step 2. Expected: PASS.

### Task 3: Ktor MTPhoto protocol client and DTOs

**Files:**
- Create: `showcase-api/src/commonMain/kotlin/com/alpha/showcase/api/mtphoto/MTPhotoData.kt`
- Create: `showcase-api/src/commonMain/kotlin/com/alpha/showcase/api/mtphoto/MTPhotoApi.kt`
- Create: `showcase-api/src/commonTest/kotlin/com.alpha.showcase.api/MTPhotoDataTest.kt`

**Interfaces:**
- Produces: `MTPhotoAlbum`, `MTPhotoFileItem`, `MTPhotoLoginRequest`, `MTPhotoLoginResponse`, `MTPhotoAuthCodeResponse`.
- Produces: `MTPhotoApi.getAlbums`, `getAlbumFiles`, `login`, `getAuthCode`, and `downloadFile`.
- Produces: `buildMTPhotoGatewayUrl(baseUrl, fileId, md5, albumId, authCode): String`.

- [ ] **Step 1: Write failing DTO and URL tests**

```kotlin
val file = json.decodeFromString<MTPhotoFileItem>(
    """{"id":7,"MD5":"abc","status":1,"tokenAt":"2026-01-01T00:00:00Z","fileType":"image/jpeg","duration":null,"fileSize":"42","width":10,"height":20}"""
)
assertEquals("abc", file.md5)
assertTrue(buildMTPhotoGatewayUrl("https://photos.test/", 7, "abc", 3, "a b").contains("auth_code=a%20b"))
```

- [ ] **Step 2: Run the API test and confirm it fails**

```bash
./gradlew :showcase-api:jvmTest \
  --tests 'com.alpha.showcase.api.MTPhotoDataTest' \
  --stacktrace
```

Expected: compilation fails because the MTPhoto API package does not exist.

- [ ] **Step 3: Implement DTOs and Ktor calls**

Normalize base URLs with `trimEnd('/')`, set either `x-api-key` or `Authorization`, use `setBody` for login/auth-code requests, and decode gateway responses as `ByteArray`.

- [ ] **Step 4: Run the API test until green**

Run the command from Step 2. Expected: PASS.

### Task 4: Authentication lifecycle, stable media model, and Coil fetching

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/repo/mtphoto/MTPhotoSourceValidation.kt`
- Create: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/repo/mtphoto/MTPhotoAuthManager.kt`
- Create: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/repo/mtphoto/MTPhotoFile.kt`
- Create: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/MTPhotoFetcher.kt`
- Modify: `composeApp/src/commonMain/kotlin/App.kt`
- Create: `composeApp/src/commonTest/kotlin/com/alpha/showcase/common/repo/mtphoto/MTPhotoAuthManagerTest.kt`
- Create: `composeApp/src/commonTest/kotlin/com/alpha/showcase/common/repo/mtphoto/MTPhotoFileTest.kt`

**Interfaces:**
- Produces: `MTPhotoAuthSession(authCode, headerName, headerValue)`.
- Produces: `MTPhotoAuthManager.register`, `getAuthForKey`, `invalidate`, `baseUrlForKey`.
- Produces: `MTPhotoFile.buildUrl(authCode)` and stable `cacheKey`.
- Produces: `MTPhotoFetcher.Factory` and `MTPhotoFileKeyer` registered for every platform.

- [ ] **Step 1: Write failing auth and media tests**

Use an injected suspending auth loader and mutable test clock. Assert:

```kotlin
assertEquals(firstSession, manager.getAuthForKey(key))
assertEquals(firstSession, manager.getAuthForKey(key))
assertEquals(1, refreshCount)
manager.invalidate(key)
manager.getAuthForKey(key)
assertEquals(2, refreshCount)
assertFalse(key.contains("plain-secret"))
assertEquals("mtphoto://$key/3/7/abc", file.cacheKey)
```

- [ ] **Step 2: Run focused tests and confirm failures**

```bash
./gradlew :composeApp:desktopTest \
  --tests 'com.alpha.showcase.common.repo.mtphoto.MTPhotoAuthManagerTest' \
  --tests 'com.alpha.showcase.common.repo.mtphoto.MTPhotoFileTest' \
  --stacktrace
```

- [ ] **Step 3: Implement authentication and fetch-time refresh**

Use an okio SHA-256 fingerprint truncated to 16 hex characters. Cache sessions for `23 * 60 * 60 * 1000L`, refresh under `Mutex`, and preserve cancellation. The fetcher calls `downloadFile`; on `ClientRequestException` with status 401 it invalidates and retries once.

- [ ] **Step 4: Register the common Coil components and run tests**

```kotlin
.components {
    add(SvgDecoder.Factory())
    add(MTPhotoFileKeyer())
    add(MTPhotoFetcher.Factory())
    addPlatformComponents()
}
```

Run the command from Step 2. Expected: PASS.

### Task 5: MTPhoto repository and manager routing

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/repo/MTPhotoSourceRepo.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/repo/RepoManager.kt`
- Create: `composeApp/src/commonTest/kotlin/com/alpha/showcase/common/repo/MTPhotoSourceRepoTest.kt`

**Interfaces:**
- Produces: `MTPhotoSourceRepo.getAlbums(source): Result<List<MTPhotoAlbum>>`.
- Produces: `MTPhotoSourceRepo.getItems(...) : Result<List<MTPhotoFile>>`.
- Consumes: `MTPhotoAuthManager`, `MTPhotoApi`, `MTPhotoFileItem`.

- [ ] **Step 1: Write failing repository tests**

Inject album and file loaders. Assert source validation, empty-album failure, metadata mapping, filtering, and `RepoManager` routing:

```kotlin
val item = repo.getItems(source, recursive = false, filter = null).getOrThrow().single()
assertEquals(7, item.fileId)
assertEquals("image/jpeg", item.mimeType)
assertEquals(TYPE_MTPHOTO, source.getType())
```

- [ ] **Step 2: Run the repository test and confirm it fails**

```bash
./gradlew :composeApp:desktopTest \
  --tests 'com.alpha.showcase.common.repo.MTPhotoSourceRepoTest' \
  --stacktrace
```

- [ ] **Step 3: Implement repository mapping and routing**

Validate credentials before network access, register source credentials before creating files, map all reference metadata, apply `filter?.invoke(item) ?: true`, and return `Result.failure(Exception("Empty album!"))` for an empty file list.

- [ ] **Step 4: Run the repository test until green**

Run the command from Step 2. Expected: PASS.

### Task 6: Picker, icon, navigation, and MTPhoto configuration UI

**Files:**
- Create: `composeApp/src/commonMain/composeResources/drawable/ic_mtphoto.xml`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-zh-rCN/strings.xml`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/networkfile/storage/StorageType.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/utils/RemoteStorageExt.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/ui/dialog/SourceTypeDialog.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/ui/source/SourceListView.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/ui/config/ConfigScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/ui/config/MTPhotoConfigDraft.kt`
- Create: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/ui/config/MTPhotoConfigPage.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/alpha/showcase/common/ui/dialog/SourceTypeSectionsTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/alpha/showcase/common/ui/source/SourceSelectionRoutingTest.kt`
- Create: `composeApp/src/commonTest/kotlin/com/alpha/showcase/common/ui/config/MTPhotoConfigDraftTest.kt`

**Interfaces:**
- Produces: `MTPhotoConfigDraft.toSource(transformSecret)` with locked-secret preservation.
- Produces: `MTPhotoConfigPage(mtPhotoSource, onTestClick, onSaveClick)`.
- Consumes: `MTPhotoSourceRepo.getAlbums` for connection testing and album selection.

- [ ] **Step 1: Extend picker/routing tests and add draft tests**

Expect album services to be `[IMMICH, MTPHOTO]`, MTPhoto to appear once with `ic_mtphoto`, and `configTypeForSourceSelection(MTPHOTO) == TYPE_MTPHOTO`.

Draft tests cover API-key mode, password mode, invalid URL, missing album, and preservation of existing encrypted values when unchanged.

- [ ] **Step 2: Run focused tests and confirm failures**

```bash
./gradlew :composeApp:desktopTest \
  --tests 'com.alpha.showcase.common.ui.dialog.SourceTypeSectionsTest' \
  --tests 'com.alpha.showcase.common.ui.source.SourceSelectionRoutingTest' \
  --tests 'com.alpha.showcase.common.ui.config.MTPhotoConfigDraftTest' \
  --stacktrace
```

- [ ] **Step 3: Implement UI resources and routing**

Add localized labels for URL, credentials, album loading, album requirement, and connection success. Add MTPhoto to `SUPPORT_LIST`, all HTTP platform filters, the album-services category, saved-source icons, source selection routing, and `ConfigScreen`.

- [ ] **Step 4: Implement the configuration page**

Follow the existing S3 locked-secret behavior. Connection testing builds a source without requiring an album, calls `getAlbums`, and selects the only album automatically. Saving requires an album ID and delegates to the existing save pipeline.

- [ ] **Step 5: Run focused UI/model tests until green**

Run the command from Step 2. Expected: PASS.

### Task 7: End-to-end integration and regression verification

**Files:**
- Modify as required by compiler feedback, limited to files listed above.
- Review: all changed files and pre-existing user diffs.

**Interfaces:**
- Consumes every interface from Tasks 1-6.
- Produces a buildable, test-covered feature on the supported KMP targets.

- [ ] **Step 1: Run all focused tests together**

```bash
./gradlew :showcase-api:jvmTest \
  --tests 'com.alpha.showcase.api.MTPhotoDataTest' \
  :composeApp:desktopTest \
  --tests 'com.alpha.showcase.common.networkfile.storage.S3RssSourceModelTest' \
  --tests 'com.alpha.showcase.common.networkfile.storage.MTPhotoSourceModelTest' \
  --tests 'com.alpha.showcase.common.repo.mtphoto.MTPhotoAuthManagerTest' \
  --tests 'com.alpha.showcase.common.repo.mtphoto.MTPhotoFileTest' \
  --tests 'com.alpha.showcase.common.repo.MTPhotoSourceRepoTest' \
  --tests 'com.alpha.showcase.common.ui.dialog.SourceTypeSectionsTest' \
  --tests 'com.alpha.showcase.common.ui.source.SourceSelectionRoutingTest' \
  --tests 'com.alpha.showcase.common.ui.config.MTPhotoConfigDraftTest' \
  --stacktrace
```

- [ ] **Step 2: Run full desktop tests and multiplatform compilations**

```bash
./gradlew :composeApp:desktopTest :composeApp:compileKotlinDesktop \
  :composeApp:compileCommonMainKotlinMetadata \
  :composeApp:compileKotlinIosSimulatorArm64 \
  :showcase-api:jvmTest \
  --stacktrace
```

- [ ] **Step 3: Inspect the final diff and workspace status**

Confirm no unrelated user edits were reverted, no generated resource sources or secrets were added, and all MTPhoto mappings appear exactly once.

- [ ] **Step 4: Record completion evidence**

Report the exact tests/compilations run, their results, any pre-existing warnings, and any intentionally unsupported behavior from the design.
