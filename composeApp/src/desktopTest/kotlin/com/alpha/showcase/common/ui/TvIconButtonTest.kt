package com.alpha.showcase.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.ic_face_privacy
import showcaseapp.composeapp.generated.resources.ic_face_privacy_checked

@OptIn(ExperimentalTestApi::class)
class TvIconButtonTest {
    @Test
    fun remoteFocusEnlargesIconsWithoutTogglingFacePrivacy() = runDesktopComposeUiTest(width = 420, height = 160) {
        lateinit var focusManager: FocusManager
        var checked by mutableStateOf(false)
        var toggles = 0
        var actions = 0
        setContent {
            focusManager = LocalFocusManager.current
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface {
                    Row(Modifier.padding(32.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvIconButton({ actions++ }, Modifier.testTag("info"), useTvStyle = true) {
                            Icon(Icons.Filled.Info, "Info", Modifier.size(24.dp).testTag("info-icon"))
                        }
                        TvIconButton({}, Modifier.testTag("disabled"), enabled = false, useTvStyle = true) {
                            Icon(Icons.Filled.Info, "Disabled")
                        }
                        TvIconToggleButton(checked, { checked = it; toggles++ },
                            Modifier.testTag("face"), useTvStyle = true) {
                            Icon(painterResource(if (checked) Res.drawable.ic_face_privacy_checked else Res.drawable.ic_face_privacy),
                                "Face privacy", Modifier.size(24.dp).testTag("face-icon"))
                        }
                    }
                }
            }
        }
        val infoWidth = onNodeWithTag("info-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
        val faceBounds = onNodeWithTag("face-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        onNodeWithTag("info").performSemanticsAction(SemanticsActions.RequestFocus)
        waitForIdle()
        assertTrue(onNodeWithTag("info-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width > infoWidth * 1.1f,
            "TV icon buttons should visibly grow when focused")
        assertEquals(faceBounds, onNodeWithTag("face-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot,
            "Focus animation must not move neighboring controls")
        onRoot().performKeyInput { pressKey(Key.Enter) }
        assertEquals(1, actions)
        runOnUiThread { focusManager.moveFocus(FocusDirection.Right) }
        onNodeWithTag("disabled").assertIsNotEnabled()
        onNodeWithTag("face").assertIsFocused().assertIsOff()
        assertEquals(0, toggles, "Moving focus must not change the privacy setting")
        waitForIdle()
        assertTrue(onNodeWithTag("face-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width > faceBounds.width * 1.1f)
        onRoot().performKeyInput { pressKey(Key.Enter) }
        onNodeWithTag("face").assertIsOn().assertIsFocused()
        assertEquals(1, toggles)
        Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).use { preview ->
            checkNotNull(preview.encodeToData(EncodedImageFormat.PNG)).use { png ->
                File("build/verification/tv-icon-buttons-focused.png").apply {
                    parentFile.mkdirs()
                    writeBytes(png.bytes)
                }
            }
        }
        runOnUiThread { focusManager.moveFocus(FocusDirection.Left) }
        onNodeWithTag("info").assertIsFocused()
        waitForIdle()
        assertEquals(faceBounds, onNodeWithTag("face-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
        onNodeWithTag("face").assertIsOn()
    }

    @Test
    fun disabledButtonsDoNotActivate() = runDesktopComposeUiTest {
        var actions = 0
        setContent {
            MaterialTheme {
                Row {
                    TvIconButton({ actions++ }, Modifier.testTag("action"), enabled = false, useTvStyle = true) {
                        Icon(Icons.Filled.Info, "Info")
                    }
                    TvIconToggleButton(true, { actions++ }, Modifier.testTag("toggle"), enabled = false, useTvStyle = true) {
                        Icon(Icons.Filled.Info, "Toggle")
                    }
                }
            }
        }
        onNodeWithTag("action").assertIsNotEnabled().performMouseInput { click() }
        onNodeWithTag("toggle").assertIsNotEnabled().assertIsOn().performMouseInput { click() }
        assertEquals(0, actions)
    }

    @Test
    fun nonTvButtonsKeepTheirSizeOnFocus() = runDesktopComposeUiTest {
        setContent {
            MaterialTheme {
                TvIconButton({}, Modifier.testTag("action"), useTvStyle = false) {
                    Icon(Icons.Filled.Info, "Info", Modifier.size(24.dp).testTag("icon"))
                }
            }
        }
        val bounds = onNodeWithTag("icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        onNodeWithTag("action").performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithTag("action").assertIsFocused()
        waitForIdle()
        assertEquals(bounds, onNodeWithTag("icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
    }
}
