package com.alpha.showcase.common.ui.confetti

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import io.github.vinceglb.confettikit.compose.ConfettiKit
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest

@Composable
internal actual fun ConfettiEventHost(
    events: Flow<ConfettiType>,
    modifier: Modifier,
) {
    var launchType by remember { mutableStateOf<ConfettiType?>(null) }
    var launchToken by remember { mutableLongStateOf(0L) }

    LaunchedEffect(events) {
        events.collectLatest { type ->
            launchType = type
            launchToken += 1
        }
    }

    val type = launchType ?: return
    val parties = remember(type) { confettiParties(type) }

    ConfettiKit(
        modifier = modifier,
        parties = parties,
        onParticleSystemEnded = { _, activeSystems ->
            if (activeSystems == 0 && launchToken > 0) {
                launchType = null
            }
        },
    )
}
