package com.alpha.showcase.common.ui.play.flip

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.ic_onedrive
import showcaseapp.composeapp.generated.resources.ic_tmdb

fun PagerState.offsetForPage(page: Int) = (currentPage - page) + currentPageOffsetFraction

fun PagerState.startOffsetForPage(page: Int): Float {
    return offsetForPage(page).coerceAtLeast(0f)
}

fun PagerState.endOffsetForPage(page: Int): Float {
    return offsetForPage(page).coerceAtMost(0f)
}

enum class FlipAxis {
    Horizontal, // 绕 Y 轴旋转，实现水平翻转
    Vertical    // 绕 X 轴旋转，实现垂直翻转
}

/**
 * 一个可翻转的容器 Composable。
 *
 * @param frontContent 正面显示的内容。
 * @param backContent 背面显示的内容。
 * @param isFlipped 是否翻转到背面。
 * @param modifier 修饰符。
 * @param axis 翻转轴，分为水平或垂直。
 * @param animationSpec 动画规约，可自定义动画速度和效果。
 */
@Composable
fun Flippable(
    frontContent: @Composable () -> Unit,
    backContent: @Composable () -> Unit,
    isFlipped: Boolean,
    modifier: Modifier = Modifier,
    axis: FlipAxis = FlipAxis.Vertical,
    animationSpec: androidx.compose.animation.core.AnimationSpec<Float> = spring(
        stiffness = Spring.StiffnessMedium
    )
) {
    // 动画驱动：根据 isFlipped 状态，在 0f 和 180f 之间执行动画
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = animationSpec,
        label = "flipAnimation"
    )

    // 判断当前是否应该显示正面
    val isFrontVisible = rotation < 90f

    Box(
        modifier = modifier
            .graphicsLayer {
                // 根据选择的轴向设置旋转
                if (axis == FlipAxis.Vertical) {
                    rotationX = rotation
                } else {
                    rotationY = rotation
                }
                // 增加镜头距离，使 3D 透视效果更明显
                cameraDistance = 12f * density
            }
    ) {
        if (isFrontVisible) {
            frontContent()
        } else {
            // 显示背面时，需要将其自身先翻转180度，以修正朝向
            Box(
                modifier = Modifier.graphicsLayer {
                    if (axis == FlipAxis.Vertical) {
                        rotationX = 180f
                    } else {
                        rotationY = 180f
                    }
                }
            ) {
                backContent()
            }
        }
    }
}


/**
 * 一个类似 AnimatedContent 的 Composable，但使用3D翻转效果来切换内容。
 *
 * @param S 状态的类型。
 * @param targetState 目标状态。当此状态改变时，会触发翻转动画。
 * @param modifier 修饰符。
 * @param axis 翻转轴。
 * @param animationSpec 动画规约。
 * @param content 用于根据状态显示内容的 lambda。
 */
@Composable
fun <S> FlippableContent(
    targetState: S,
    modifier: Modifier = Modifier,
    axis: FlipAxis = FlipAxis.Vertical,
    animationSpec: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessVeryLow),
    content: @Composable (S) -> Unit
) {
    // 使用 Animatable 替代 animateFloatAsState
    val rotation = remember { Animatable(0f) }

    // 我们需要三个状态来管理翻转：
    // 1. currentFront: 当前稳定显示在正面的状态
    // 2. currentBack: 当前稳定显示在背面的状态
    // 3. isFlipped: 卡片是否处于翻转到背面的状态
    var currentFront by remember { mutableStateOf(targetState) }
    var currentBack by remember { mutableStateOf(targetState) }
    var isFlipped by remember { mutableStateOf(false) }

    // 当外部 targetState 改变时，启动副作用
    LaunchedEffect(targetState) {
        // 如果目标状态就是当前所显示的内容，则不做任何事
        if (targetState == (if (isFlipped) currentBack else currentFront)) {
            return@LaunchedEffect
        }

        // --- 核心中断处理逻辑 ---
        if (isFlipped) {
            // 如果当前已经翻转到背面，现在要翻转回来
            // 那么，新的背面就是当前的正面，新的正面是 targetState
            currentFront = targetState
        } else {
            // 如果当前在正面，要翻转到背面
            // 那么，新的背面就是 targetState
            currentBack = targetState
        }

        // 切换翻转状态，并启动动画
        isFlipped = !isFlipped
        launch {
            rotation.animateTo(
                targetValue = if (isFlipped) 180f else 0f,
                animationSpec = animationSpec
            )
        }
    }

    val isFrontVisible = rotation.value < 90f

    Box(
        modifier = modifier
            .graphicsLayer {
                if (axis == FlipAxis.Vertical) {
                    rotationX = rotation.value
                } else {
                    rotationY = rotation.value
                }
                cameraDistance = 12f * density
            }
    ) {
        if (isFrontVisible) {
            content(currentFront)
        } else {
            Box(
                modifier = Modifier.graphicsLayer {
                    if (axis == FlipAxis.Vertical) {
                        rotationX = 180f
                    } else {
                        rotationY = 180f
                    }
                }
            ) {
                content(currentBack)
            }
        }
    }
}

@Preview
@Composable
fun FlipAnimationDemo() {
    var isVerticalFlipped by remember { mutableStateOf(false) }
    var isHorizontalFlipped by remember { mutableStateOf(false) }

    val androidPainter = painterResource(Res.drawable.ic_tmdb)
    val composePainter = painterResource(Res.drawable.ic_onedrive)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("垂直翻转 (绕水平轴)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Flippable(
            isFlipped = isVerticalFlipped,
            axis = FlipAxis.Vertical,
            frontContent = {
                Image(
                    painter = androidPainter,
                    contentDescription = "Android Logo",
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { isVerticalFlipped = !isVerticalFlipped },
                    contentScale = ContentScale.Crop
                )
            },
            backContent = {
                Image(
                    painter = composePainter,
                    contentDescription = "Compose Logo",
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { isVerticalFlipped = !isVerticalFlipped },
                    contentScale = ContentScale.Crop
                )
            }
        )
        Button(
            onClick = { isVerticalFlipped = !isVerticalFlipped },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(if (isVerticalFlipped) "Front" else "Back")
        }

        Spacer(Modifier.height(48.dp))

        Text("水平翻转 (绕垂直轴)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Flippable(
            isFlipped = isHorizontalFlipped,
            axis = FlipAxis.Horizontal,
            frontContent = {
                Image(
                    painter = androidPainter,
                    contentDescription = "Android Logo",
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { isHorizontalFlipped = !isHorizontalFlipped },
                    contentScale = ContentScale.Crop
                )
            },
            backContent = {
                Image(
                    painter = composePainter,
                    contentDescription = "Compose Logo",
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { isHorizontalFlipped = !isHorizontalFlipped },
                    contentScale = ContentScale.Crop
                )
            }
        )
        Button(
            onClick = { isHorizontalFlipped = !isHorizontalFlipped },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(if (isHorizontalFlipped) "翻回正面" else "翻到背面")
        }
    }
}