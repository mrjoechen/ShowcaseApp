package com.alpha.showcase.common.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SentryLifecycleController(
    private val initializeSdk: () -> Unit,
    private val closeSdk: () -> Unit,
    private val runOnRequiredThread: suspend (() -> Unit) -> Unit,
) {
    private val lifecycleMutex = Mutex()
    private var isRunning = false

    suspend fun setEnabled(enabled: Boolean) {
        lifecycleMutex.withLock {
            if (enabled == isRunning) return

            runOnRequiredThread {
                if (enabled) initializeSdk() else closeSdk()
            }
            isRunning = enabled
        }
    }
}
