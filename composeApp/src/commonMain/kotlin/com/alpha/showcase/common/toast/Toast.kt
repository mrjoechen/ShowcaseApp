package com.alpha.showcase.common.toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun Toast(toastMessage: ToastMessage, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor = when (toastMessage.type) {
        ToastType.SUCCESS -> Color(0xFF2E7D32)
        ToastType.FAILED -> Color(0xFFE28A00)
        ToastType.ERROR -> colorScheme.error
        ToastType.INFO -> colorScheme.primary
    }

    Card(
        modifier = modifier
            .widthIn(min = 180.dp, max = 420.dp)
            .sizeIn(minHeight = 52.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = toastMessage.message,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val state by ToastManager.currentToastFlow.collectAsState()
    val visibilityState = remember { MutableTransitionState(false) }
    var shownToast by remember { mutableStateOf<ToastMessage?>(null) }

    LaunchedEffect(state) {
        if (state != null) {
            shownToast = state
            visibilityState.targetState = true
        } else {
            visibilityState.targetState = false
        }
    }
    LaunchedEffect(visibilityState.isIdle, visibilityState.currentState, visibilityState.targetState) {
        if (visibilityState.isIdle && !visibilityState.currentState && !visibilityState.targetState) {
            shownToast = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                initialOffsetY = { height -> height / 3 }
            ) + fadeIn(
                animationSpec = tween(durationMillis = 180)
            ),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                targetOffsetY = { height -> height / 4 }
            ) + fadeOut(
                animationSpec = tween(durationMillis = 120)
            )
        ) {
            shownToast?.let { Toast(it) }
        }
    }
}
