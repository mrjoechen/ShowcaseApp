# TMDB Web Token and Provider Help Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Require a user-owned TMDB API Read Access Token on Web, remove TMDB secrets from the JS build, and add consistent token-acquisition help to the TMDB, Unsplash, and Pexels configuration pages.

**Architecture:** Extend the existing Unsplash/Pexels platform credential seam to TMDB. Store TMDB credentials on `TMDBSource`, normalize them with the existing configuration encryption, and create an API client from the source credential on Web while retaining the built-in non-Web fallback. Render all provider guidance through one small Compose component backed by provider-specific official URLs.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlinx.serialization, Ktor, Gradle, GitHub Actions.

**Spec:** User-approved design in the 2026-08-31 task conversation; no separate design document.

## Global Constraints

- Do not commit any changes.
- Web must never require, embed, or fall back to a repository-owned TMDB, Unsplash, or Pexels credential.
- Non-Web platforms must retain their current built-in credential fallback.
- `TMDBSource.apiToken` must be the final constructor field, nullable, and default to `null` for old JSON and source compatibility.
- TMDB uses the API Read Access Token as `Authorization: Bearer <token>`; do not label it as the v3 API key.
- New plaintext credential state must use `remember`, not `rememberSaveable`; unchanged stored ciphertext must remain unchanged.
- Token guidance must align with the existing 280dp form controls and use official provider pages.
- Preserve all unrelated uncommitted user changes.

---

### Task 1: TMDB API Platform Credential Boundary

**Files:**
- Modify: `showcase-api/src/commonTest/kotlin/com.alpha.showcase.api/TmdbApiTest.kt`
- Create: `showcase-api/src/nonWebTest/kotlin/com.alpha.showcase.api/TmdbBuiltInApiTest.kt`
- Modify: `showcase-api/src/commonMain/kotlin/com/alpha/showcase/api/ExternalImageApiKey.kt`
- Modify: `showcase-api/src/webMain/kotlin/com/alpha/showcase/api/ExternalImageApiKey.web.kt`
- Modify: `showcase-api/src/nonWebMain/kotlin/com/alpha/showcase/api/ExternalImageApiKey.nonWeb.kt`
- Modify: `showcase-api/src/commonMain/kotlin/com/alpha/showcase/api/tmdb/TmdbApi.kt`
- Modify: `showcase-api/build.gradle.kts`
- Modify: `.github/workflows/deploy-js-pages.yml`

**Interfaces:**
- Produces: `internal expect fun builtInTmdbApiToken(): String` and `internal fun HeadersBuilder.applyTmdbApiToken(apiToken: String)`.
- Produces: `TmdbApi(apiToken: String = builtInTmdbApiToken())`, which remains source-compatible on non-Web.
- Consumes: `TMDB_API_KEY` only from `nonWebMain` BuildConfig.

- [ ] **Step 1: Write the failing deterministic authorization test**

Add a test that calls `HeadersBuilder().applyTmdbApiToken("configured-token")` and expects `Authorization` to equal the literal `Bearer configured-token`. Keep the no-argument constructor compatibility assertion in common tests, and move live no-argument network tests to `TmdbBuiltInApiTest` under `nonWebTest`.

- [ ] **Step 2: Run the common test compile and verify RED**

Run `./gradlew :showcase-api:compileCommonTestKotlinMetadata --stacktrace`. Expected: failure because `applyTmdbApiToken` does not exist.

- [ ] **Step 3: Implement the platform boundary**

Add `builtInTmdbApiToken` expect/actual functions beside the existing image provider keys. Make the Web actual fail with a clear source-configuration requirement and the non-Web actual return `TMDB_API_KEY`. Change `TmdbApi` to use that seam and apply its header through `applyTmdbApiToken`.

- [ ] **Step 4: Move the build secret to non-Web only**

Generate `TMDB_API_KEY` in the existing `sourceSets.named("nonWebMain")` BuildConfig block using the lazy required-key provider. Remove TMDB from the GitHub Actions required-secret list, environment blocks, and `local.properties` output.

- [ ] **Step 5: Verify GREEN**

Run `./gradlew :showcase-api:compileCommonTestKotlinMetadata :showcase-api:compileKotlinJs --stacktrace`. Expected: both tasks pass without evaluating `TMDB_API_KEY` for JS.

---

