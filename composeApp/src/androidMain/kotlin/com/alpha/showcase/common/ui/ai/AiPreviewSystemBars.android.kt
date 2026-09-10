package com.alpha.showcase.common.ui.ai

import androidx.compose.runtime.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import currentActivity

@Composable
internal actual fun AiPreviewSystemBars(chromeVisible: Boolean) {
    val activity = currentActivity ?: return
    val window = activity.window
    val controller = remember(window) { WindowInsetsControllerCompat(window, window.decorView) }
    DisposableEffect(controller) {
        val statusLight = controller.isAppearanceLightStatusBars
        val navigationLight = controller.isAppearanceLightNavigationBars
        val behavior = controller.systemBarsBehavior
        val insets = ViewCompat.getRootWindowInsets(window.decorView)
        val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        onDispose {
            controller.isAppearanceLightStatusBars = statusLight
            controller.isAppearanceLightNavigationBars = navigationLight
            controller.systemBarsBehavior = behavior
            if (statusVisible) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
            if (navigationVisible) controller.show(WindowInsetsCompat.Type.navigationBars()) else controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
    SideEffect {
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        if (chromeVisible) controller.show(WindowInsetsCompat.Type.systemBars()) else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
