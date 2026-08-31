package com.alpha.showcase.common.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

actual interface ScreenFeature {
    actual fun keepScreenOn(on: Boolean)
    actual fun fullScreen()
    actual fun exitFullScreen()
}

internal class DesktopScreenFeatureDelegate(
    private val keepAwakeController: DesktopKeepAwakeController,
    val fullScreenFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) : ScreenFeature {
    override fun keepScreenOn(on: Boolean) = keepAwakeController.setEnabled(on)

    override fun fullScreen() {
        scope.launch {
            fullScreenFlow.emit(true)
        }
    }

    override fun exitFullScreen() {
        scope.launch {
            fullScreenFlow.emit(false)
        }
    }
}

object DesktopScreenFeature : ScreenFeature {
    private val delegate = DesktopScreenFeatureDelegate(
        keepAwakeController = DesktopKeepAwakeController(createDesktopSleepInhibitor()),
    )

    val fullScreenFlow: MutableStateFlow<Boolean>
        get() = delegate.fullScreenFlow

    override fun keepScreenOn(on: Boolean) = delegate.keepScreenOn(on)
    override fun fullScreen() = delegate.fullScreen()
    override fun exitFullScreen() = delegate.exitFullScreen()
}
