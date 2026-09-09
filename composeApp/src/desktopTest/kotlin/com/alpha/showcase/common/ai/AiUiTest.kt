package com.alpha.showcase.common.ai

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
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
import showcaseapp.composeapp.generated.resources.*
import kotlin.test.Test

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

    @Test fun providerDialogShowsEditableModelAndSeparatesCapabilities() = runDesktopComposeUiTest(width = 800, height = 1000) {
        setContent {
            val scope = rememberCoroutineScope()
            val engine = androidx.compose.runtime.remember {
                AiEngine(MemoryStore(), UnusedFiles, AiModel.builder().registerBuiltIns().build(), scope, { it }, { it })
            }
            MaterialTheme { AiProviderDialog(engineOverride = engine) {} }
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
            MaterialTheme { AiGeneratorDialog(image, engineOverride = engine) {} }
        }
        val selectedLabel = getString(Res.string.ai_style_cyberpunk_name)
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(selectedLabel).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(selectedLabel).assertIsSelected()
        saveScreenshot("ai-generator", onNode(isDialog()).captureToImage())
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
