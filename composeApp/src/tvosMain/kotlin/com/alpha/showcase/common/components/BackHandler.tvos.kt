package com.alpha.showcase.common.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Boolean) {
    val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
    val fallback = remember(dispatcher) { DirectNavigationEventInput() }
    val handler = remember(dispatcher) { TvosBackCallback(enabled, onBack, fallback::backCompleted) }
    SideEffect {
        handler.isBackEnabled = enabled
        handler.onBack = onBack
    }
    DisposableEffect(dispatcher, handler) {
        dispatcher.addInput(fallback)
        dispatcher.addHandler(handler)
        onDispose {
            handler.remove()
            dispatcher.removeInput(fallback)
        }
    }
}
