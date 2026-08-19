package com.alpha.showcase.common.ui.play

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.ui.settings.DisplayMode
import com.alpha.showcase.common.ui.settings.Settings
import com.alpha.showcase.common.ui.settings.getInterval
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

private data class SquareVisualConfig(
    val intervalMillis: Long,
    val preferredCellSizeDp: Int,
    val focusScale: Float,
    val spacingDp: Int,
    val transitionMillis: Int,
    val fitSize: Boolean
) {
    companion object {
        fun from(mode: Settings.SquareMode): SquareVisualConfig = SquareVisualConfig(
            intervalMillis = getInterval(timeUnit = 0, interval = mode.interval),
            preferredCellSizeDp = SquareLayoutPolicy.itemSizeDp(mode.squareSize),
            focusScale = SquareLayoutPolicy.focusScalePercent(mode.focusScale) / 100f,
            spacingDp = SquareLayoutPolicy.spacingDp(mode.spacing),
            transitionMillis = SquareLayoutPolicy.transitionMillis(mode.transitionDuration),
            fitSize = mode.displayMode == DisplayMode.CenterCrop.value
        )
    }
}

private class SquareMotionController {
    var animationJob: Job? = null

    fun cancelAnimation() {
        animationJob?.cancel()
        animationJob = null
    }
}

