package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.components.ScreenControlEffect
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.toast.ToastHost
import com.alpha.showcase.common.ui.play.flip.FlipPager
import com.alpha.showcase.common.ui.play.flip.FlipPagerOrientation
import com.alpha.showcase.common.ui.settings.Settings
import com.alpha.showcase.common.ui.settings.DisplayMode
import com.alpha.showcase.common.ui.settings.FrameWallMode
import com.alpha.showcase.common.ui.settings.Orientation
import com.alpha.showcase.common.ui.settings.ProgressIndicator
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_BENTO
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_CALENDER
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_CAROUSEL
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_CUBE
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FADE
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FRAME_WALL
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_REVEAL
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SLIDE
import com.alpha.showcase.common.ui.settings.SettingPreferenceRepo
import com.alpha.showcase.common.ui.settings.SettingsViewModel.Companion.settingsFlow
import com.alpha.showcase.common.ui.settings.SlideEffect
import com.alpha.showcase.common.ui.settings.getInterval
import com.alpha.showcase.common.ui.view.BackKeyHandler
import com.alpha.showcase.common.ui.view.DataNotFoundAnim
import com.alpha.showcase.common.ui.vm.UiState
import com.alpha.showcase.common.ui.vm.succeeded
import com.alpha.showcase.common.utils.ToastUtil
import getScreenFeature
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.close
import showcaseapp.composeapp.generated.resources.the_number_of_files_may_be_too_large_please_wait


const val LOADING_WARNING_TIME = 5000L
const val DEFAULT_PERIOD = 5000L

@Composable
fun PlayPage(remoteApi: RemoteApi, onBack: () -> Unit = {}) {

    var showCloseButton by remember { mutableStateOf(false) }

    var loadComplete by remember { mutableStateOf(false) }

    LaunchedEffect(showCloseButton) {
        if (showCloseButton) {
            delay(5000)
            showCloseButton = false // Hide the close button
        }
    }

    val screenFeature = remember(remoteApi) {
        getScreenFeature()
    }

    ScreenControlEffect(
        screenFeature = screenFeature,
        keepScreenOn = true,
        fullScreen = true
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
            var settingsState: UiState<Settings> by remember(remoteApi) {
                mutableStateOf(UiState.Loading)
            }

            var imageFile: UiState<List<Any>> by remember(remoteApi) {
                mutableStateOf(UiState.Loading)
            }

            LaunchedEffect(remoteApi) {
                settingsState =
                    UiState.Content(SettingPreferenceRepo().getSettings())
            }

            LaunchedEffect(remoteApi) {

                val lsJob = launch {
                    val settings = (settingsFlow.value as UiState.Content).data
                    imageFile = PlayViewModel.getImageFileInfo(
                        remoteApi,
                        settings.recursiveDirContent,
                        settings.supportVideo && settings.showcaseMode != SHOWCASE_MODE_FRAME_WALL,
                        settings.sortRule
                    )
                }

                launch {
                    // 等待警告时间
                    delay(LOADING_WARNING_TIME)
                    // 如果任务仍在进行中，则给出警告
                    if (remoteApi is RcloneRemoteApi && lsJob.isActive) {
                        ToastUtil.toast(
                            getString(Res.string.the_number_of_files_may_be_too_large_please_wait)
                        )
                    }
                }

            }


            DisposableEffect(Unit) {
                onDispose {
                    PlayViewModel.onClear()
                }
            }

            imageFile.let {
                when (it) {
                    is UiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            DataNotFoundAnim(it.msg ?: "")
                        }
                    }

                    UiState.Loading -> ProgressIndicator()
                    is UiState.Content -> {
                        if (imageFile.succeeded && settingsState.succeeded) {
                            val settings = (settingsState as UiState.Content).data
                            if (it.data.isNotEmpty()) {
                                MainPlayContentPage(it.data.toMutableList(), settings)
                                loadComplete = true
                            } else {
                                DataNotFoundAnim()
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(showCloseButton,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)){
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(30.dp).focusable().background(Color.Gray.copy(0.5f), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.close),
                    tint = Color.Black.copy(0.6f)
                )
            }
        }
    }

}


@Composable
fun MainPlayContentPage(contents: List<Any>, settings: Settings) {

    Surface {
        if (contents.isNotEmpty()) {

            when (settings.showcaseMode) {

                SHOWCASE_MODE_SLIDE -> {
                    val switchDuration = getInterval(
                        settings.slideMode.intervalTimeUnit,
                        settings.slideMode.intervalTime
                    )

                    when (settings.slideMode.effect) {
                        SlideEffect.Default.value -> {
                            SlideImagePager(
                                imageList = contents,
                                fitSize = settings.slideMode.displayMode == DisplayMode.CenterCrop.value,
                                vertical = settings.slideMode.orientation == Orientation.Vertical.value,
                                switchDuration = switchDuration,
                                showProgress = settings.slideMode.showTimeProgressIndicator
                            )
                        }
                        SlideEffect.Cube.value -> {
                            CubePager(
                                switchDuration,
                                contents,
                                fitSize = settings.slideMode.displayMode == DisplayMode.CenterCrop.value,
                                showProgress = settings.slideMode.showTimeProgressIndicator
                            )
                        }
                        SlideEffect.Reveal.value -> {
                            CircleRevealPager(
                                switchDuration,
                                contents,
                                fitSize = settings.slideMode.displayMode == DisplayMode.CenterCrop.value,
                                showProgress = settings.slideMode.showTimeProgressIndicator
                            )
                        }

//                        SlideEffect.Carousel.value -> {
//                            CarouselPager(
//                                switchDuration,
//                                contents,
//                                fitSize = settings.slideMode.displayMode == DisplayMode.CenterCrop.value,
//                            )
//                        }

                        SlideEffect.Flip.value -> {
                            FlipPager(
                                switchDuration,
                                contents,
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
                                data = contents,
                                duration = it.interval * 1000L,
                                fitSize = settings.frameWallMode.displayMode == DisplayMode.CenterCrop.value,
                            )
                        }
                    }
                }

                SHOWCASE_MODE_FADE -> {

                    FadeLayout(
                        imageList = contents,
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
                        contents
                    )
                }

                SHOWCASE_MODE_BENTO -> {
                    BentoPlay(
                        settings.bentoMode.bentoStyle,
                        settings.bentoMode.interval * 1000L,
                        contents
                    )
                }

                else -> {

                    SlideImagePager(
                        imageList = contents,
                        fitSize = settings.slideMode.displayMode == DisplayMode.CenterCrop.value,
                        vertical = settings.slideMode.orientation == Orientation.Vertical.value,
                        switchDuration = getInterval(settings.slideMode.intervalTimeUnit, settings.slideMode.intervalTime),
                        showProgress = settings.slideMode.showTimeProgressIndicator
                    )
                }
            }

            if (settings.showTimeAndDate && settings.showcaseMode != SHOWCASE_MODE_CALENDER) {
                TimeCard()
            }
        }
    }
}