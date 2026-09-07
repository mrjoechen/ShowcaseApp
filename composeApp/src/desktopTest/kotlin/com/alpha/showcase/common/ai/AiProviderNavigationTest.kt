package com.alpha.showcase.common.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.alpha.ai.imagegeneration.AiModel
import com.alpha.ai.imagegeneration.provider.registerBuiltIns
import com.alpha.showcase.common.storage.ObjectStore
import com.alpha.showcase.common.ui.ai.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.compose.resources.getString
import showcaseapp.composeapp.generated.resources.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class AiProviderNavigationTest {
    @Test fun settingsEntryPushesAFullPageAndReturnsAfterEditing() = runDesktopComposeUiTest(width = 900, height = 700) {
        lateinit var nav: NavHostController
        setContent {
            nav = rememberNavController()
            val scope = rememberCoroutineScope()
            val engine = remember { AiEngine(MemoryStore(), UnusedFiles, AiModel.builder().registerBuiltIns().build(), scope, { it }, { it }) }
            MaterialTheme {
                NavHost(nav, startDestination = "settings", modifier = Modifier.fillMaxSize()) {
                    composable("settings") {
                        Surface(Modifier.fillMaxSize()) {
                            AiClientSettings(isBrowser = false, onOpenProviders = { nav.navigate(AI_PROVIDER_ROUTE) })
                        }
                    }
                    aiProviderDestination(nav, engine)
                }
            }
        }
        // Navigation lifecycle updates must run on the UI thread; mouse injection in the
        // desktop test harness can execute callbacks on the test dispatcher instead.
        onNodeWithText(getString(Res.string.ai_provider_settings_title)).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertEquals(AI_PROVIDER_ROUTE, nav.currentBackStackEntry?.destination?.route)
        onAllNodes(isDialog()).assertCountEquals(0)
        onNodeWithContentDescription(getString(Res.string.back)).assertIsDisplayed()
        val image = onRoot().captureToImage()
        org.jetbrains.skia.Image.makeFromBitmap(image.asSkiaBitmap()).use { screenshot ->
            screenshot.encodeToData(EncodedImageFormat.PNG)!!.use {
                val folder = File("build/ai-verification").apply { mkdirs() }
                File(folder, "provider-navigation.png").writeBytes(it.bytes)
            }
        }
        onNodeWithText(getString(Res.string.ai_new_configuration)).performSemanticsAction(SemanticsActions.OnClick)
        onAllNodes(isDialog()).assertCountEquals(1)
        onNodeWithContentDescription(getString(Res.string.close)).performSemanticsAction(SemanticsActions.OnClick)
        onAllNodes(isDialog()).assertCountEquals(0)
        assertEquals(AI_PROVIDER_ROUTE, nav.currentBackStackEntry?.destination?.route)
        onNodeWithContentDescription(getString(Res.string.back)).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertEquals("settings", nav.currentBackStackEntry?.destination?.route)
        onNodeWithText(getString(Res.string.ai_provider_settings_title)).assertIsDisplayed()
    }

    @Test fun providerHeaderAvoidsDesktopWindowControls() = runDesktopComposeUiTest(width = 900, height = 700) {
        var scale = 1f
        setContent {
            scale = LocalDensity.current.density
            val scope = rememberCoroutineScope()
            val engine = remember { AiEngine(MemoryStore(), UnusedFiles, AiModel.builder().registerBuiltIns().build(), scope, { it }, { it }) }
            MaterialTheme { AiProviderDialog(engineOverride = engine) {} }
        }
        waitForIdle()
        val titleTop = onNodeWithText(getString(Res.string.ai_provider_settings_title)).fetchSemanticsNode().boundsInRoot.top / scale
        assertTrue(titleTop >= 36f, "Header overlaps desktop window controls: title top = $titleTop dp")
    }

    private class MemoryStore : ObjectStore<AiLibrary> {
        private var value: AiLibrary? = null
        override suspend fun get() = value
        override suspend fun set(value: AiLibrary) { this.value = value }
        override suspend fun delete() { value = null }
    }
    private object UnusedFiles : AiFiles {
        override suspend fun write(name: String, bytes: ByteArray) = error("unused")
        override suspend fun read(name: String): ByteArray = error("unused")
        override suspend fun delete(name: String) = Unit
        override fun imageModel(name: String): Any = error("unused")
    }
}
