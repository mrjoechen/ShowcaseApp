package com.alpha.showcase.common.ui.ai

import androidx.compose.runtime.*
import com.alpha.showcase.common.components.IOSScreenFeature
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.setStatusBarStyle

@Composable
internal actual fun AiPreviewSystemBars(chromeVisible: Boolean) {
    val application = UIApplication.sharedApplication
    DisposableEffect(application) {
        val previousStyle = application.statusBarStyle
        onDispose {
            application.setStatusBarStyle(previousStyle)
            IOSScreenFeature.exitFullScreen()
        }
    }
    SideEffect {
        application.setStatusBarStyle(UIStatusBarStyleLightContent)
        if (chromeVisible) IOSScreenFeature.exitFullScreen() else IOSScreenFeature.fullScreen()
    }
}
