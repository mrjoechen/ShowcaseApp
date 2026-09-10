package com.alpha.showcase.common.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.graphics.asSkiaBitmap
import coil3.asImage
import com.alpha.ai.imagegeneration.*
import com.alpha.ai.imagegeneration.provider.registerBuiltIns
import com.alpha.showcase.common.storage.ObjectStore
import com.alpha.showcase.common.ui.ai.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.compose.resources.getString
import okio.buffer
import java.io.File
import showcaseapp.composeapp.generated.resources.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class AiConfigurationFlowTest {
    @Test fun newConfigurationUsesTheProviderAndModelAsItsName() = runDesktopComposeUiTest(width = 800, height = 1000) {
        lateinit var engine: AiEngine
        setContent {
            val scope = rememberCoroutineScope()
            engine = remember { AiEngine(MemoryStore(), MemoryFiles(), FakeClient(), scope, { it }, { it }) }
            MaterialTheme { AiProviderDialog(engineOverride = engine) {} }
        }
        waitForIdle()
        onNodeWithText(getString(Res.string.ai_new_configuration)).performClick()
        onNodeWithText(getString(Res.string.ai_token)).performTextInput("test-token")
        onNodeWithText(getString(Res.string.save)).performClick()
        waitForIdle()
        assertEquals("OpenAI Images · gpt-image-1", engine.library.value.activeProfiles.singleOrNull()?.name)
    }

    @Test fun selectingAGenerationProfilePersistsBeforeGenerating() = runDesktopComposeUiTest(width = 800, height = 1000) {
        lateinit var engine: AiEngine
        setContent {
            val scope = rememberCoroutineScope()
            engine = remember { AiEngine(MemoryStore(AiLibrary(profiles = listOf(profile("first"), profile("second")),
                generationProfileId = "first")), MemoryFiles(), FakeClient(), scope, { it }, { it }) }
            val bitmap = remember { org.jetbrains.skia.Bitmap().apply { allocN32Pixels(32, 32); erase(org.jetbrains.skia.Color.BLUE) } }
            DisposableEffect(bitmap) { onDispose { bitmap.close() } }
            val image = remember(bitmap) { bitmap.asImage() }
            MaterialTheme { AiGeneratorPage(image, engineOverride = engine) {} }
        }
        waitForIdle()
        onNodeWithText("second").performClick().assertIsSelected()
        waitForIdle()
        assertEquals("second", engine.library.value.generationProfileId)
        onNodeWithText(getString(Res.string.ai_generate_action)).assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 10_000) { engine.library.value.tasks.singleOrNull()?.status == AiTaskStatus.SUCCEEDED }
        assertEquals("second", engine.library.value.tasks.single().profileId)
    }

    @Test fun generationWaitsForTheChosenProfileToBeSaved() = runDesktopComposeUiTest(width = 800, height = 1000) {
        val store = MemoryStore(AiLibrary(profiles = listOf(profile("first"), profile("second")), generationProfileId = "first"))
        lateinit var engine: AiEngine
        setContent {
            val scope = rememberCoroutineScope()
            engine = remember { AiEngine(store, MemoryFiles(), FakeClient(), scope, { it }, { it }) }
            val bitmap = remember { org.jetbrains.skia.Bitmap().apply { allocN32Pixels(32, 32); erase(org.jetbrains.skia.Color.BLUE) } }
            DisposableEffect(bitmap) { onDispose { bitmap.close() } }
            val image = remember(bitmap) { bitmap.asImage() }
            MaterialTheme { AiGeneratorPage(image, engineOverride = engine) {} }
        }
        waitForIdle()
        onNodeWithText(getString(Res.string.ai_generate_action)).assertIsEnabled()
        val saved = CompletableDeferred<Unit>()
        runOnIdle { store.writeGate = saved }
        onNodeWithText("second").performClick()
        try {
            onNodeWithText(getString(Res.string.ai_generate_action)).assertIsNotEnabled()
        } finally { saved.complete(Unit) }
        waitForIdle()
        onNodeWithText("second").assertIsSelected()
        onNodeWithText(getString(Res.string.ai_generate_action)).assertIsEnabled()
    }

    @Test fun understandingOnlySetupCanAddAGenerationProfileAndGenerateWithoutReopening() = runDesktopComposeUiTest(width = 390, height = 844) {
        val understanding = profile("Summary service").copy(providerId = "openai-vision", model = "gpt-4o-mini")
        lateinit var engine: AiEngine
        setContent {
            val scope = rememberCoroutineScope()
            engine = remember { AiEngine(MemoryStore(AiLibrary(profiles = listOf(understanding), understandingProfileId = understanding.id)),
                MemoryFiles(), FakeClient(), scope, { it }, { it }) }
            val bitmap = remember { org.jetbrains.skia.Bitmap().apply { allocN32Pixels(32, 32); erase(org.jetbrains.skia.Color.BLUE) } }
            DisposableEffect(bitmap) { onDispose { bitmap.close() } }
            val image = remember(bitmap) { bitmap.asImage() }
            MaterialTheme { AiGeneratorPage(image, engineOverride = engine) {} }
        }
        waitForIdle()
        onNodeWithText(understanding.name).assertDoesNotExist()
        onNodeWithText(getString(Res.string.ai_generation_profile_required)).assertExists()
        onNodeWithText(getString(Res.string.ai_generate_action)).assertIsNotEnabled()
        onNodeWithText(getString(Res.string.ai_configure_service)).performScrollTo().performClick()
        onNodeWithText(getString(Res.string.ai_new_configuration)).performClick()
        onNodeWithText(getString(Res.string.ai_token)).performTextInput("test-generation-token")
        saveScreenshot("ai-editor-phone", onAllNodes(isDialog()).onLast().captureToImage())
        onNodeWithText(getString(Res.string.save)).performClick()
        waitForIdle()
        val generation = engine.library.value.activeProfiles.single { it.providerId == "openai" }
        assertEquals("test-generation-token", generation.encryptedToken)
        onNodeWithText(getString(Res.string.ai_capability_image_understanding)).performClick()
        onNodeWithText(understanding.name).assertExists()
        onNode(hasText(generation.name) and SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.RadioButton)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.ai_capability_image_to_image)).performClick()
        saveScreenshot("ai-profiles-phone", onAllNodes(isDialog()).onLast().captureToImage())
        onAllNodesWithContentDescription(getString(Res.string.back)).onLast().performClick()
        onNodeWithText(generation.name).assertIsSelected()
        saveScreenshot("ai-generator-phone", onRoot().captureToImage())
        onNodeWithText(getString(Res.string.ai_generate_action)).assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 10_000) { engine.library.value.tasks.singleOrNull()?.status == AiTaskStatus.SUCCEEDED }
        assertEquals(generation.id, engine.library.value.tasks.single().profileId)
        assertEquals(understanding, engine.library.value.activeProfiles.single { it.providerId == "openai-vision" })
    }

    @Test fun editingAProfileCanChooseALoadedModelAndKeepItsStoredToken() = runDesktopComposeUiTest(width = 390, height = 844) {
        val existing = profile("My image service")
        lateinit var engine: AiEngine
        setContent {
            val scope = rememberCoroutineScope()
            engine = remember { AiEngine(MemoryStore(AiLibrary(profiles = listOf(existing), generationProfileId = existing.id)),
                MemoryFiles(), FakeClient(), scope, { it }, { it }) }
            MaterialTheme { AiProviderDialog(engineOverride = engine) {} }
        }
        waitForIdle()
        onNodeWithContentDescription(getString(Res.string.ai_profile_edit_named, existing.name)).performClick()
        onNodeWithContentDescription(getString(Res.string.ai_model_catalog_load)).performClick()
        waitForIdle()
        onNodeWithText("Second image model").performClick()
        onNodeWithText(getString(Res.string.save)).performClick()
        waitForIdle()
        val updated = engine.library.value.activeProfiles.single()
        assertEquals("image-two", updated.model)
        assertEquals("test-token", updated.encryptedToken)
        assertEquals(existing.name, updated.name)
        assertEquals(2, updated.revision)
        assertEquals(existing.id, engine.library.value.generationProfileId)
    }

    private fun saveScreenshot(name: String, image: androidx.compose.ui.graphics.ImageBitmap) {
        val folder = File("build/ai-verification").apply { mkdirs() }
        org.jetbrains.skia.Image.makeFromBitmap(image.asSkiaBitmap()).use { snapshot ->
            snapshot.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.use { File(folder, "$name.png").writeBytes(it.bytes) }
        }
    }

    private fun profile(id: String) = AiProfile(id, name = id, providerId = "openai", model = "gpt-image-1",
        baseUrl = "https://api.example/v1", encryptedToken = "test-token")

    private class MemoryStore(private var value: AiLibrary? = null) : ObjectStore<AiLibrary> {
        var writeGate: CompletableDeferred<Unit>? = null
        override suspend fun get() = value
        override suspend fun set(value: AiLibrary) { writeGate?.await(); this.value = value }
        override suspend fun delete() { value = null }
    }
    private class MemoryFiles : AiFiles {
        private val data = mutableMapOf<String, ByteArray>()
        override suspend fun write(name: String, bytes: ByteArray) { data[name] = bytes }
        override suspend fun read(name: String) = data.getValue(name)
        override suspend fun delete(name: String) { data.remove(name) }
        override fun imageModel(name: String): Any = data[name] ?: name
    }
    private class FakeClient : AiModelClient by AiModel.builder().registerBuiltIns().build() {
        override suspend fun listModels(request: ProviderModelCatalogRequest) = ProviderModelCatalogResult.Available(
            request.providerId, request.capability, listOf(ProviderModel("image-two", "Second image model")))
        override fun generateImage(request: GenerateImageRequest, config: ProviderRuntimeConfig): Flow<ImageGenerationEvent> = flowOf(
            ImageGenerationEvent.Completed(request.operationId, GenerationResult.Success(
                GeneratedImage(request.source.openSource().buffer().use { it.readByteArray() }, "image/jpeg",
                    providerId = config.providerId, model = config.model))))
    }
}
