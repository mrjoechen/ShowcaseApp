package com.alpha.showcase.common.ui.confetti

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.vinceglb.confettikit.compose.ConfettiKit
import kotlinx.coroutines.flow.Flow

@Composable
internal actual fun ConfettiEventHost(events: Flow<ConfettiType>, modifier: Modifier) {
    var launch by remember { mutableStateOf<TvosConfettiLaunch?>(null) }

    LaunchedEffect(events) {
        events.collect { type -> launch = TvosConfettiLaunch(type) }
    }

    val currentLaunch = launch ?: return
    // Replace the previous system even for repeated events of the same type.
    // ConfettiKit only draws a Canvas, so remote input stays with the content.
    key(currentLaunch) {
        val parties = remember { confettiParties(currentLaunch.type) }
        ConfettiKit(
            modifier = modifier,
            parties = parties,
            onParticleSystemEnded = { _, activeSystems ->
                if (activeSystems == 0 && launch === currentLaunch) launch = null
            },
        )
    }
}

private class TvosConfettiLaunch(val type: ConfettiType)
