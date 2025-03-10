package com.alpha.showcase.common.ui.play

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_BENTO
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_CALENDER
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FADE
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FRAME_WALL
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SLIDE
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_CAROUSEL
import com.alpha.showcase.common.ui.settings.Settings
import com.alpha.showcase.common.ui.settings.SettingsViewModel
import com.alpha.showcase.common.ui.settings.settingsStyleList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.absoluteValue


@Composable
fun StylePreview(contents: List<Any>, settings: Settings, viewModel: SettingsViewModel) {
    var editMode by remember { mutableStateOf(false) }

    var vertical by remember {
        mutableStateOf(false)
    }

    val animHorizontalPadding by animateDpAsState(
        targetValue = if (editMode) 192.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "animHorizontalPadding"
    )

    val animVerticalPadding by animateDpAsState(
        targetValue = if (editMode) 64.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "animVerticalPadding"
    )
    val pagerList = remember {
        settingsStyleList.map { it.value }.toMutableStateList()
    }
    Surface(modifier = Modifier
        .fillMaxSize()
        .onGloballyPositioned { coordinates ->
            val width = coordinates.size.width
            val height = coordinates.size.height
            vertical = height > width
        }
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                // 检测缩放手势
                if (zoom != 1f) {
                    editMode = zoom < 1f
                }
            }
        }
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                // 检测垂直方向的移动
                if (abs(pan.y) > abs(pan.x) * 2) {
                    editMode = true
                }
            }
        }
    ) {
        var currentMode by remember {
            mutableStateOf(
                getMode(settings, settings.showcaseMode)
            )
        }
        val scope = rememberCoroutineScope()

        val pagerState = rememberPagerState(
            initialPage = pagerList.indexOf(settings.showcaseMode),
            pageCount = { pagerList.size })

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collectLatest { page ->
                // 当页面改变时触发
                currentMode = getMode(settings, pagerList[page])
            }
        }

        HorizontalPager(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = if (vertical) animVerticalPadding else animHorizontalPadding,
            ),
            state = pagerState,
            userScrollEnabled = editMode
        ) { page ->
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue

            Column {
                Card(
                    Modifier
                        .graphicsLayer {
                            // Calculate the absolute offset for the current page from the
                            // scroll position. We use the absolute value which allows us to mirror
                            // any effects for both directions
                            // We animate the scaleX + scaleY, between 85% and 100%
                                lerp(
                                    0.8f,
                                    1f,
                                    1f - pageOffset.coerceIn(0f, 1f)
                                )
                                .also { scale ->
                                    scaleX = scale
                                    scaleY = scale
                                }

                            // We animate the alpha, between 50% and 100%
                            alpha = lerp(
                                0.5f,
                                1f,
                                1f - pageOffset.coerceIn(0f, 1f)
                            )
                        }
                        .fillMaxWidth()
                        .then(if (editMode) Modifier.aspectRatio(if (vertical) 9f / 16f else 16f / 9f) else Modifier)
                        .clickable {
                            editMode = false
                            scope.launch {
                                viewModel.updateSettings(
                                    settings.copy(showcaseMode = pagerList[page])
                                )
                            }
                        },
                    shape = if (editMode) RoundedCornerShape(16.dp) else RectangleShape,
                    border = BorderStroke(1.dp, if (editMode && pagerState.currentPage == page) MaterialTheme.colorScheme.primary else Color.Transparent)
                ) {

                    Box(modifier = Modifier.fillMaxSize()){
                        MainPlayContentPage(
                            contents,
                            settings.copy(showcaseMode = pagerList[page])
                        )
                        if (editMode) {
                            // Show the edit mode overlay
                            Surface(modifier = Modifier
                                .fillMaxSize().background(Color.Transparent), color = Color.Transparent) {
                            }
                        }
                    }

                }

                Text(
                    stringResource(settingsStyleList[page].resString),
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = .6f),
                            blurRadius = 25f,
                        )
                    )
                )
            }

        }
    }


}

fun getMode(settings: Settings, mode: Int): Any {
    return when (mode) {
        SHOWCASE_MODE_SLIDE -> settings.slideMode
        SHOWCASE_MODE_FADE -> settings.fadeMode
        SHOWCASE_MODE_FRAME_WALL -> settings.frameWallMode
        SHOWCASE_MODE_CALENDER -> settings.calenderMode
        SHOWCASE_MODE_BENTO -> settings.bentoMode
        SHOWCASE_MODE_CAROUSEL -> settings.carouselMode
        else -> settings.slideMode
    }
}