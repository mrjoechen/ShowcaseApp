package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CLOSE_BUTTON_HIDE_DELAY_MILLIS = 3_000L

internal class CloseButtonVisibilityState(private val scope: CoroutineScope) {
    var isVisible by mutableStateOf(false)
        private set

    private var hideJob: Job? = null

    fun onInteraction() {
        hideJob?.cancel()
        isVisible = true
        // Restart directly: a false -> true change within one frame can be invisible
        // to LaunchedEffect(isVisible), leaving a visible button with no hide timer.
        hideJob = scope.launch {
            delay(CLOSE_BUTTON_HIDE_DELAY_MILLIS)
            isVisible = false
        }
    }
}

@Composable
internal fun rememberCloseButtonVisibilityState(): CloseButtonVisibilityState {
    val scope = rememberCoroutineScope()
    return remember(scope) { CloseButtonVisibilityState(scope) }
}
