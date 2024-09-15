package com.alpha.showcase.common.ui.ext

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

fun Modifier.handleBackKey(onBack: () -> Unit): Modifier {
    return onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && (it.key in listOf(Key.Backspace, Key.Escape, Key.Back))) {
            onBack()
            true
        } else {
            false
        }
    }
}