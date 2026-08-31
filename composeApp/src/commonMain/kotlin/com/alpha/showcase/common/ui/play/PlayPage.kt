package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.components.ScreenControlEffect
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.ui.celebration.FestivalOverlay
import com.alpha.showcase.common.ui.confetti.ConfettiType
import com.alpha.showcase.common.ui.confetti.LocalConfettiTrigger
import com.alpha.showcase.common.ui.confetti.ScopedConfettiHost
import com.alpha.showcase.common.ui.play.flip.FlipPager
import com.alpha.showcase.common.ui.play.flip.FlipPagerOrientation
import com.alpha.showcase.common.ui.settings.Settings
import com.alpha.showcase.common.ui.settings.DisplayMode
import com.alpha.showcase.common.ui.settings.FrameWallMode
import com.alpha.showcase.common.ui.settings.Orientation
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_BENTO
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_CALENDER
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FADE
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FRAME_WALL
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SLIDE
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SQUARE
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_WATERFALL
import com.alpha.showcase.common.ui.settings.SlideEffect
import com.alpha.showcase.common.ui.settings.SettingsViewModel
import com.alpha.showcase.common.ui.settings.getInterval
import com.alpha.showcase.common.ui.view.BackKeyHandler
import com.alpha.showcase.common.ui.view.DataNotFoundAnim
import com.alpha.showcase.common.ui.view.CircleLoadingIndicator
import com.alpha.showcase.common.ui.vm.UiState
import com.alpha.showcase.common.ui.vm.succeeded
import com.alpha.showcase.common.utils.ToastUtil
import getScreenFeature
import isDesktop
import isWeb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import com.alpha.showcase.common.ui.view.ContainedLoadingIndicator
import kotlin.coroutines.cancellation.CancellationException
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.close
import showcaseapp.composeapp.generated.resources.the_number_of_files_may_be_too_large_please_wait


const val LOADING_WARNING_TIME = 5000L
const val DEFAULT_PERIOD = 5000L

private class PagingSessionLoadFailure(
    val originalFailure: Exception,
) : Throwable()

/**
 * Owns the initial load and every paging child launched into [load]'s scope for
 * exactly one source/settings session. The successful load result is published,
 * its one-shot warning is cancelled, and the scope then remains alive until the
 * keyed Compose effect is cancelled.
 */
internal suspend fun <T> runPagingSession(
    warningDelayMillis: Long,
    shouldWarn: Boolean,
    load: suspend (CoroutineScope) -> T,
    onLoaded: (T) -> Unit,
    onWarning: suspend () -> Unit,
): Nothing {
    try {
        supervisorScope {
            require(warningDelayMillis >= 0L) { "warningDelayMillis must not be negative" }
            val sessionScope = this
            val loadJob = async {
                try {
                    Result.success(load(sessionScope))
                } catch (e: CancellationException) {
                    throw e
                } catch (failure: Exception) {
                    Result.failure(failure)
                }
            }
            val warningJob = launch {
                delay(warningDelayMillis)
                if (shouldWarn && loadJob.isActive) onWarning()
            }

            val loadResult = loadJob.await()
            val loadFailure = loadResult.exceptionOrNull()
            if (loadFailure != null) {
                throw PagingSessionLoadFailure(loadFailure as Exception)
            }
            onLoaded(loadResult.getOrThrow())
            warningJob.cancelAndJoin()
            awaitCancellation()
        }
    } catch (failure: PagingSessionLoadFailure) {
        throw failure.originalFailure
    }
}

