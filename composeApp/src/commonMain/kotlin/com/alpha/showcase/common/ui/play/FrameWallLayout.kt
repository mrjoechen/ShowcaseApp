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
    // Re-key on generation so a background-sync refresh re-samples from the fresh
    // dataset instead of holding deleted/old items.
    val generation = pagingItems.generation
    // Load initial items from paging source
    val initialItems = remember(row, column, generation) {
        pagingItems.getRange(0, gridSize.coerceAtMost(pagingItems.size))
    }

    val reservedDataList = remember(row, column, generation) {
        // Items beyond the initial grid serve as the reserve pool
        val reserve = if (pagingItems.size > gridSize) {
            pagingItems.getRange(gridSize, (pagingItems.size - gridSize).coerceAtMost(PagingPlayItems.DEFAULT_PAGE_SIZE))
        } else {
            emptyList()
        }
        reserve.toMutableStateList()
    }

    // Track next index for sequential loading from paging source
    var nextPagedIndex by remember(generation) { mutableIntStateOf(gridSize + reservedDataList.size) }

    fun randomGet(): Any? {
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
        // markEmpty() (size -> 0) can land while an animation coroutine is still
        // mid-iteration: both refills above then produce nothing and nextInt(0)
        // would throw. Report exhaustion instead so the caller stops animating.
        if (reservedDataList.isEmpty()) return null
        val nextInt = nextInt(reservedDataList.size)
        return reservedDataList.removeAt(nextInt)
    }

    val currentShowFrameList = remember(row, column, generation) {
        val list = mutableListOf<Any>()
        list.addAll(initialItems)
        // Fill remaining slots if needed. When the paging source is (transiently)
        // empty, initialItems is empty too — fall back to blank placeholders
        // instead of an `index % 0` crash; the generation key re-runs this block
        // once real data lands.
        while (list.size < gridSize) {
            val filler = if (reservedDataList.isNotEmpty()) {
                randomGet()
            } else {
                initialItems.getOrNull(list.size % initialItems.size.coerceAtLeast(1))
            }
            list.add(filler ?: EMPTY_PLACEHOLDER)
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
    randomGet: () -> Any?
) {

    var preIndex by remember {
        mutableIntStateOf(0)
    }
    // Restart when the frame list is recreated (e.g. after a sync refresh) so the
    // loop animates the current list, not a detached old one.
    LaunchedEffect(frameList) {
        delay(animateDuration)
        while (isActive) {

            repeat(row * column / 10 + 1) {
                if (frameList.isEmpty()) break
                // Fetch the replacement BEFORE removing anything: when the data
                // source was emptied under this running loop, randomGet() returns
                // null and we stop instead of crashing on an empty pool.
                val replacement = randomGet() ?: break
                preIndex = getRandomIntNoRe(frameList.size, preIndex)
                val removeAt = frameList.removeAt(preIndex)
                frameList.add(preIndex, replacement)
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
    randomGet: () -> Any?
) {

    var preIndex by remember {
        mutableIntStateOf(0)
    }
    // Restart when the frame list is recreated (e.g. after a sync refresh).
    LaunchedEffect(frameList) {
        delay(animateDuration)
        while (isActive) {
            if (frameList.isEmpty()) { delay(animateDuration); continue }
            preIndex = nextInt(column)
            repeat(row) {
                // Replacement first: a null means the data source was emptied under
                // this running loop — stop animating rather than crash on nextInt(0).
                val replacement = randomGet() ?: break
                val index = (column * it + (preIndex + it) % column) % frameList.size
                val removeAt = frameList.removeAt(index)
                frameList.add(index, replacement)
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

