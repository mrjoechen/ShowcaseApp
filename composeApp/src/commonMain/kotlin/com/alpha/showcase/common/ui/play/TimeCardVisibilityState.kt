package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TIME_CARD_RESTORE_DELAY_MILLIS = 5_000L

internal class TimeCardVisibilityState(private val scope: CoroutineScope) {
    var isVisible by mutableStateOf(true)
        private set

    private var restoreJob: Job? = null

    fun onTouch(isPressed: Boolean) {
        isVisible = false
        restoreJob?.cancel()
        restoreJob = if (isPressed) null else scope.launch {
            delay(TIME_CARD_RESTORE_DELAY_MILLIS)
            isVisible = true
        }
    }
}
