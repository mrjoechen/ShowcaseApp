package com.alpha.showcase.common.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.asImage
import com.alpha.ai.imagegeneration.AiModel
import com.alpha.ai.imagegeneration.provider.registerBuiltIns
import com.alpha.showcase.common.storage.ObjectStore
import com.alpha.showcase.common.ui.ai.*
import com.alpha.showcase.common.ui.play.LocalPlaybackActive
import org.jetbrains.compose.resources.getString
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import showcaseapp.composeapp.generated.resources.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class AiPageNavigationTest {
    @Test fun settingsCreationPreviewReturnsThroughTheMainBackStack() = runDesktopComposeUiTest(width = 440, height = 900) {
        lateinit var nav: NavHostController
        setContent {
            nav = rememberNavController()
            val scope = rememberCoroutineScope()
            val engine = remember { fixtureEngine(scope, withTask = true) }
            MaterialTheme {
                AiNavigationHost(engine) {
                    NavHost(nav, startDestination = "settings") {
                        composable("settings") {
                            AiClientSettings(
                                isBrowser = false,
                                onOpenProviders = { nav.navigate(AI_PROVIDER_ROUTE) },
                                onOpenCreations = { nav.navigate(AI_CREATIONS_ROUTE) },
                            )
                        }
                        aiProviderDestination(nav, engine)
                        aiCreationDestinations(nav, engine)
                    }
                }
            }
        }
        onNodeWithText(getString(Res.string.ai_creation_center_title)).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithContentDescription(getString(Res.string.ai_generated_image_description)).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertEquals("$AI_CREATION_PREVIEW_ROUTE/{taskId}", nav.currentBackStackEntry?.destination?.route)
        assertEquals(AI_CREATIONS_ROUTE, nav.previousBackStackEntry?.destination?.route)
        onAllNodes(isDialog()).assertCountEquals(0)
        onNodeWithContentDescription(getString(Res.string.ai_generated_image_description)).assertIsDisplayed()
        onNodeWithContentDescription(getString(Res.string.back)).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertEquals(AI_CREATIONS_ROUTE, nav.currentBackStackEntry?.destination?.route)
        onNodeWithContentDescription(getString(Res.string.back)).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertEquals("settings", nav.currentBackStackEntry?.destination?.route)
        onNodeWithText(getString(Res.string.ai_provider_settings_title)).assertIsDisplayed()
    }

    @Test fun desktopPageHeadersMatchImageServices() = runDesktopComposeUiTest(width = 1280, height = 900) {
        val bitmap = Bitmap().apply { allocN32Pixels(240, 160); erase(0xff327c85.toInt()) }
        val image = bitmap.asImage()
        setContent {
            DisposableEffect(Unit) { onDispose { bitmap.close() } }
            val scope = rememberCoroutineScope()
            val engine = remember { fixtureEngine(scope) }
            MaterialTheme { AiNavigationHost(engine) {
                val navigation = LocalAiNavigation.current!!
                Column {
                    Button(onClick = navigation.providers) { Text("Open services") }
                    Button(onClick = { navigation.generate(image) }) { Text("Open generator") }
                }
            } }
        }
        val back = onNodeWithContentDescription(getString(Res.string.back))
        onNodeWithText("Open services").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        val serviceBackBounds = back.fetchSemanticsNode().boundsInRoot
        val serviceTitleBounds = onNodeWithText(getString(Res.string.ai_provider_settings_title)).fetchSemanticsNode().boundsInRoot
        saveScreenshot("ai-services-desktop", onRoot().captureToImage())
        back.performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithText("Open generator").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        saveScreenshot("ai-generator-desktop", onRoot().captureToImage())
        val generatorBackBounds = back.fetchSemanticsNode().boundsInRoot
        val generatorTitleBounds = onNodeWithText(getString(Res.string.ai_generate_title)).fetchSemanticsNode().boundsInRoot
        onNodeWithText(getString(Res.string.ai_creation_center_title)).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        saveScreenshot("ai-creations-empty-desktop", onRoot().captureToImage())
        val creationsBackBounds = back.fetchSemanticsNode().boundsInRoot
        val creationsTitleBounds = onNodeWithText(getString(Res.string.ai_creation_center_title)).fetchSemanticsNode().boundsInRoot
        assertEquals(serviceBackBounds, generatorBackBounds, "Generator back button must match image services")
        assertEquals(serviceBackBounds, creationsBackBounds, "Creations back button must match image services")
        for (bounds in listOf(generatorTitleBounds, creationsTitleBounds)) {
            assertEquals(serviceTitleBounds.topLeft, bounds.topLeft, "Page title must match image services position")
            assertEquals(serviceTitleBounds.height, bounds.height, "Page title must match image services typography")
        }
    }

    @Test fun generatorBackButtonWorksOnRepeatedVisits() = runDesktopComposeUiTest(width = 1280, height = 900) {
        val bitmap = Bitmap().apply { allocN32Pixels(240, 160); erase(0xff327c85.toInt()) }
        val image = bitmap.asImage()
        var playbackActive = true
        setContent {
            DisposableEffect(Unit) { onDispose { bitmap.close() } }
            val scope = rememberCoroutineScope()
            val engine = remember { fixtureEngine(scope) }
            MaterialTheme { AiNavigationHost(engine) {
                playbackActive = LocalPlaybackActive.current
                val navigation = LocalAiNavigation.current!!
                Button(onClick = { navigation.generate(image) }) { Text("Open generator") }
            } }
        }
        repeat(3) { visit ->
            onNodeWithText("Open generator").performSemanticsAction(SemanticsActions.OnClick)
            waitForIdle()
            onNodeWithText(getString(Res.string.ai_generate_title)).assertIsDisplayed()
            val back = onNodeWithContentDescription(getString(Res.string.back))
            runOnUiThread { back.performMouseInput { click() } }
            waitForIdle()
            saveScreenshot("ai-return-visit-${visit + 1}", onRoot().captureToImage())
            runOnIdle { assertTrue(playbackActive, "Visit ${visit + 1} must return to playback") }
            onNodeWithText("Open generator").assertIsDisplayed()
        }
    }

    @Test fun generatorAndCreationsUsePagesAndPreserveCoveredPlayback() = runDesktopComposeUiTest(width = 440, height = 900) {
        val bitmap = Bitmap().apply { allocN32Pixels(240, 160); erase(0xff327c85.toInt()) }
        val image = bitmap.asImage()
        var playbackActive = true
        var compositionCount = 0
        setContent {
            DisposableEffect(Unit) { onDispose { bitmap.close() } }
            val scope = rememberCoroutineScope()
            val engine = remember { fixtureEngine(scope) }
            MaterialTheme { AiNavigationHost(engine) {
                remember { compositionCount++; true }
                playbackActive = LocalPlaybackActive.current
                val navigation = LocalAiNavigation.current!!
                Column { Button(onClick = { navigation.generate(image) }) { Text("Open generator") } }
            } }
        }
        onNodeWithText("Open generator").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onAllNodes(isDialog()).assertCountEquals(0)
        onNodeWithText(getString(Res.string.ai_generate_title)).assertIsDisplayed()
        runOnIdle { assertFalse(playbackActive) }
        onNodeWithText(getString(Res.string.ai_creation_center_title)).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onAllNodes(isDialog()).assertCountEquals(0)
        onNodeWithText(getString(Res.string.ai_creations_empty)).assertIsDisplayed()
        onNodeWithContentDescription(getString(Res.string.back)).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithText(getString(Res.string.ai_generate_title)).assertIsDisplayed()
        onNodeWithContentDescription(getString(Res.string.back)).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithText("Open generator").assertIsDisplayed()
        runOnIdle { assertTrue(playbackActive); assertEquals(1, compositionCount) }
    }

    @Test fun creationGalleryOpensFullscreenBrowserWithOriginalAndInformation() = runDesktopComposeUiTest(width = 440, height = 900) {
        setContent {
            val scope = rememberCoroutineScope()
            val engine = remember { fixtureEngine(scope, withTask = true) }
            MaterialTheme { AiNavigationHost(engine) {
                val navigation = LocalAiNavigation.current!!
                Button(onClick = navigation.creations) { Text("Open creations") }
            } }
        }
        onNodeWithText("Open creations").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithContentDescription(getString(Res.string.ai_generated_image_description)).assertExists()
        saveScreenshot("ai-creations-phone", onRoot().captureToImage())
        onNodeWithContentDescription(getString(Res.string.ai_generated_image_description)).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onAllNodes(isDialog()).assertCountEquals(0)
        onNodeWithText(getString(Res.string.ai_task_detail_title)).assertIsDisplayed()
        onNodeWithContentDescription(getString(Res.string.ai_more_actions)).performSemanticsAction(SemanticsActions.OnClick)
        onNodeWithText(getString(Res.string.ai_show_original)).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithContentDescription(getString(Res.string.ai_source_image_description)).assertIsDisplayed()
        onNodeWithText("Unsupported data").assertDoesNotExist()
        onNodeWithContentDescription(getString(Res.string.ai_more_actions)).performSemanticsAction(SemanticsActions.OnClick)
        onNodeWithText(getString(Res.string.ai_creation_info)).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithText(getString(Res.string.ai_original_metadata)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.ai_upload_snapshot_notice)).assertIsDisplayed()
        saveScreenshot("ai-detail-phone", onAllNodes(isRoot()).onLast().captureToImage())
    }

    @Test fun galleryBrowsesAdjacentCreationsAndDeletionReturnsToEmptyGallery() = runDesktopComposeUiTest(width = 1280, height = 900) {
        lateinit var engine: AiEngine
        setContent {
            val scope = rememberCoroutineScope()
            engine = remember { fixtureEngine(scope, withTask = true, taskCount = 6) }
            MaterialTheme { AiNavigationHost(engine) {
                val navigation = LocalAiNavigation.current!!
                Button(onClick = navigation.creations) { Text("Open creations") }
            } }
        }
        onNodeWithText("Open creations").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithText(getString(Res.string.ai_tasks)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.ai_gallery)).assertDoesNotExist()
        saveScreenshot("ai-creations-grid-desktop", onRoot().captureToImage())
        onAllNodesWithContentDescription(getString(Res.string.ai_generated_image_description)).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        saveScreenshot("ai-preview-desktop", onRoot().captureToImage())
        val image = onAllNodesWithContentDescription(getString(Res.string.ai_generated_image_description)).onFirst()
        runOnUiThread { image.performMouseInput { click() } }
        mainClock.advanceTimeBy(800) // Allow single-tap recognition after the double-tap window.
        waitForIdle()
        onNodeWithContentDescription(getString(Res.string.ai_more_actions)).assertDoesNotExist()
        runOnUiThread { image.performMouseInput { click() } }
        mainClock.advanceTimeBy(800)
        waitForIdle()
        onNodeWithContentDescription(getString(Res.string.ai_more_actions)).assertIsDisplayed()
        runOnUiThread { onRoot().performKeyInput { pressKey(Key.DirectionRight) } }
        waitForIdle()
        onNodeWithContentDescription(getString(Res.string.ai_previous_creation)).assertIsDisplayed()
        repeat(6) {
            val before = engine.library.value.tasks.size
            onNodeWithContentDescription(getString(Res.string.ai_more_actions)).performSemanticsAction(SemanticsActions.OnClick)
            onNodeWithText(getString(Res.string.delete)).performSemanticsAction(SemanticsActions.OnClick)
            onNodeWithText(getString(Res.string.cancel)).assertIsDisplayed()
            onNode(hasText(getString(Res.string.delete)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
            waitForIdle()
            runOnIdle { assertEquals(before - 1, engine.library.value.tasks.size) }
            if (it == 0) runOnIdle { assertFalse(engine.library.value.tasks.any { task -> task.id == "task-1" }) }
        }
        onNodeWithText(getString(Res.string.ai_creations_empty)).assertIsDisplayed()
        onNodeWithContentDescription(getString(Res.string.back)).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithText("Open creations").assertIsDisplayed()
    }

    private fun fixtureEngine(scope: kotlinx.coroutines.CoroutineScope, withTask: Boolean = false, taskCount: Int = 1): AiEngine {
        val task = AiTask("task", "profile", 1, "ghibli", "A quiet coastal landscape", 1_700_000_000_000,
            "source.png", "image/png", AiTaskStatus.SUCCEEDED, attempt = 1, resultFile = "result.png")
        val profile = AiProfile("profile", name = "Studio", providerId = "openai", model = "gpt-image-1", baseUrl = "https://api.openai.com/v1", encryptedToken = "unused")
        val store = object : ObjectStore<AiLibrary> {
            var value = AiLibrary(profiles = listOf(profile), tasks = if (withTask) List(taskCount) {
                task.copy(id = "task-$it", createdAt = task.createdAt - it, resultFile = "result-$it.png", originalName = "Photo $it")
            } else emptyList())
            override suspend fun get() = value
            override suspend fun set(value: AiLibrary) { this.value = value }
            override suspend fun delete() { value = AiLibrary() }
        }
        val imageBytes = List(taskCount) { index -> Bitmap().use { bitmap ->
            val heights = listOf(160, 320, 120, 240, 360, 192)
            bitmap.allocN32Pixels(240, heights[index % heights.size]); bitmap.erase((0xff327c85L + index * 0x181004).toInt())
            org.jetbrains.skia.Image.makeFromBitmap(bitmap).use { it.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes } }
        } }
        fun bytes(name: String): ByteArray {
            val index = name.substringAfter("result-", "0").substringBefore('.').toIntOrNull() ?: 0
            return imageBytes[index.coerceIn(imageBytes.indices)]
        }
        val files = object : AiFiles {
            override suspend fun write(name: String, bytes: ByteArray) = Unit
            override suspend fun read(name: String) = bytes(name)
            override suspend fun delete(name: String) = Unit
            override fun imageModel(name: String): Any = bytes(name)
        }
        return AiEngine(store, files, AiModel.builder().registerBuiltIns().build(), scope, { it }, { it })
    }

    private fun saveScreenshot(name: String, image: androidx.compose.ui.graphics.ImageBitmap) {
        val folder = File("build/ai-verification").apply { mkdirs() }
        org.jetbrains.skia.Image.makeFromBitmap(image.asSkiaBitmap()).use { snapshot ->
            snapshot.encodeToData(EncodedImageFormat.PNG)!!.use { File(folder, "$name.png").writeBytes(it.bytes) }
        }
    }
}
