package com.alpha.showcase.common.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.alpha.ai.imagegeneration.*
import com.alpha.ai.imagegeneration.provider.registerBuiltIns
import com.alpha.showcase.common.storage.ObjectStore
import com.alpha.showcase.common.ui.ai.*
import kotlinx.coroutines.flow.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import coil3.asImage
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray
import showcaseapp.composeapp.generated.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AiUiTest {
    @Test fun browserSettingsHideEveryAiEntry() = runDesktopComposeUiTest {
        setContent { MaterialTheme { AiClientSettings(isBrowser = true) } }
        onNodeWithText(getString(Res.string.ai_provider_settings_title)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.ai_creation_center_title)).assertDoesNotExist()
    }

    @Test fun nativeSettingsExposeAiConfiguration() = runDesktopComposeUiTest {
        setContent { MaterialTheme { AiClientSettings(isBrowser = false) } }
        onNodeWithText(getString(Res.string.ai_provider_settings_title)).assertExists()
    }

    @Test fun nativeSettingsHideCreations() = runDesktopComposeUiTest {
        setContent { MaterialTheme { AiClientSettings(isBrowser = false) } }
        onNodeWithText(getString(Res.string.ai_creation_center_title)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.ai_provider_settings_title)).assertExists()
    }

    @Test fun defaultConfigurationOnlyOffersUnderstanding() = runDesktopComposeUiTest(width = 800, height = 1000) {
        setContent {
            val scope = rememberCoroutineScope()
            val engine = androidx.compose.runtime.remember {
                AiEngine(MemoryStore(), UnusedFiles, AiModel.builder().registerBuiltIns().build(), scope, { it }, { it })
            }
            MaterialTheme { AiProviderDialog(engineOverride = engine) {} }
        }
        onNodeWithText(getString(Res.string.ai_capability_image_to_image)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.ai_capability_image_understanding)).assertExists().assertHasNoClickAction()
        onNodeWithText(getStringArray(Res.array.ai_summary_preview_city_narrations)[1]).assertIsDisplayed()
        saveScreenshot("ai-summary-configuration", onAllNodes(isDialog()).onLast().captureToImage())
        onNodeWithTag("ai-summary-preview-pager").performTouchInput { swipeLeft() }
        waitForIdle()
        onNodeWithText(getStringArray(Res.array.ai_summary_preview_autumn_narrations)[3]).assertIsDisplayed()
        onNodeWithContentDescription(getString(Res.string.ai_summary_preview_example, 2, 2)).assertIsSelected()
        saveScreenshot("ai-summary-configuration-autumn", onAllNodes(isDialog()).onLast().captureToImage())
        onNodeWithContentDescription(getString(Res.string.ai_summary_preview_example, 1, 2)).performClick()
        waitForIdle()
        onNodeWithText(getStringArray(Res.array.ai_summary_preview_city_narrations)[1]).assertIsDisplayed()
        onNodeWithText(getString(Res.string.ai_new_configuration)).performClick()
        onNodeWithText("gpt-4o-mini").assertExists()
        onNodeWithText("gpt-image-1").assertDoesNotExist()
    }

    @Test fun summaryPreviewCyclesCaptionsAndRemembersEachPhoto() = runDesktopComposeUiTest(width = 600, height = 600) {
        val city = getStringArray(Res.array.ai_summary_preview_city_narrations)
        val autumn = getStringArray(Res.array.ai_summary_preview_autumn_narrations)
        setContent { MaterialTheme { AiSummaryPreview() } }
        val pager = onNodeWithTag("ai-summary-preview-pager")
        city.indices.forEach { step ->
            onNodeWithText(city[(1 + step) % city.size]).assertIsDisplayed()
                .performTouchInput { doubleClick() }
            onNodeWithText(city[(2 + step) % city.size]).assertIsDisplayed()
        }
        pager.performTouchInput { swipeLeft() }
        autumn.indices.forEach { step ->
            onNodeWithText(autumn[(3 + step) % autumn.size]).assertIsDisplayed()
                .performTouchInput { doubleClick() }
            onNodeWithText(autumn[(4 + step) % autumn.size]).assertIsDisplayed()
        }
        onNodeWithText(autumn[3]).performTouchInput { doubleClick() }
        pager.performTouchInput { swipeRight() }
        onNodeWithText(city[1]).assertIsDisplayed()
        pager.performTouchInput { swipeLeft() }
        val actions = onNodeWithText(autumn[4]).assertIsDisplayed()
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        runOnIdle { assertTrue(actions.single().action()) }
        onNodeWithText(autumn[0]).assertIsDisplayed()
        onNodeWithText(getString(Res.string.ai_summary_preview_description)).assertIsDisplayed()
    }

    @Test fun summaryPreviewSupportsMouseDraggingWithoutChangingCaptions() = runDesktopComposeUiTest(width = 600, height = 600) {
        val city = getStringArray(Res.array.ai_summary_preview_city_narrations)
        val autumn = getStringArray(Res.array.ai_summary_preview_autumn_narrations)
        setContent { MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AiSummaryPreview()
                Spacer(Modifier.height(800.dp))
            }
        } }
        val pager = onNodeWithTag("ai-summary-preview-pager")
        pager.performMouseInput {
            moveTo(Offset(width * 0.8f, centerY))
            press()
            repeat(12) { step ->
                moveTo(Offset(width * (0.8f - 0.6f * (step + 1) / 12), centerY), delayMillis = 16)
            }
            release()
        }
        onNodeWithText(autumn[3]).assertIsDisplayed()
        onNodeWithContentDescription(getString(Res.string.ai_summary_preview_example, 2, 2)).assertIsSelected()
        pager.performMouseInput {
            moveTo(Offset(width * 0.2f, centerY))
            press()
            repeat(12) { step ->
                moveTo(Offset(width * (0.2f + 0.6f * (step + 1) / 12), centerY), delayMillis = 16)
            }
            release()
        }
        onNodeWithText(city[1]).assertIsDisplayed()
        pager.performMouseInput {
            moveTo(center)
            press()
            moveTo(center + Offset(10f, 0f), delayMillis = 200)
            release()
        }
        onNodeWithText(city[1]).assertIsDisplayed()
        onNodeWithText(city[1]).performMouseInput { doubleClick() }
        onNodeWithText(city[2]).assertIsDisplayed()
    }

    @Test fun summaryIndicatorPressDoesNotPaintItsRectangularHitArea() = runDesktopComposeUiTest(width = 600, height = 480) {
        setContent { MaterialTheme {
            androidx.compose.material3.Surface { AiSummaryPreview() }
        } }
        for (page in 1..2) {
            val indicator = onNodeWithContentDescription(getString(Res.string.ai_summary_preview_example, page, 2))
            val before = indicator.captureToImage().toPixelMap()
            indicator.performMouseInput { moveTo(center); press() }
            mainClock.advanceTimeBy(300)
            val pressed = indicator.captureToImage().toPixelMap()
            saveScreenshot("ai-summary-indicator-pressed-$page", onRoot().captureToImage())
            indicator.performMouseInput { release() }
            assertEquals(before[before.width / 2, 0], pressed[pressed.width / 2, 0],
                "Indicator press must not paint the padding above the dot")
            assertEquals(before[0, before.height / 2], pressed[0, pressed.height / 2],
                "Indicator press must not paint the padding beside the dot")
        }
    }

    @Test fun providerDialogShowsEditableModelAndSeparatesCapabilities() = runDesktopComposeUiTest(width = 800, height = 1000) {
        setContent {
            val scope = rememberCoroutineScope()
            val engine = androidx.compose.runtime.remember {
                AiEngine(MemoryStore(), UnusedFiles, AiModel.builder().registerBuiltIns().build(), scope, { it }, { it })
            }
            AiGenerationTestTheme { AiProviderDialog(engineOverride = engine) {} }
        }
        waitForIdle()
        onNodeWithText(getString(Res.string.ai_new_configuration)).performClick()
        onNodeWithText("gpt-image-1").assertExists()
        onNodeWithContentDescription(getString(Res.string.close)).performClick()
        onNodeWithText(getString(Res.string.ai_capability_image_understanding)).performClick()
        onNodeWithText(getString(Res.string.ai_new_configuration)).performClick()
        onNodeWithText("gpt-4o-mini").assertExists()
        onNodeWithText(getString(Res.string.ai_provider)).performClick()
        onNodeWithText(getString(Res.string.ai_provider_deepseek_vision)).assertExists()
        onNodeWithText(getString(Res.string.ai_provider_deepseek_vision)).performClick()
        onNodeWithText(getString(Res.string.ai_model), useUnmergedTree = true).assertExists()
        saveScreenshot("ai-provider", onAllNodes(isDialog()).onLast().captureToImage())
    }

    @Test fun styleChoicesKeepTheOriginalPresentation() = runDesktopComposeUiTest(width = 660, height = 360) {
        setContent { MaterialTheme {
            androidx.compose.material3.Surface { Column(Modifier.fillMaxSize().padding(24.dp)) {
                AiStyleChoices(LocalAiStyleCatalog.styles(), "ghibli", true, {})
            } }
        } }
        onNodeWithText(getString(Res.string.ai_style_ghibli_name)).assertExists()
        saveScreenshot("ai-styles", onRoot().captureToImage())
    }

    @Test fun reopeningGeneratorRestoresTheSavedStyle() = runDesktopComposeUiTest(width = 800, height = 1000) {
        setContent {
            val scope = rememberCoroutineScope()
            val engine = androidx.compose.runtime.remember {
                AiEngine(MemoryStore(AiLibrary(styleKey = "cyberpunk")), UnusedFiles,
                    AiModel.builder().registerBuiltIns().build(), scope, { it }, { it })
            }
            val bitmap = androidx.compose.runtime.remember {
                org.jetbrains.skia.Bitmap().apply { allocN32Pixels(32, 32); erase(org.jetbrains.skia.Color.BLUE) }
            }
            androidx.compose.runtime.DisposableEffect(bitmap) { onDispose { bitmap.close() } }
            val image = androidx.compose.runtime.remember(bitmap) { bitmap.asImage() }
            MaterialTheme { AiGeneratorPage(image, engineOverride = engine) {} }
        }
        val selectedLabel = getString(Res.string.ai_style_cyberpunk_name)
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(selectedLabel).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(selectedLabel).assertIsSelected()
        saveScreenshot("ai-generator", onRoot().captureToImage())
    }

    private fun saveScreenshot(name: String, image: androidx.compose.ui.graphics.ImageBitmap) {
        val folder = File("build/ai-verification").apply { mkdirs() }
        org.jetbrains.skia.Image.makeFromBitmap(image.asSkiaBitmap()).use { snapshot ->
            snapshot.encodeToData(EncodedImageFormat.PNG)!!.use { File(folder, "$name.png").writeBytes(it.bytes) }
        }
    }

    private class MemoryStore(private var library: AiLibrary? = null) : ObjectStore<AiLibrary> {
        override suspend fun set(value: AiLibrary) { library = value }
        override suspend fun get() = library
        override suspend fun delete() { library = null }
    }
    private object UnusedFiles : AiFiles {
        override suspend fun write(name: String, bytes: ByteArray) = error("No images expected")
        override suspend fun read(name: String): ByteArray = error("No images expected")
        override suspend fun delete(name: String) = Unit
        override fun imageModel(name: String): Any = name
    }
}
