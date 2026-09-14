package com.alpha.showcase.common.ui.play

import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

/** One policy for every timed image replacement. Can be overridden for a whole playback tree. */
data class ImagePlaybackConfig(val loadTimeoutMillis: Long = 30_000L) {
    init { require(loadTimeoutMillis > 0) }
}

val LocalImagePlaybackConfig = staticCompositionLocalOf { ImagePlaybackConfig() }

internal data class ImagePlaybackFrame(
    val key: Any,
    val ready: Boolean,
    val paused: Boolean = false,
    val enabled: Boolean = true,
)

/** Counts loading and successful display separately; all timestamps must be monotonic. */
internal class ImagePlaybackTimer(
    displayDurationMillis: Long,
    private val config: ImagePlaybackConfig,
) {
    private val duration = displayDurationMillis.takeIf { it > 0 } ?: DEFAULT_PERIOD
    private var previous: ImagePlaybackFrame? = null
    private var previousTime = 0L
    private var loadingMillis = 0L
    private var displayMillis = 0L
    private var finished = false
    val progress: Float get() = (displayMillis.toFloat() / duration).coerceIn(0f, 1f)

    fun update(frame: ImagePlaybackFrame, nowMillis: Long): Boolean {
        val old = previous
        val changed = old?.key != frame.key
        val elapsed = if (old == null || changed || old.paused || frame.paused || !old.enabled) 0L
            else (nowMillis - previousTime).coerceAtLeast(0)
        previous = frame
        previousTime = nowMillis
        if (changed || !frame.enabled) {
            loadingMillis = 0
            displayMillis = 0
            finished = false
        }
        if (!frame.ready && old?.ready == true) {
            displayMillis = 0
            loadingMillis = 0
        }
        if (!frame.enabled || frame.paused || finished) return false
        if (frame.ready) {
            // The first success observation starts a fresh, full display interval.
            if (old?.ready == true && !changed) displayMillis += elapsed
            loadingMillis = 0
        } else {
            displayMillis = 0
            if (old?.ready == true) loadingMillis = 0 else loadingMillis += elapsed
        }
        finished = if (frame.ready) displayMillis >= duration else loadingMillis >= config.loadTimeoutMillis
        return finished
    }
}

/** Timers follow actual render state; toggling a progress indicator never changes playback. */
@Composable
internal fun rememberImagePlaybackProgress(
    displayDurationMillis: Long,
    owner: Any = Unit,
    current: () -> ImagePlaybackFrame,
    onAdvance: suspend () -> Unit,
): State<Float> {
    val config = LocalImagePlaybackConfig.current
    val latestCurrent by rememberUpdatedState(current)
    val latestAdvance by rememberUpdatedState(onAdvance)
    val progress = remember { mutableFloatStateOf(0f) }
    var progressKey by remember { mutableStateOf<Any?>(null) }
    PlaybackEffect(owner, displayDurationMillis, config) {
        var timer = ImagePlaybackTimer(displayDurationMillis, config)
        progressKey = null
        progress.floatValue = 0f
        while (isActive) {
            val now = withFrameNanos { it / 1_000_000L }
            val frame = latestCurrent()
            val advance = timer.update(frame, now)
            progressKey = frame.key
            progress.floatValue = timer.progress
            if (advance) {
                try { latestAdvance() }
                catch (cancelled: CancellationException) {
                    // A gesture can cancel the pager's animation without cancelling this
                    // playback effect. Start a fresh interval for the page it settles on.
                    currentCoroutineContext().ensureActive()
                    timer = ImagePlaybackTimer(displayDurationMillis, config)
                    progress.floatValue = 0f
                }
            }
            delay(50)
        }
    }
    // A manual switch can happen between timer ticks. Never expose the previous
    // image's progress while the next frame is waiting for its first timer update.
    return remember {
        derivedStateOf { if (progressKey == latestCurrent().key) progress.floatValue else 0f }
    }
}

@Composable
internal fun rememberPagerImagePlaybackProgress(
    pagerState: PagerState,
    displayDurationMillis: Long,
    itemCount: Int,
    animationMillis: Int,
    stateForPage: (Int) -> MediaItemState,
): State<Float> = rememberImagePlaybackProgress(displayDurationMillis, owner = pagerState, current = {
    if (itemCount <= 1) ImagePlaybackFrame(Unit, ready = false, enabled = false)
    else {
        val page = pagerState.currentPage
        val media = stateForPage(page)
        ImagePlaybackFrame(page to media, media.ready, paused = pagerState.isScrollInProgress,
            enabled = !media.data.isVideo())
    }
}) {
    val next = if (pagerState.canScrollForward) pagerState.currentPage + 1 else 0
    pagerState.animateScrollToPage(next, animationSpec = tween(animationMillis))
}
