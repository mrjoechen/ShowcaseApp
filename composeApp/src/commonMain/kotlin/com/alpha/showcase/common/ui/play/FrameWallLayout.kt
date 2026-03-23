package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.ui.play.flip.FlipAxis
import com.alpha.showcase.common.ui.play.flip.FlippableContent
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FRAME_WALL
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random.Default.nextBoolean
import kotlin.random.Random.Default.nextInt


@Composable
fun FrameWallLayout(
    row: Int,
    column: Int,
    pagingItems: PagingPlayItems,
    random: Boolean = false,
    duration: Long = DEFAULT_PERIOD,
    fitSize: Boolean = false
) {

    val gridSize = row * column
    // Load initial items from paging source
    val initialItems = remember(row, column) {
        pagingItems.getRange(0, gridSize.coerceAtMost(pagingItems.size))
    }

    val reservedDataList = remember(row, column) {
        // Items beyond the initial grid serve as the reserve pool
        val reserve = if (pagingItems.size > gridSize) {
            pagingItems.getRange(gridSize, (pagingItems.size - gridSize).coerceAtMost(PagingPlayItems.DEFAULT_PAGE_SIZE))
        } else {
            emptyList()
        }
        reserve.toMutableStateList()
    }

    // Track next index for sequential loading from paging source
    var nextPagedIndex by remember { mutableIntStateOf(gridSize + reservedDataList.size) }

    fun randomGet(): Any {
        if (reservedDataList.isEmpty()) {
            // Refill from paging source
            if (nextPagedIndex < pagingItems.size) {
                val batch = pagingItems.getRange(nextPagedIndex, PagingPlayItems.DEFAULT_PAGE_SIZE.coerceAtMost(pagingItems.size - nextPagedIndex))
                nextPagedIndex += batch.size
                reservedDataList.addAll(batch)
            }
            if (reservedDataList.isEmpty()) {
                // Wrap around
                nextPagedIndex = 0
                val batch = pagingItems.getRange(0, gridSize.coerceAtMost(pagingItems.size))
                reservedDataList.addAll(batch)
            }
        }
        val nextInt = nextInt(reservedDataList.size)
        return reservedDataList.removeAt(nextInt)
    }

    val currentShowFrameList = remember(row, column) {
        val list = mutableListOf<Any>()
        list.addAll(initialItems)
        // Fill remaining slots if needed
        while (list.size < gridSize) {
            list.add(if (reservedDataList.isNotEmpty()) randomGet() else initialItems[list.size % initialItems.size])
        }
        list.toMutableStateList()
    }

    Column {
        repeat(row) { i ->
            Row(modifier = Modifier.weight(1f / row)) {
                repeat(column) { j ->
                    Column(modifier = Modifier.weight(1f / column)) {
                        FlippableContent(
                            currentShowFrameList[i * column + j],
                            axis = if (kotlin.random.Random.nextBoolean()) FlipAxis.Vertical else FlipAxis.Horizontal
                        ){
                            PagerItem(
                                modifier = Modifier.padding(2.dp),
                                data = it,
                                fitSize,
                                parentType = SHOWCASE_MODE_FRAME_WALL
                            )
                        }
                    }
                }
            }
        }
    }

    val style by remember {
        mutableIntStateOf(1)
    }


    when (style) {
        0 -> {
            AnimateStyle0(
                row,
                column,
                currentShowFrameList,
                animateDuration = if (duration <= 0) DEFAULT_PERIOD else duration,
                onRecycle = {
                    reservedDataList.add(it)
                }
            ) {
                randomGet()
            }
        }

        1 -> {
            AnimateStyle1(
                row,
                column,
                currentShowFrameList,
                animateDuration = if (duration <= 0) DEFAULT_PERIOD else duration,
                onRecycle = {
                    reservedDataList.add(it)
                }
            ) {
                randomGet()
            }
        }

        else -> {

        }
    }

}

// replace the old frame with a new frame
@Composable
fun AnimateStyle0(
    row: Int,
    column: Int,
    frameList: SnapshotStateList<Any>,
    animateDuration: Long,
    onRecycle: (Any) -> Unit,
    randomGet: () -> Any
) {

    var preIndex by remember {
        mutableIntStateOf(0)
    }
    LaunchedEffect(Unit) {
        delay(animateDuration)
        while (isActive) {

            repeat(row * column / 10 + 1) {
                preIndex = getRandomIntNoRe(frameList.size, preIndex)
                val removeAt = frameList.removeAt(preIndex)
                frameList.add(preIndex, randomGet())
                onRecycle(removeAt)
                delay(1000)
            }
            delay(animateDuration)
        }
    }
}

@Composable
fun AnimateStyle1(
    row: Int,
    column: Int,
    frameList: SnapshotStateList<Any>,
    animateDuration: Long,
    onRecycle: (Any) -> Unit,
    randomGet: () -> Any
) {

    var preIndex by remember {
        mutableIntStateOf(0)
    }
    LaunchedEffect(Unit) {
        delay(animateDuration)
        while (isActive) {
            preIndex = nextInt(column)
            repeat(row) {
                val index = (column * it + (preIndex + it) % column) % frameList.size
                val removeAt = frameList.removeAt(index)
                frameList.add(index, randomGet())
                onRecycle(removeAt)
                delay(800)
            }
            delay(animateDuration)
        }
    }
}

fun getRandomIntNoRe(bound: Int, candi: Int?): Int {
    val nextInt = nextInt(bound)
    return if (candi == null || nextInt != candi) nextInt else getRandomIntNoRe(bound, candi)
}