### Task 2: TMDB Source Credential Lifecycle

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/com/alpha/showcase/common/repo/ExternalSourceConfigTest.kt`
- Modify: `composeApp/src/webTest/kotlin/com/alpha/showcase/common/repo/SourceListRepoWebTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/networkfile/storage/remote/TMDBSource.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/repo/TmdbConfig.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/repo/ExternalImageApiFactory.kt`
- Modify: `composeApp/src/webMain/kotlin/com/alpha/showcase/common/repo/ExternalImageApiFactory.web.kt`
- Modify: `composeApp/src/nonWebMain/kotlin/com/alpha/showcase/common/repo/ExternalImageApiFactory.nonWeb.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/repo/TmdbSourceRepo.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/repo/SourceListRepo.kt`

**Interfaces:**
- Consumes: `TmdbApi(apiToken)` and the `ExternalImageApiKeyEdit` lifecycle.
- Produces: `TMDBSource.apiToken: String?`, `TmdbConfigDraft.toSource`, `TMDBSource.resolveApiToken`, `shouldRequestTmdbApiToken`, and `createTmdbApi` expect/actual.

- [ ] **Step 1: Write failing source and lifecycle tests**

Add literal assertions covering: legacy TMDB JSON decodes with `apiToken == null`; Web requests a TMDB token while non-Web does not; a new draft encrypts its token; an edit preserves unchanged ciphertext without re-encrypting; and `resolveApiToken` decrypts configured ciphertext.

- [ ] **Step 2: Write the failing Web persistence test**

Save a `TMDBSource` containing `plain-api-token` through `SourceListRepo`, then assert its stored field starts with `scenc:v2:` and decrypts back to `plain-api-token`.

- [ ] **Step 3: Run focused tests and verify RED**

Run `./gradlew :composeApp:desktopTest --tests '*ExternalSourceConfigTest*' :composeApp:jsTest --tests '*SourceListRepoWebTest*' --stacktrace`. Expected: compile/test failure because the TMDB credential interfaces do not exist.

- [ ] **Step 4: Implement the source model and draft**

Append `apiToken: String? = null` to `TMDBSource`. Add a draft that builds all existing fields and delegates token preservation/encryption to `ExternalImageApiKeyEdit`. When the field is hidden on non-Web, preserve an existing stored value so editing a synchronized Web source does not silently clear its credential.

- [ ] **Step 5: Implement platform client selection and repository resolution**

Add `createTmdbApi(apiToken)` to the existing expect/actual factory. Web trims and requires the supplied value; non-Web uses the supplied value when present and otherwise calls the no-argument constructor. Resolve/decrypt the source token inside `TmdbSourceRepo` before selecting the client, without evaluating a no-argument Web client first.

- [ ] **Step 6: Normalize TMDB token storage**

Add a `TMDBSource` branch in `SourceListRepo.normalizeSensitiveFields` that encrypts plaintext, preserves current ciphertext, and copies every existing field unchanged.

- [ ] **Step 7: Verify GREEN**

Re-run the focused common/desktop/Web tests. Expected: all new and existing tests pass.

---

### Task 3: Consistent Token Help and TMDB Configuration UI

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/ui/config/ApiTokenHelp.kt`
- Create: `composeApp/src/commonTest/kotlin/com/alpha/showcase/common/ui/config/ApiTokenHelpTest.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-zh-rCN/strings.xml`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/ui/config/TMDBConfigPage.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/ui/config/UnsplashConfigPage.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/ui/config/PexelsConfigPage.kt`

**Interfaces:**
- Consumes: `TmdbConfigDraft`, `ExternalImageApiKeyEdit`, `PasswordInput`, and `TextWithHyperlink`.
- Produces: `ApiTokenProvider` with official URL mapping and `ApiTokenHelp` with a fixed 280dp content width.

- [ ] **Step 1: Write the failing provider-link test**

Assert literal official URLs for TMDB (`https://www.themoviedb.org/settings/api`), Unsplash (`https://unsplash.com/oauth/applications`), and Pexels (`https://www.pexels.com/api/key/`). This catches a provider wired to the wrong acquisition destination.

- [ ] **Step 2: Run the focused common test and verify RED**

Run `./gradlew :composeApp:desktopTest --tests '*ApiTokenHelpTest*' --stacktrace`. Expected: failure because `ApiTokenProvider` does not exist.

- [ ] **Step 3: Add shared help UI and localized copy**

Implement the provider URL mapping and a shared composable that delegates to `TextWithHyperlink` using `Modifier.width(280.dp)`. Add English and Simplified Chinese copy that names TMDB `API Read Access Token`, Unsplash `Access Key`, and Pexels `API Key`, each with a localized `Get token`/`获取 Token` link label.

- [ ] **Step 4: Add TMDB token editing**

On Web, show a `PasswordInput` after the source name using non-saveable plaintext state, unchanged-ciphertext locking, required validation, and `TmdbConfigDraft` for both test and save. Place `ApiTokenHelp` immediately below the input.

- [ ] **Step 5: Add help to Unsplash and Pexels**

Place the same help directly below each currently visible token input. This makes all three visible on Web and preserves the existing non-Web visibility rules.

- [ ] **Step 6: Verify GREEN and compile all platforms**

Run the focused test, Compose common metadata compilation, and desktop compilation. Expected: tests and compiles pass.

---

### Task 4: Integration Verification and Review

**Files:**
- Review all files changed by Tasks 1-3 plus the pre-existing uncommitted external-provider work.

**Interfaces:**
- Consumes: all completed tasks.
- Produces: a locally served production JS distribution with no provider token embedded.

- [ ] **Step 1: Run regression tests**

Run the relevant API, common, desktop, and Web test suites. Investigate every new failure rather than weakening tests.

- [ ] **Step 2: Build production JS without TMDB configuration**

Run `./gradlew :composeApp:jsBrowserDistribution --stacktrace`. Expected: successful output without requiring `TMDB_API_KEY` for the Web target.

- [ ] **Step 3: Scan the artifact for known local provider credentials**

Read the three values from local configuration without printing them, and fail the scan if any value occurs in the production JavaScript artifacts.

- [ ] **Step 4: Verify the UI in the browser**

Serve the new production distribution at `http://127.0.0.1:8080/`. Verify TMDB, Unsplash, and Pexels forms at wide and narrow widths: token controls align with other 280dp fields, help wraps without overflow, and each link opens the expected official page.

- [ ] **Step 5: Perform independent code review**

Review correctness, backward compatibility, credential handling, platform source-set boundaries, workflow behavior, layout consistency, and accidental scope expansion. Fix confirmed findings and re-run affected checks.