@Composable
fun PlayPage(remoteApi: RemoteApi, onBack: () -> Unit = {}) {

    var showCloseButton by remember { mutableStateOf(false) }

    var loadComplete by remember { mutableStateOf(false) }

    val settingsState by SettingsViewModel.settingsFlow.collectAsState()

    LaunchedEffect(showCloseButton) {
        if (showCloseButton) {
            delay(5000)
            showCloseButton = false // Hide the close button
        }
    }

    val screenFeature = remember(remoteApi) {
        getScreenFeature()
    }

    val autoFullscreen = playFullScreenEnabled(settingsState)

    ScreenControlEffect(
        screenFeature = screenFeature,
        keepScreenOn = shouldKeepScreenOnDuringPlayback(
            isDesktop = isDesktop(),
            autoFullscreen = autoFullscreen,
            isWeb = isWeb(),
        ),
        fullScreen = autoFullscreen,
    )

    BackKeyHandler(
        onBack = onBack
    ) {
        Surface(Modifier.pointerInput(Unit) {
            // Listen for pointer (mouse) movements
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.isNotEmpty()) {
                        // Show the close button when the mouse moves
                        showCloseButton = true
                    }
                }
            }
        }) {
            var pagingState: UiState<PagingPlayItems> by remember(remoteApi) {
                mutableStateOf(UiState.Loading)
            }

            LaunchedEffect(remoteApi, settingsState) {
                pagingState = UiState.Loading
                val settings = (settingsState as? UiState.Content)?.data ?: return@LaunchedEffect

                runPagingSession(
                    warningDelayMillis = LOADING_WARNING_TIME,
                    shouldWarn = remoteApi is RcloneRemoteApi,
                    load = { sessionScope ->
                        PlayViewModel.getPagedImageFileInfo(
                            remoteApi,
                            settings.recursiveDirContent,
                            settings.supportVideo && supportsVideoForShowcaseMode(settings.showcaseMode),
                            settings.sortRule,
                            sessionScope,
                        )
                    },
                    onLoaded = { pagingState = it },
                    onWarning = {
                        ToastUtil.toast(
                            getString(Res.string.the_number_of_files_may_be_too_large_please_wait)
                        )
                    },
                )
            }

            pagingState.let {
                when (it) {
                    is UiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            DataNotFoundAnim(it.msg ?: "")
                        }
                    }

                    UiState.Loading -> ContainedLoadingIndicator()
                    is UiState.Content -> {
                        if (pagingState.succeeded && settingsState.succeeded) {
                            val settings = (settingsState as UiState.Content).data
                            if (it.data.size > 0) {
                                MainPlayContentPage(it.data, settings)
                                loadComplete = true
                            } else {
                                DataNotFoundAnim()
                            }
                        }
                    }
                }
            }
        }

        val density = LocalDensity.current
        val displayCutoutTop = (WindowInsets.displayCutout.getTop(density) / density.density).dp
        AnimatedVisibility(showCloseButton,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.padding(top = displayCutoutTop).align(Alignment.TopCenter)){
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(30.dp).focusable().background(Color.Gray.copy(0.5f), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.close),
                    tint = Color.Black.copy(0.5f)
                )
            }
        }
    }

}

internal fun playFullScreenEnabled(settingsState: UiState<Settings>): Boolean =
    (settingsState as? UiState.Content)?.data?.autoFullScreen == true


