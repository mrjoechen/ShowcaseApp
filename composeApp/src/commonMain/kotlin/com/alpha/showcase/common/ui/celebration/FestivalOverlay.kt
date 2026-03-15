package com.alpha.showcase.common.ui.celebration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.alpha.showcase.common.ui.view.LottieAssetLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val CHECK_INTERVAL_MS = 10_000L
private const val INITIAL_DELAY_MS = 10_000L
private const val FADE_DURATION_MS = 400
private const val DEFAULT_PARTIAL_SIZE = 0.35f

@Composable
fun FestivalOverlay(
    modifier: Modifier = Modifier,
) {
    val active by FestivalEasterEggManager.currentAnimation.collectAsState()
    var lastActive by remember { mutableStateOf<ActiveFestivalAnimation?>(null) }
    if (active != null) {
        lastActive = active
    }

    LaunchedEffect(Unit) {
        delay(INITIAL_DELAY_MS)
        while (isActive) {
            if (FestivalEasterEggManager.tryTrigger()) {
                delay(FestivalEasterEggManager.getRandomIntervalMs())
            } else {
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { FestivalEasterEggManager.resetSession() }
    }

    AnimatedVisibility(
        visible = active != null,
        enter = fadeIn(animationSpec = tween(durationMillis = FADE_DURATION_MS)),
        exit = fadeOut(animationSpec = tween(durationMillis = FADE_DURATION_MS)),
        modifier = modifier,
    ) {
        (active ?: lastActive)?.let { current ->
            FestivalLottie(
                activeAnimation = current,
                onFinished = FestivalEasterEggManager::onAnimationFinished,
            )
        }
    }
}

@Composable
private fun FestivalLottie(
    activeAnimation: ActiveFestivalAnimation,
    onFinished: () -> Unit,
) {
    val animation = activeAnimation.animation
    val isFullScreen = activeAnimation.position == AnimationPosition.FULL_SCREEN
    val sizeFraction = animation.sizeFraction
    val contentScale = if (isFullScreen) ContentScale.Crop else ContentScale.Fit
    val alignment = activeAnimation.position.toAlignment()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = alignment,
    ) {
        LottieAssetLoader(
            lottieAsset = animation.lottieAsset,
            iterations = animation.iterations,
            contentScale = contentScale,
            onFinished = onFinished,
            modifier = when {
                sizeFraction != null -> Modifier.fillMaxSize(sizeFraction)
                isFullScreen -> Modifier.fillMaxSize()
                else -> Modifier.fillMaxSize(DEFAULT_PARTIAL_SIZE)
            },
        )
    }
}

private fun AnimationPosition.toAlignment(): Alignment = when (this) {
    AnimationPosition.TOP_START -> Alignment.TopStart
    AnimationPosition.TOP_CENTER -> Alignment.TopCenter
    AnimationPosition.TOP_END -> Alignment.TopEnd
    AnimationPosition.CENTER_START -> Alignment.CenterStart
    AnimationPosition.CENTER -> Alignment.Center
    AnimationPosition.CENTER_END -> Alignment.CenterEnd
    AnimationPosition.BOTTOM_START -> Alignment.BottomStart
    AnimationPosition.BOTTOM_CENTER -> Alignment.BottomCenter
    AnimationPosition.BOTTOM_END -> Alignment.BottomEnd
    AnimationPosition.FULL_SCREEN -> Alignment.Center
}
