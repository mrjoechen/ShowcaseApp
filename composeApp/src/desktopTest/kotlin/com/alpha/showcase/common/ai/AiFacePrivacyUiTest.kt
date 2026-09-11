package com.alpha.showcase.common.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.runDesktopComposeUiTest
import coil3.asImage
import com.alpha.ai.imagegeneration.AiModel
import com.alpha.ai.imagegeneration.provider.registerBuiltIns
import com.alpha.showcase.common.storage.ObjectStore
import com.alpha.showcase.common.toast.ToastManager
import com.alpha.showcase.common.ui.ai.AiProviderPage
import com.alpha.showcase.common.ui.ai.AiSummaryOverlay
import org.jetbrains.compose.resources.getString
import showcaseapp.composeapp.generated.resources.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class AiFacePrivacyUiTest {
    @Test fun toggleIsUnderstandingOnlyAndPersistsWithoutProfiles() = runDesktopComposeUiTest(width = 800, height = 900) {
        val store = MemoryStore()
        setContent { ProviderFixture(store) }
        waitForIdle()
        val toggle = getString(Res.string.ai_image_summary_face_privacy)
        val description = getString(Res.string.ai_image_summary_face_privacy_description)
        onNodeWithContentDescription(toggle).assertDoesNotExist()
        onNodeWithText(getString(Res.string.ai_capability_image_understanding)).performClick()
        onNodeWithContentDescription(toggle).assertIsOn().assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithContentDescription(toggle).assertIsOff().performClick()
        waitForIdle()
        onNodeWithContentDescription(toggle).assertIsOn()
        onNodeWithText(description).assertExists()
        assertTrue(store.value!!.facePrivacyEnabled)
        assertTrue(store.value!!.profiles.isEmpty())
        onNodeWithText(getString(Res.string.ai_capability_image_to_image)).performClick()
        onNodeWithContentDescription(toggle).assertDoesNotExist()
        onNodeWithText(description).assertDoesNotExist()
        onNodeWithText(getString(Res.string.ai_capability_image_understanding)).performClick()
        onNodeWithContentDescription(toggle).assertIsOn().performClick()
        waitForIdle()
        onNodeWithText(description).assertDoesNotExist()
        assertFalse(store.value!!.facePrivacyEnabled)
    }

    @Test fun failedToggleSaveKeepsTheStoredValueAndShowsTheExistingError() = runDesktopComposeUiTest(width = 800, height = 900) {
        val store = MemoryStore()
        setContent { ProviderFixture(store) }
        waitForIdle()
        onNodeWithText(getString(Res.string.ai_capability_image_understanding)).performClick()
        runOnIdle { store.failWrites = true }
        onNodeWithContentDescription(getString(Res.string.ai_image_summary_face_privacy)).performClick()
        waitForIdle()
        onNodeWithText(getString(Res.string.ai_profile_error_save_failed)).assertExists()
        onNodeWithContentDescription(getString(Res.string.ai_image_summary_face_privacy)).assertIsOn().assertIsEnabled()
        assertTrue(store.value!!.facePrivacyEnabled)
    }

    @Test fun pendingHidesPreviouslyVisibleCachedContentAndEveryStatus() = runDesktopComposeUiTest {
        var state by mutableStateOf(AiSummaryState(content = cached))
        setContent { OverlayFixture(state) }
        onNodeWithText(cached.narration).assertExists()
        runOnIdle {
            state = state.copy(generating = true, failed = true, facePrivacyPending = true,
                facePrivacyBlocked = true, facePrivacyUnavailable = true)
        }
        onNodeWithText(cached.narration).assertDoesNotExist()
        onNodeWithText(cached.tags.single()).assertDoesNotExist()
        onNodeWithContentDescription(getString(Res.string.ai_generated_badge)).assertDoesNotExist()
        onNodeWithContentDescription(getString(Res.string.ai_image_summary_generating)).assertDoesNotExist()
        onNodeWithContentDescription(getString(Res.string.ai_image_summary_face_privacy_blocked)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.ai_image_summary_face_detection_failed)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.ai_image_summary_failed)).assertDoesNotExist()
    }

    @Test fun blockedClickExplainsPrivacyWithoutRegeneratingEvenWithCachedContentAndLoading() = runDesktopComposeUiTest {
        var retries = 0
        setContent { OverlayFixture(AiSummaryState(content = cached, generating = true, failed = true,
            facePrivacyBlocked = true, facePrivacyUnavailable = true), regenerate = { retries++ }) }
        onNodeWithText(cached.narration).assertDoesNotExist()
        onNodeWithText(cached.tags.single()).assertDoesNotExist()
        onNodeWithContentDescription(getString(Res.string.ai_generated_badge)).assertDoesNotExist()
        onNodeWithContentDescription(getString(Res.string.ai_image_summary_generating)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.ai_image_summary_face_detection_failed)).assertDoesNotExist()
        onNodeWithContentDescription(getString(Res.string.ai_image_summary_face_privacy_blocked))
            .assertExists().assertHasClickAction().performClick()
        val message = getString(Res.string.ai_image_summary_face_privacy_blocked)
        waitUntil { ToastManager.currentToastFlow.value?.message == message }
        onNodeWithContentDescription(message).performTouchInput { doubleClick() }
        runOnIdle { assertEquals(0, retries) }
    }

    @Test fun pendingNeverShowsAiIconBeforeTheFaceIndicator() = runDesktopComposeUiTest {
        var state by mutableStateOf(AiSummaryState(facePrivacyPending = true, generating = true))
        setContent { OverlayFixture(state) }
        val face = getString(Res.string.ai_image_summary_face_privacy_blocked)
        val ai = getString(Res.string.ai_image_summary_generating)
        onNodeWithContentDescription(ai).assertDoesNotExist()
        onNodeWithContentDescription(face).assertDoesNotExist()
        runOnIdle { state = AiSummaryState(facePrivacyBlocked = true) }
        onNodeWithContentDescription(ai).assertDoesNotExist()
        onNodeWithContentDescription(face).assertIsDisplayed().assertHasClickAction()
    }

    @Test fun privacyIndicatorCanRenderWithoutAProfile() = runDesktopComposeUiTest {
        setContent { OverlayFixture(AiSummaryState(facePrivacyBlocked = true), hasProfile = false) }
        onNodeWithContentDescription(getString(Res.string.ai_image_summary_face_privacy_blocked)).assertExists()
        onNodeWithContentDescription(getString(Res.string.ai_generated_badge)).assertDoesNotExist()
    }

    @Test fun missingProfileHidesCachedSummaryLoadingAndAiFailure() = runDesktopComposeUiTest {
        setContent { OverlayFixture(AiSummaryState(content = cached, generating = true, failed = true), hasProfile = false) }
        onNodeWithText(cached.narration).assertDoesNotExist()
        onNodeWithText(cached.tags.single()).assertDoesNotExist()
        onNodeWithContentDescription(getString(Res.string.ai_generated_badge)).assertDoesNotExist()
        onNodeWithContentDescription(getString(Res.string.ai_image_summary_generating)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.ai_image_summary_failed)).assertDoesNotExist()
    }

    @Test fun unavailableOverridesCacheAndAllowsRetryWithoutAProfile() = runDesktopComposeUiTest {
        var retries = 0
        setContent { OverlayFixture(AiSummaryState(content = cached, generating = true, facePrivacyUnavailable = true),
            hasProfile = false, regenerate = { retries++ }) }
        onNodeWithText(cached.narration).assertDoesNotExist()
        onNodeWithText(cached.tags.single()).assertDoesNotExist()
        onNodeWithContentDescription(getString(Res.string.ai_image_summary_generating)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.ai_image_summary_face_detection_failed))
            .assertExists().performTouchInput { doubleClick() }
        runOnIdle { assertEquals(1, retries) }
    }

    @Composable private fun ProviderFixture(store: MemoryStore) {
        val scope = rememberCoroutineScope()
        val engine = remember { AiEngine(store, UnusedFiles, AiModel.builder().registerBuiltIns().build(), scope, { it }, { it }) }
        MaterialTheme { AiProviderPage(engineOverride = engine) {} }
    }

    @Composable private fun OverlayFixture(state: AiSummaryState, hasProfile: Boolean = true, regenerate: () -> Unit = {}) {
        val bitmap = remember { org.jetbrains.skia.Bitmap().apply { allocN32Pixels(100, 100) } }
        DisposableEffect(bitmap) { onDispose { bitmap.close() } }
        val image = remember(bitmap) { bitmap.asImage() }
        MaterialTheme { Box(Modifier.fillMaxSize()) { AiSummaryOverlay(state, image, true, hasProfile, regenerate) } }
    }

    private val cached = AiSummaryContent("A cached summary", "A private cached narration", listOf("private cached tag"))

    private class MemoryStore(var value: AiLibrary? = null) : ObjectStore<AiLibrary> {
        var failWrites = false
        override suspend fun set(value: AiLibrary) { check(!failWrites); this.value = value }
        override suspend fun get() = value
        override suspend fun delete() { value = null }
    }

    private object UnusedFiles : AiFiles {
        override suspend fun write(name: String, bytes: ByteArray) = error("No images expected")
        override suspend fun read(name: String): ByteArray = error("No images expected")
        override suspend fun delete(name: String) = Unit
        override fun imageModel(name: String): Any = name
    }
}
