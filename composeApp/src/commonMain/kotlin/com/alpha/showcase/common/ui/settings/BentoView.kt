package com.alpha.showcase.common.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bento
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.ui.play.BentoGrid
import com.alpha.showcase.common.ui.play.DEFAULT_PERIOD
import com.alpha.showcase.common.ui.play.bentoStyleMap
import com.alpha.showcase.common.ui.play.bentoStyles
import com.alpha.showcase.common.ui.view.CheckItem
import com.alpha.showcase.common.ui.view.SlideItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.display_style_bento
import showcaseapp.composeapp.generated.resources.interval
import showcaseapp.composeapp.generated.resources.second
import kotlin.collections.get
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

@Composable
fun BentoView(bentoMode: Settings.BentoMode, onSet: (String, Any) -> Unit) {

    CheckItem(
        Icons.Outlined.Bento,
        bentoMode.bentoStyle to "Style ${bentoMode.bentoStyle + 1}",
        stringResource(Res.string.display_style_bento),
        bentoStyleMap,
        onCheck = {
            onSet(BentoStyle.key, it.first)
        }
    )

    BentoStylePreview(
        selectedStyle = bentoMode.bentoStyle,
        onStyleSelected = { styleIndex ->
            onSet(BentoStyle.key, styleIndex)
        }
    )

    SlideItem(
        Icons.Outlined.HistoryToggleOff,
        desc = stringResource(Res.string.interval),
        value = if (bentoMode.interval <= 0) DEFAULT_PERIOD.toInt() / 1000 else bentoMode.interval,
        range = 1f..60f,
        step = 59,
        unit = stringResource(Res.string.second),
        onValueChanged = {
            onSet(Interval.key, it)
        }
    )
}



@Composable
fun BentoStylePreview(
    selectedStyle: Int,
    onStyleSelected: (Int) -> Unit
) {
    val current = LocalWindowInfo.current
    val screenWidth = max(current.containerSize.width, current.containerSize.height).toFloat()
    val screenHeight = min(current.containerSize.width, current.containerSize.height).toFloat()
    val aspectRatio = screenWidth / screenHeight

    val pagerState = rememberPagerState(
        initialPage = selectedStyle.coerceIn(0, bentoStyles.size - 1)
    ) { bentoStyles.size }

    val coroutineScope = rememberCoroutineScope()

    // 标记是否正在进行编程式滚动
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    // 当 selectedStyle 改变时，滚动到对应页面
    LaunchedEffect(selectedStyle) {
        val targetPage = selectedStyle.coerceIn(0, bentoStyles.size - 1)
        if (pagerState.currentPage != targetPage) {
            isProgrammaticScroll = true
            pagerState.animateScrollToPage(targetPage)
            isProgrammaticScroll = false
        }
    }

    // 当用户滑动时，通知外部更新
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collectLatest { page ->
            if (page != selectedStyle && !isProgrammaticScroll) {
                onStyleSelected(page)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // HorizontalPager 显示 Bento 样式预览
        Row(
            modifier = Modifier
                .fillMaxWidth()
        ){

            Spacer(modifier = Modifier.weight(0.15f))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(0.7f)
                    .aspectRatio(aspectRatio)
                    .padding(12.dp)
            ) { page ->
                val bentoStyle = bentoStyles[page]

                // 使用 BentoGrid 显示预览，每个 item 使用随机颜色
                BentoGrid(bentoStyle) { index, item ->
                    val randomColor = remember(page, index) {
                        Color(
                            red = Random.nextFloat(),
                            green = Random.nextFloat(),
                            blue = Random.nextFloat(),
                            alpha = 0.6f
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(randomColor)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(0.15f))
        }


        // Page indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(bentoStyles.size) { index ->
                val isSelected = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            }
                        )
                        .clickable {
                            coroutineScope.launch {
                                isProgrammaticScroll = true
                                pagerState.animateScrollToPage(index)
                                isProgrammaticScroll = false
                                onStyleSelected(index)
                            }
                        }
                )
            }
        }
    }
}