@Composable
fun SquareScreen(
    pagingItems: PagingPlayItems,
    squareMode: Settings.SquareMode,
    modifier: Modifier = Modifier,
    parentActive: Boolean = true,
    editMode: Boolean = false
) {
    val config = remember(squareMode) { SquareVisualConfig.from(squareMode) }
    val coroutineScope = rememberCoroutineScope()
    val motionController = remember(pagingItems) { SquareMotionController() }
    var manualInteractionGeneration by remember(pagingItems) { mutableIntStateOf(0) }

    DisposableEffect(motionController) {
        onDispose(motionController::cancelAnimation)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val itemCount = pagingItems.size
        if (itemCount <= 0) return@BoxWithConstraints

        val density = LocalDensity.current
        val spacing = config.spacingDp.dp
        val cellWidth = SquareLayoutPolicy.focusSafeCellSize(
            preferredCellSize = config.preferredCellSizeDp.toFloat(),
            viewportSize = maxWidth.value,
            focusScale = config.focusScale
        ).dp
        val cellHeight = (cellWidth * (9f / 16f)).coerceAtMost(maxHeight * 0.8f)
        val cellWidthPx = with(density) { cellWidth.toPx() }
        val cellHeightPx = with(density) { cellHeight.toPx() }
        val spacingPx = with(density) { spacing.toPx() }
        val horizontalStepPx = SquareLayoutPolicy.cellStep(
            cellSize = cellWidthPx,
            configuredSpacing = spacingPx,
            focusScale = config.focusScale
        ).coerceAtLeast(1f)
        val verticalStepPx = SquareLayoutPolicy.cellStep(
            cellSize = cellHeightPx,
            configuredSpacing = spacingPx,
            focusScale = config.focusScale
        ).coerceAtLeast(1f)
        val columnCount = SquareLayoutPolicy.balancedColumnCount(itemCount)
        val bounds = SquareLayoutPolicy.scrollBounds(
            itemCount = itemCount,
            columnCount = columnCount,
            horizontalStep = horizontalStepPx,
            verticalStep = verticalStepPx
        )
        val initialCoordinate = SquareLayoutPolicy.initialCenter(itemCount, columnCount)
        val initialIndex = SquareLayoutPolicy.indexFor(
            coordinate = initialCoordinate,
            columnCount = columnCount,
            itemCount = itemCount
        ) ?: (itemCount / 2).coerceIn(0, itemCount - 1)
        val initialOffset = SquareLayoutPolicy.scrollOffsetForIndex(
            index = initialIndex,
            columnCount = columnCount,
            itemCount = itemCount,
            horizontalStep = horizontalStepPx,
            verticalStep = verticalStepPx
        )
        val canvasState = remember(pagingItems) {
            SquareCanvasState(
                initialScrollX = initialOffset.x,
                initialScrollY = initialOffset.y,
                initialFocusedIndex = initialIndex
            )
        }
        val canvasMetrics = remember(
            columnCount,
            cellWidthPx,
            cellHeightPx,
            horizontalStepPx,
            verticalStepPx,
            config.focusScale
        ) {
            SquareCanvasMetrics(
                columnCount = columnCount,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                horizontalStepPx = horizontalStepPx,
                verticalStepPx = verticalStepPx,
                focusScale = config.focusScale
            )
        }

        suspend fun animateToIndex(index: Int) {
            if (index !in 0 until itemCount) return
            val target = SquareLayoutPolicy.scrollOffsetForIndex(
                index = index,
                columnCount = columnCount,
                itemCount = itemCount,
                horizontalStep = horizontalStepPx,
                verticalStep = verticalStepPx
            )
            val startX = canvasState.scrollX
            val startY = canvasState.scrollY
            val distanceInCells = max(
                abs(target.x - startX) / horizontalStepPx,
                abs(target.y - startY) / verticalStepPx
            )
            if (distanceInCells < 0.001f) {
                canvasState.settle(index)
                return
            }
            val duration = (
                config.transitionMillis * distanceInCells.coerceIn(0.65f, 1.5f)
                ).roundToInt()

            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = duration,
                    easing = FastOutSlowInEasing
                )
            ) { progress, _ ->
                canvasState.updateScroll(
                    x = startX + (target.x - startX) * progress,
                    y = startY + (target.y - startY) * progress
                )
            }
            canvasState.updateScroll(target.x, target.y)
            canvasState.settle(index)
        }

        fun startAnimationTo(index: Int): Job {
            motionController.cancelAnimation()
            return coroutineScope.launch {
                animateToIndex(index)
            }.also { motionController.animationJob = it }
        }

        fun snapAfterGesture(fingerVelocity: Offset) {
            if (!parentActive) return
            val targetIndex = SquareLayoutPolicy.releaseTargetIndex(
                scrollX = canvasState.scrollX,
                scrollY = canvasState.scrollY,
                fingerVelocityX = fingerVelocity.x,
                fingerVelocityY = fingerVelocity.y,
                columnCount = columnCount,
                itemCount = itemCount,
                horizontalStep = horizontalStepPx,
                verticalStep = verticalStepPx
            ) ?: return
            startAnimationTo(targetIndex)
        }

        LaunchedEffect(pagingItems, columnCount, horizontalStepPx, verticalStepPx) {
            motionController.cancelAnimation()
            val focusedIndex = canvasState.focusedIndex.coerceIn(0, itemCount - 1)
            val target = SquareLayoutPolicy.scrollOffsetForIndex(
                index = focusedIndex,
                columnCount = columnCount,
                itemCount = itemCount,
                horizontalStep = horizontalStepPx,
                verticalStep = verticalStepPx
            )
            val clamped = SquareLayoutPolicy.clampScrollOffset(target.x, target.y, bounds)
            canvasState.reset(clamped.x, clamped.y, focusedIndex)
        }

        LaunchedEffect(parentActive) {
            if (!parentActive) motionController.cancelAnimation()
        }

        LaunchedEffect(
            pagingItems,
            config.intervalMillis,
            config.transitionMillis,
            parentActive,
            manualInteractionGeneration,
            columnCount
        ) {
            if (!parentActive) return@LaunchedEffect

            while (currentCoroutineContext().isActive && parentActive) {
                delay(config.intervalMillis)
                val center = SquareLayoutPolicy.coordinateForIndex(
                    index = canvasState.focusedIndex,
                    columnCount = columnCount
                )
                val targetIndices = SquareLayoutPolicy.autoPlayCandidates(
                    center = center,
                    previousFocusedIndex = canvasState.previousFocusedIndex,
                    columnCount = columnCount,
                    itemCount = itemCount
                )
                if (targetIndices.isEmpty()) continue

                val targetIndex = targetIndices[Random.nextInt(targetIndices.size)]
                pagingItems.preload(targetIndex)
                startAnimationTo(targetIndex).join()
            }
        }

        SquareLazyCanvas(
            items = pagingItems,
            canvasState = canvasState,
            metrics = canvasMetrics,
            parentActive = parentActive,
            fitSize = config.fitSize,
            editMode = editMode,
            onItemClick = { index ->
                manualInteractionGeneration++
                startAnimationTo(index)
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    pagingItems,
                    itemCount,
                    columnCount,
                    horizontalStepPx,
                    verticalStepPx,
                    config.transitionMillis,
                    parentActive
                ) {
                    if (!parentActive) return@pointerInput
                    val velocityTracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = {
                            motionController.cancelAnimation()
                            velocityTracker.resetTracking()
                            manualInteractionGeneration++
                        },
                        onDragCancel = { snapAfterGesture(Offset.Zero) },
                        onDragEnd = {
                            val velocity = velocityTracker.calculateVelocity()
                            snapAfterGesture(Offset(velocity.x, velocity.y))
                        },
                        onDrag = { change, dragAmount ->
                            velocityTracker.addPosition(
                                timeMillis = change.uptimeMillis,
                                position = change.position
                            )
                            val next = SquareLayoutPolicy.clampScrollOffset(
                                x = canvasState.scrollX - dragAmount.x,
                                y = canvasState.scrollY - dragAmount.y,
                                bounds = bounds
                            )
                            canvasState.updateScroll(next.x, next.y)
                            change.consume()
                        }
                    )
                }
        )
    }
}