@Composable
fun MainPlayContentPage(
    pagingItems: PagingPlayItems,
    settings: Settings,
    parentActive: Boolean = true,
    editMode: Boolean = false
) {

    Surface {
        if (pagingItems.size > 0) {
            // A settings/source reload replaces the PagingPlayItems object, while
            // an ordinary background refresh mutates the same object in place.
            // Reset child pager/animation state only for the former: otherwise
            // LaunchedEffect(Unit) and un-keyed remember blocks in a showcase mode
            // can keep closures over the previous source and display stale media.
            // In-place refreshes retain their controller state and stable anchor.
            key(pagingItems) {
                Box(modifier = Modifier.fillMaxSize()) {
                when (settings.showcaseMode) {
                    SHOWCASE_MODE_SLIDE -> {
                        val switchDuration = getInterval(
                            settings.slideMode.intervalTimeUnit,
                            settings.slideMode.intervalTime
                        )

                        when (settings.slideMode.effect) {
                            SlideEffect.Default.value -> {
                                SlideImagePager(
                                    pagingItems = pagingItems,
                                    fitSize = settings.slideMode.displayMode == DisplayMode.CenterCrop.value,
                                    vertical = settings.slideMode.orientation == Orientation.Vertical.value,
                                    switchDuration = switchDuration,
                                    showProgress = settings.slideMode.showTimeProgressIndicator
                                )
                            }
                            SlideEffect.Cube.value -> {
                                CubePager(
                                    switchDuration,
                                    pagingItems,
                                    fitSize = settings.slideMode.displayMode == DisplayMode.CenterCrop.value,
                                    showProgress = settings.slideMode.showTimeProgressIndicator
                                )
                            }
                            SlideEffect.Reveal.value -> {
                                CircleRevealPager(
                                    switchDuration,
                                    pagingItems,
                                    fitSize = settings.slideMode.displayMode == DisplayMode.CenterCrop.value,
                                    showProgress = settings.slideMode.showTimeProgressIndicator
                                )
                            }

//                        SlideEffect.Carousel.value -> {
//                            CarouselPager(
//                                switchDuration,
//                                pagingItems,
//                                fitSize = settings.slideMode.displayMode == DisplayMode.CenterCrop.value,
//                            )
//                        }

                            SlideEffect.Flip.value -> {
                                FlipPager(
                                    switchDuration,
                                    pagingItems,
                                    fitSize = settings.slideMode.displayMode == DisplayMode.CenterCrop.value,
                                    settings.slideMode.orientation == FlipPagerOrientation.Vertical.value,
                                    showProgress = settings.slideMode.showTimeProgressIndicator
                                )
                            }
                        }

                    }

                    SHOWCASE_MODE_FRAME_WALL -> {

                        settings.frameWallMode.let {

                            if (it.frameStyle == FrameWallMode.FixSize.value) {
                                FrameWallLayout(
                                    if (settings.frameWallMode.matrixSizeRow == 0) 2 else settings.frameWallMode.matrixSizeRow,
                                    if (settings.frameWallMode.matrixSizeColumn == 0) 2 else settings.frameWallMode.matrixSizeColumn,
                                    pagingItems = pagingItems,
                                    duration = it.interval * 1000L,
                                    fitSize = settings.frameWallMode.displayMode == DisplayMode.CenterCrop.value,
                                )
                            }
                        }
                    }

                    SHOWCASE_MODE_FADE -> {

                        FadeLayout(
                            pagingItems = pagingItems,
                            fitSize = settings.fadeMode.displayMode == DisplayMode.CenterCrop.value,
                            switchDuration = getInterval(settings.fadeMode.intervalTimeUnit, settings.fadeMode.intervalTime),
                            showProgress = settings.fadeMode.showTimeProgressIndicator
                        )
                    }

                    SHOWCASE_MODE_CALENDER -> {
                        CalenderPlay(
                            settings.calenderMode.autoPlay,
                            getInterval(settings.calenderMode.intervalTimeUnit, settings.calenderMode.intervalTime),
                            settings.sortRule,
                            pagingItems
                        )
                    }

                    SHOWCASE_MODE_BENTO -> {
                        BentoPlay(
                            settings.bentoMode.bentoStyle,
                            settings.bentoMode.interval * 1000L,
                            pagingItems
                        )
                    }

                    SHOWCASE_MODE_SQUARE -> {
                        SquareScreen(
                            pagingItems = pagingItems,
                            squareMode = settings.squareMode,
                            parentActive = parentActive,
                            editMode = editMode
                        )
                    }

                    SHOWCASE_MODE_WATERFALL -> {
                        WaterfallScreen(
                            pagingItems = pagingItems,
                            waterfallMode = settings.waterfallMode,
                            parentActive = parentActive,
                            editMode = editMode
                        )
                    }

                    else -> {

                        SlideImagePager(
                            pagingItems = pagingItems,
                            fitSize = settings.slideMode.displayMode == DisplayMode.CenterCrop.value,
                            vertical = settings.slideMode.orientation == Orientation.Vertical.value,
                            switchDuration = getInterval(settings.slideMode.intervalTimeUnit, settings.slideMode.intervalTime),
                            showProgress = settings.slideMode.showTimeProgressIndicator
                        )
                    }
                }

                FestivalOverlay(
                    modifier = Modifier.fillMaxSize()
                )

                WeatherBackgroundLayer(
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.18f
                )

                if (
                    settings.showTimeAndDate &&
                    settings.showcaseMode != SHOWCASE_MODE_CALENDER &&
                    settings.showcaseMode != SHOWCASE_MODE_WATERFALL
                ) {
                    TimeCard()
                }
                }
            }
        }
    }
}
