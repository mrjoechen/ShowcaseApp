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
import io.github.vinceglb.confettikit.core.Angle
import io.github.vinceglb.confettikit.core.Party
import io.github.vinceglb.confettikit.core.Position
import io.github.vinceglb.confettikit.core.Spread
import io.github.vinceglb.confettikit.core.emitter.Emitter
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration.Companion.seconds

enum class ConfettiType {
    Celebration,
    Success,
    Burst,
}

object ConfettiController {
    private val _events = MutableSharedFlow<ConfettiType>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events = _events.asSharedFlow()

    fun trigger(type: ConfettiType) {
        _events.tryEmit(type)
    }
}

val LocalConfettiTrigger = compositionLocalOf<(ConfettiType) -> Unit> {
    {}
}

@Composable
fun GlobalConfettiHost(modifier: Modifier = Modifier.fillMaxSize()) {
    ConfettiEventHost(events = ConfettiController.events, modifier = modifier)
}

@Composable
fun ScopedConfettiHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val localEvents = remember {
        MutableSharedFlow<ConfettiType>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    }
    val trigger: (ConfettiType) -> Unit = remember(localEvents) {
        { type -> localEvents.tryEmit(type) }
    }

    CompositionLocalProvider(LocalConfettiTrigger provides trigger) {
        Box(modifier = modifier) {
            content()
            ConfettiEventHost(events = localEvents, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ConfettiEventHost(
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
            if (activeSystems == 0L && launchToken > 0) {
                launchType = null
            }
        },
    )
}

private fun confettiParties(type: ConfettiType): List<Party> {
    return when (type) {
        ConfettiType.Celebration -> listOf(
            Party(
                speed = 0f,
                maxSpeed = 15f,
                damping = 0.9f,
                angle = Angle.BOTTOM,
                spread = Spread.ROUND,
                colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
                emitter = Emitter(duration = 5.seconds).perSecond(100),
                position = Position.Relative(0.0, 0.0).between(Position.Relative(1.0, 0.0)),
            ),
        )

        ConfettiType.Success -> listOf(
            Party(
                speed = 0f,
                maxSpeed = 10f,
                damping = 0.92f,
                angle = Angle.BOTTOM,
                spread = Spread.ROUND,
                colors = listOf(0x8bc34a, 0x4caf50, 0xcddc39, 0xa5d6a7),
                emitter = Emitter(duration = 2.5.seconds).perSecond(70),
                position = Position.Relative(0.15, 0.0).between(Position.Relative(0.85, 0.0)),
            ),
        )

        ConfettiType.Burst -> listOf(
            Party(
                speed = 0f,
                maxSpeed = 20f,
                damping = 0.88f,
                angle = Angle.BOTTOM,
                spread = Spread.SMALL,
                colors = listOf(0xffd54f, 0xff8a65, 0x4fc3f7, 0xce93d8),
                emitter = Emitter(duration = 1.2.seconds).perSecond(180),
                position = Position.Relative(0.5, 0.15),
            ),
        )
    }
}
