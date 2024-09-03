package com.alpha.showcase.common.ui.view

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

@Composable
fun BackKeyHandler(
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val focusRequester = remember(onBack){ FocusRequester() }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown &&
                    (it.key in listOf(Key.Backspace, Key.Escape, Key.Back))) {
                    onBack()
                    true
                } else {
                    false
                }
            }
    ) {

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        content()
    }
}