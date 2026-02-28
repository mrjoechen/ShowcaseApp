package com.alpha.showcase.common.ui.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import isMobile

@Composable
fun rememberMobileHaptic(
    type: HapticFeedbackType = HapticFeedbackType.LongPress
): () -> Unit {
    val hapticFeedback = LocalHapticFeedback.current
    return remember(hapticFeedback, type) {
        {
            if (isMobile()) {
                hapticFeedback.performHapticFeedback(type)
            }
        }
    }
}
