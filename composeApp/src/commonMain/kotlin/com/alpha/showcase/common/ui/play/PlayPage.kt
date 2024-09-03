package com.alpha.showcase.common.ui.play

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.navigation.compose.rememberNavController
import com.alpha.showcase.common.components.BackHandler
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.ui.ext.handleBackKey
import com.alpha.showcase.common.ui.settings.Settings
import com.alpha.showcase.common.ui.settings.DisplayMode
import com.alpha.showcase.common.ui.settings.FrameWallMode
import com.alpha.showcase.common.ui.settings.Orientation
import com.alpha.showcase.common.ui.settings.ProgressIndicator
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_CALENDER
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FADE
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FRAME_WALL
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SLIDE
import com.alpha.showcase.common.ui.settings.SettingPreferenceRepo
import com.alpha.showcase.common.ui.settings.SettingsViewModel.Companion.settingsFlow
import com.alpha.showcase.common.ui.settings.getInterval
import com.alpha.showcase.common.ui.view.BackKeyHandler
import com.alpha.showcase.common.ui.view.DataNotFoundAnim
import com.alpha.showcase.common.ui.vm.UiState
import com.alpha.showcase.common.ui.vm.succeeded
import com.alpha.showcase.common.utils.ToastUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.the_number_of_files_may_be_too_large_please_wait


const val LOADING_WARNING_TIME = 5000L
const val DEFAULT_PERIOD = 5000L

@Composable
fun PlayPage(remoteApi: RemoteApi<Any>, onBack: () -> Unit = {}) {
    val focusRequester = remember { FocusRequester() }

    Surface(
        Modifier.handleBackKey{
            onBack()
        }.focusRequester(focusRequester).focusable()
    ) {

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
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
                        Res.string.the_number_of_files_may_be_too_large_please_wait
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


                        BackKeyHandler(onBack = {onBack()}) {
                            if (it.data.isNotEmpty()) {
                                MainPlayContentPage(it.data.toMutableList(), settings)
                            } else {
                                DataNotFoundAnim()
                            }
                        }

                    }
                }

                else -> {}
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
                    SlideImagePager(
                        imageList = contents,
                        fitSize = settings.slideMode.displayMode == DisplayMode.CenterCrop.value,
                        vertical = settings.slideMode.orientation == Orientation.Vertical.value,
                        switchDuration = getInterval(settings.slideMode.intervalTimeUnit, settings.slideMode.intervalTime),
                        showProgress = settings.slideMode.showTimeProgressIndicator
                    )

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

//                SHOWCASE_MODE_CUBE -> {
//                    CubePager()
//                }

//                SHOWCASE_MODE_REVEAL -> {
//                    CircleRevealPager()
//                }

//                SHOWCASE_MODE_CAROUSEL -> {
//                    CarouselPager()
//                }

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