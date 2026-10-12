package com.alpha.showcase.common.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TvFocusTest {
    @Test
    fun fieldAndActionAllowBidirectionalRemoteFocusTraversal() = runDesktopComposeUiTest {
        lateinit var focusManager: FocusManager
        setContent {
            focusManager = LocalFocusManager.current
            MaterialTheme {
                TvTextFieldWithAction(enabled = true, action = {
                    IconButton({}, Modifier.testTag("action")) { Text("Load") }
                }) { modifier, trailingAction ->
                    OutlinedTextField("Model", {}, singleLine = true,
                        modifier = modifier.testTag("field"), trailingIcon = trailingAction)
                }
            }
        }
        onNodeWithTag("field").performSemanticsAction(SemanticsActions.RequestFocus)
        // The tvOS mediator calls moveFocus before editable fields consume direction keys.
        // Official desktop Compose only traverses by Tab, so exercise the same focus manager API.
        runOnUiThread { focusManager.moveFocus(FocusDirection.Right) }
        onNodeWithTag("action").assertIsFocused()
        runOnUiThread { focusManager.moveFocus(FocusDirection.Left) }
        onNodeWithTag("field").assertIsFocused()
    }

    @Test
    fun readOnlySelectorAllowsUpAndDownWithoutOpeningTheKeyboard() = runDesktopComposeUiTest {
        val inputMode = RemoteInputModeManager()
        setContent {
            CompositionLocalProvider(LocalInputModeManager provides inputMode) { MaterialTheme {
                Column {
                    // UIKit buttons use SystemDefined focusability, unlike desktop buttons.
                    Button({}, Modifier.testTag("above").remoteButtonFocus(inputMode)) { Text("Above") }
                    OutlinedTextField("Provider", {}, readOnly = true, singleLine = true,
                        modifier = Modifier.testTag("provider").tvReadOnlyFieldNavigation(enabled = true))
                    OutlinedTextField("URL", {}, singleLine = true, modifier = Modifier.testTag("url"))
                }
            } }
        }
        onNodeWithTag("provider").performSemanticsAction(SemanticsActions.RequestFocus)
        onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag("url").assertIsFocused()
        runOnUiThread { inputMode.requestInputMode(InputMode.Touch) }
        onNodeWithTag("provider").performSemanticsAction(SemanticsActions.RequestFocus)
        onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithTag("above").assertIsFocused()
    }

    @Test
    fun nonTvReadOnlyFieldKeepsItsExistingArrowBehavior() = runDesktopComposeUiTest {
        setContent {
            MaterialTheme {
                Column {
                    OutlinedTextField("Provider", {}, readOnly = true, singleLine = true,
                        modifier = Modifier.testTag("provider").tvReadOnlyFieldNavigation())
                    Button({}, Modifier.testTag("below")) { Text("Below") }
                }
            }
        }
        onNodeWithTag("provider").performSemanticsAction(SemanticsActions.RequestFocus)
        onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag("provider").assertIsFocused()
    }

    @Test
    fun destinationReceivesFocusAgainAfterPoppingBackWithoutAnArrowPress() = runDesktopComposeUiTest {
        lateinit var nav: NavHostController
        val inputMode = RemoteInputModeManager()
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalInputModeManager provides inputMode) { MaterialTheme {
                NavHost(nav, "settings", modifier = Modifier.fillMaxSize()) {
                    composable("settings") {
                        Column(Modifier.fillMaxSize().tvScreenFocus(enabled = true)) {
                            Button({}, Modifier.testTag("first").remoteButtonFocus(inputMode)) { Text("First") }
                            Button({ nav.navigate("ai") }, Modifier.testTag("ai-entry").remoteButtonFocus(inputMode)) { Text("AI services") }
                        }
                    }
                    composable("ai") {
                        Column(Modifier.fillMaxSize().tvScreenFocus(enabled = true)) {
                            Button({ nav.popBackStack() }, Modifier.testTag("back").remoteButtonFocus(inputMode)) { Text("Back") }
                        }
                    }
                }
            } }
        }
        onNodeWithTag("first").assertIsFocused()
        repeat(2) {
            onNodeWithTag("ai-entry").performSemanticsAction(SemanticsActions.RequestFocus)
            onNodeWithTag("ai-entry").performSemanticsAction(SemanticsActions.OnClick)
            waitForIdle()
            onNodeWithTag("back").assertIsFocused()
            onNodeWithTag("back").performSemanticsAction(SemanticsActions.OnClick)
            waitForIdle()
            onNode(isFocused()).assertExists()
        }
    }
}

private class RemoteInputModeManager : InputModeManager {
    override var inputMode by mutableStateOf(InputMode.Touch)
        private set
    override fun requestInputMode(inputMode: InputMode): Boolean {
        this.inputMode = inputMode
        return true
    }
}

private fun Modifier.remoteButtonFocus(inputMode: InputModeManager) = focusProperties {
    canFocus = inputMode.inputMode == InputMode.Keyboard
}
