package com.alpha.showcase.common.components

import com.alpha.showcase.common.utils.Log

internal interface DesktopSleepInhibitor {
    fun enable()
    fun disable()
}

internal class DesktopKeepAwakeController(
    private val inhibitor: DesktopSleepInhibitor,
    private val reportFailure: (operation: String, error: Throwable) -> Unit = { operation, error ->
        Log.w("DesktopKeepAwake", "$operation failed: ${error.message ?: error::class.simpleName}")
    },
) {
    var isEnabled: Boolean = false
        private set

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        if (enabled == isEnabled) return

        if (!enabled) {
            if (disableWithRetries("disable")) {
                isEnabled = false
            }
            return
        }

        try {
            inhibitor.enable()
            isEnabled = true
        } catch (error: Throwable) {
            error.throwIfFatal()
            reportFailure("enable", error)
            isEnabled = !disableWithRetries("enable cleanup")
        }
    }

    private fun disableWithRetries(operation: String): Boolean {
        repeat(DESKTOP_KEEP_AWAKE_RELEASE_ATTEMPTS) { attempt ->
            try {
                inhibitor.disable()
                return true
            } catch (error: Throwable) {
                error.throwIfFatal()
                reportFailure("$operation (attempt ${attempt + 1})", error)
            }
        }
        return false
    }
}

internal fun Throwable.throwIfFatal() {
    if (this is VirtualMachineError || this is ThreadDeath) throw this
}

private const val DESKTOP_KEEP_AWAKE_RELEASE_ATTEMPTS = 3
