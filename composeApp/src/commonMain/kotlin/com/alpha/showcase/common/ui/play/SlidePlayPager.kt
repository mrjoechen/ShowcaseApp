package com.alpha.showcase.common.ui.play

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SLIDE
import kotlinx.coroutines.delay

@Composable
fun SlideImagePager(
  imageList: List<Any>,
  vertical: Boolean = false,
  fitSize: Boolean = false,
  switchDuration: Long = DEFAULT_PERIOD,
  showProgress: Boolean = true,
  showContentInfo: Boolean = false
) {

  val pagerState = rememberPagerState {
    imageList.size
  }
  val focusRequester = remember { FocusRequester() }

  var currentData by remember {
    mutableStateOf<Any?>(null)
  }

  Box(modifier = Modifier.fillMaxSize()) {
    if (vertical) {
      VerticalPager(
        state = pagerState, modifier = Modifier
          .fillMaxSize()
          .focusRequester(focusRequester)
      ) { page ->

        // Calculate the absolute offset for the current page from the
        // scroll position. We use the absolute value which allows us to mirror
        // any effects for both directions
        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        PagerCard(pageOffset) {
          PagerItem(data = imageList[page], fitSize = fitSize, parentType = SHOWCASE_MODE_SLIDE) {
            currentData = it
          }
        }
      }
    } else {
      HorizontalPager(
        state = pagerState, modifier = Modifier
          .fillMaxSize()
          .focusRequester(focusRequester)
      ) { page ->

        // Calculate the absolute offset for the current page from the
        // scroll position. We use the absolute value which allows us to mirror
        // any effects for both directions
        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        PagerCard(pageOffset) {
          PagerItem(data = imageList[page], fitSize = fitSize, parentType = SHOWCASE_MODE_SLIDE) {
            currentData = it
          }
        }
      }
    }

    var progress by remember { mutableFloatStateOf(-1f) }
    var currentPage by remember { mutableIntStateOf(0) }

    LaunchedEffect(pagerState) {
      snapshotFlow { pagerState.currentPage }.collect { _ ->
        progress = 0f
        currentPage = pagerState.currentPage
      }
    }
    // progress
    if (showProgress && currentData != null) {
      if (progress != 0f) {
        val progressAnimationValue by animateFloatAsState(
          targetValue = progress,
          animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
          label = "progress animateFloat"
        )
        LinearProgressIndicator(
          progress = {
            progressAnimationValue / switchDuration.toFloat()
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .align(Alignment.BottomCenter),
        )
      } else {
        LinearProgressIndicator(
          progress = {
            0f
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .align(Alignment.BottomCenter),
        )
      }
    } else {
      progress = 0f
    }

    LaunchedEffect(pagerState){
      while (true) {
        delay(100)
        if (currentData != null) {
          if (progress > switchDuration + 100 || currentData?.isVideo() == true) {
            currentData = null
            if (pagerState.canScrollForward) {
              pagerState.animateScrollToPage(
                page = pagerState.currentPage + 1
              )
            } else {
              pagerState.animateScrollToPage(
                page = 0
              )
            }
            delay(300)
          } else {
            if (!pagerState.isScrollInProgress) {
              progress += 100
            }
          }
        }

      }
    }
  }
}


@Composable
fun PagerCard(pageOffset: Float, content: @Composable ColumnScope.() -> Unit) {
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
      .fillMaxSize(),
    shape = RectangleShape) {
    content()
  }
}