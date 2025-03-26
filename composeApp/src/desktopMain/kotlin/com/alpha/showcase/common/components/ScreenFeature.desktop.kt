package com.alpha.showcase.common.components

import isWindows

actual interface ScreenFeature {
    actual fun keepScreenOn(on: Boolean)
    actual fun fullScreen()
    actual fun exitFullScreen()
}


object DesktopScreenFeature : ScreenFeature {
    private var isScreenOnRequested = false

    override fun keepScreenOn(on: Boolean) {
        isScreenOnRequested = on
        if (on) {
            // 使用 Windows API 保持屏幕常亮
            // 注意：这需要通过 JNI 或其他方式调用原生 Windows API
            // 以下是伪代码，实际实现需要使用适当的跨平台桥接方法
            if (isWindows()){
//                val ES_CONTINUOUS = 0x80000000.toUInt()
//                val ES_SYSTEM_REQUIRED = 0x00000001.toUInt()
//                WindowsNative.SetThreadExecutionState(ES_CONTINUOUS or ES_SYSTEM_REQUIRED or ES_DISPLAY_REQUIRED)
            }
        } else {
            // 恢复正常电源管理
            // WindowsNative.SetThreadExecutionState(ES_CONTINUOUS)
        }
    }

    override fun fullScreen() {
    }

    override fun exitFullScreen() {
    }

}