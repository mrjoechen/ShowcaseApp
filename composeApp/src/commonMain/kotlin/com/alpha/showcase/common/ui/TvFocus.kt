package com.alpha.showcase.common.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import usesRemoteNavigation

/** Give a resumed TV destination a usable child focus, restoring the previous child when available. */
@Composable
internal fun Modifier.tvScreenFocus(enabled: Boolean = usesRemoteNavigation()): Modifier {
    if (!enabled) return this
    val focus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycle) {
        if (lifecycle == Lifecycle.State.RESUMED) {
            // tvOS scenes start in Touch mode, where UIKit's SystemDefined buttons reject focus.
            inputMode.requestInputMode(InputMode.Keyboard)
            // A focus group can enter its children only after they have been placed.
            withFrameNanos { }
            focus.requestFocus()
        }
    }
    return focusRequester(focus).focusRestorer().focusGroup()
}

/** Read-only fields are selectors on TV; their text cursor must not consume remote arrows. */
@Composable
internal fun Modifier.tvReadOnlyFieldNavigation(enabled: Boolean = usesRemoteNavigation()): Modifier {
    if (!enabled) return this
    val focusManager = LocalFocusManager.current
    val inputMode = LocalInputModeManager.current
    return onPreviewKeyEvent { event ->
        val direction = when (event.key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            Key.DirectionLeft -> FocusDirection.Left
            Key.DirectionRight -> FocusDirection.Right
            else -> return@onPreviewKeyEvent false
        }
        if (event.type == KeyEventType.KeyDown) {
            inputMode.requestInputMode(InputMode.Keyboard)
            focusManager.moveFocus(direction)
        }
        true
    }
}
