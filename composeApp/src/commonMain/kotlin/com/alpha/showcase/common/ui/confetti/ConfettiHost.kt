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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration.Companion.seconds

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
internal expect fun ConfettiEventHost(events: Flow<ConfettiType>, modifier: Modifier)
