package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_BENTO
import com.alpha.showcase.common.ui.view.DataNotFoundAnim
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.random.Random


val bentoItems1 = listOf(
    BentoItem("1", 0f, 0f, 1f, 1f),
    BentoItem("2", 1f, 0f, 1f, 1f),
    BentoItem("3", 2f, 0f, 2f, 1f),

    BentoItem("4", 4f, 2f, 1f, 1f),
    BentoItem("5", 5f, 2f, 1f, 1f),

    BentoItem("6", 0f, 1f, 2f, 2f),
    BentoItem("7", 2f, 1f, 2f, 1f),
    BentoItem("8", 4f, 0f, 2f, 2f),
    BentoItem("9", 2f, 2f, 2f, 1f),
)

val bentoItems2 = listOf(
    BentoItem("1", 0f, 0f, 1f, 2f),
    BentoItem("2", 0f, 2f, 1.5f, 1f),
    BentoItem("3", 1f, 0f, 1.5f, 1f),
    BentoItem("4", 2.5f, 0f, 1.5f, 1f),
    BentoItem("5", 3f, 1f, 1f, 2f),
    BentoItem("6", 1.5f, 2f, 1.5f, 1f),
    BentoItem("7", 1f, 1f, 1f, 1f),
    BentoItem("7", 2f, 1f, 1f, 1f)
)


val bentoItems3 = listOf(
    BentoItem("1", 0f, 0f, 1f, 1.6f),
    BentoItem("2", 1f, 0f, 1f, 1f),
    BentoItem("3", 2f, 0f, 2f, 1f),
    BentoItem("4", 0f, 1.6f, 1f, 1f),
    BentoItem("5", 1f, 1f, 2f, 1f),
    BentoItem("6", 3f, 1f, 1f, 1f),
    BentoItem("7", 1f, 2f, 1f, 0.6f),
    BentoItem("7", 2f, 2f, 1f, 0.6f),
    BentoItem("7", 3f, 2f, 1f, 0.6f)
)

val bentoItems4 = listOf(
    BentoItem("1", 0f, 0f, 2f, 2f),
    BentoItem("2", 2f, 0f, 1f, 2f),
    BentoItem("3", 3f, 0f, 1f, 1f),
    BentoItem("4", 0f, 2f, 1f, 1f),
    BentoItem("5", 1f, 2f, 1f, 1f),
    BentoItem("6", 2f, 2f, 1f, 1f),
    BentoItem("7", 3f, 1f, 1f, 2f),
)

val bentoItems5 = listOf(
    BentoItem("1", 0f, 0f, 3f, 2f),
    BentoItem("2", 3f, 0f, 1f, 2f),
    BentoItem("3", 0f, 2f, 1f, 1f),
    BentoItem("4", 1f, 2f, 2f, 1f),
    BentoItem("5", 3f, 2f, 1f, 1f),
)

val bentoItems6 = listOf(
    BentoItem("1", 0f, 0f, 2f, 1f),
    BentoItem("2", 2f, 0f, 1f, 1.3f),
    BentoItem("2", 3f, 0f, 1f, 1.3f),
    BentoItem("2", 4f, 0f, 1f, 1.3f),
    BentoItem("2", 5f, 0f, 2f, 1f),

    BentoItem("3", 0f, 1f, 1f, 1f),
    BentoItem("3", 1f, 1f, 1f, 1f),
    BentoItem("3", 0f, 2f, 1f, 1f),
    BentoItem("3", 1f, 2f, 1f, 1f),
    BentoItem("3", 0f, 3f, 2f, 1f),
    BentoItem("3", 2f, 1.3f, 3f, 1.4f),

    BentoItem("3", 2f, 2.7f, 1f, 1.3f),
    BentoItem("3", 3f, 2.7f, 1f, 1.3f),
    BentoItem("3", 4f, 2.7f, 1f, 1.3f),


    BentoItem("4", 5f, 1f, 1f, 1f),
    BentoItem("3", 6f, 1f, 1f, 1f),
    BentoItem("3", 5f, 2f, 1f, 1f),
    BentoItem("3", 6f, 2f, 1f, 1f),
    BentoItem("3", 5f, 3f, 2f, 1f)
)

val bentoItems7 = listOf(
    BentoItem("1", 0f, 0f, 2f, 1f),
    BentoItem("2", 2f, 0f, 1f, 1.3f),
    BentoItem("2", 3f, 0f, 1f, 1.3f),
    BentoItem("2", 4f, 0f, 1f, 1.3f),
    BentoItem("2", 5f, 0f, 2f, 1f),

    BentoItem("3", 0f, 1f, 1f, 1f),
    BentoItem("3", 1f, 1f, 1f, 1f),
    BentoItem("3", 0f, 2f, 1f, 1f),
    BentoItem("3", 1f, 2f, 1f, 1f),
    BentoItem("3", 0f, 3f, 2f, 1f),
    BentoItem("3", 2f, 1.3f, 3f, 1.4f),

    BentoItem("3", 2f, 2.7f, 1f, 1.3f),
    BentoItem("3", 3f, 2.7f, 1f, 1.3f),
    BentoItem("3", 4f, 2.7f, 1f, 1.3f),


    BentoItem("4", 5f, 1f, 1f, 1f),
    BentoItem("3", 6f, 1f, 1f, 0.7f),
    BentoItem("3", 5f, 2f, 1f, 0.7f),
    BentoItem("3", 6f, 1.7f, 1f, 1f),
    BentoItem("3", 5f, 2.7f, 2f, 1.3f)
)

val bentoStyles = listOf(
    BentoLayout(bentoItems1),
    BentoLayout(bentoItems2),
    BentoLayout(bentoItems3),
    BentoLayout(bentoItems4),
    BentoLayout(bentoItems5),
    BentoLayout(bentoItems6),
    BentoLayout(bentoItems7)
)

val bentoStyleMap = List(bentoStyles.size) { index -> index to "Style ${index + 1}" }

@Composable
fun BentoPlay(style: Int, interval: Long = DEFAULT_PERIOD, data: List<Any>) {
    val bentoStyle = remember(style){
        when (style) {
            0 -> {
                BentoLayout(bentoItems1)
            }

            1 -> {
                BentoLayout(bentoItems2)
            }

            2 -> {
                BentoLayout(bentoItems3)
            }

            3 -> {
                BentoLayout(bentoItems4)
            }

            4 -> {
                BentoLayout(bentoItems5)
            }

            5 -> {
                BentoLayout(bentoItems6)
            }

            6 -> {
                BentoLayout(bentoItems7)
            }

            else -> {
                BentoLayout(bentoItems1)
            }
        }
    }


    val currentDisplay = remember(data, style) {
        val toMutableStateList = (if (data.size > bentoStyle.items.size) data.subList(
            0,
            bentoStyle.items.size
        ) else data).toMutableStateList()
        if (data.size < bentoStyle.items.size) {
            repeat(bentoStyle.items.size - data.size) {
                toMutableStateList.add(data[Random.nextInt(data.size)])
            }
        }
        toMutableStateList

    }

    if (data.isNotEmpty()){
        BentoGrid(bentoStyle){ index, item ->
            AnimatedContent(
                currentDisplay[index % currentDisplay.size],
                label = "Bento Grid anim"
            ){
                PagerItem(
                    modifier = Modifier,
                    data = it,
                    false,
                    parentType = SHOWCASE_MODE_BENTO
                )
            }
        }

        var preIndex by remember {
            mutableIntStateOf(0)
        }
        LaunchedEffect(Unit) {
            while (true) {
                delay(if (interval <= 1) DEFAULT_PERIOD else interval)
                preIndex = getRandomIntNoRe(bentoStyle.items.size, preIndex)
                currentDisplay.removeAt(preIndex)
                currentDisplay.add(preIndex, data[Random.nextInt(data.size)])
            }
        }
    }else {
        DataNotFoundAnim()
    }
}


@Serializable
data class BentoLayout(
    @SerialName("items") val items: List<BentoItem>,
    @SerialName("horizontalPadding") val horizontalPadding: Int = 4,
    @SerialName("verticalPadding") val verticalPadding: Int = 4,
    @SerialName("roundCorner") val roundCorner: Int = 6
)
@Serializable
data class BentoItem(
    @SerialName("id") val id: String,
    @SerialName("startX") val startX: Float,
    @SerialName("startY") val startY: Float,
    @SerialName("width") val width: Float,
    @SerialName("height") val height: Float,
)

@Composable
fun BentoGrid(bentoLayout: BentoLayout, itemContent: @Composable BoxScope.(Int, BentoItem) -> Unit) {
    val maxWidth = remember(bentoLayout) { bentoLayout.items.maxOf { it.startX + it.width } }
    val maxHeight = remember(bentoLayout) { bentoLayout.items.maxOf { it.startY + it.height } }

    Layout(
        content = {
            bentoLayout.items.forEachIndexed { index, item ->
                BentoCell(
                    horizontalPadding = bentoLayout.horizontalPadding.dp,
                    verticalPadding = bentoLayout.verticalPadding.dp,
                    roundCorner = bentoLayout.roundCorner.dp
                ){
                    itemContent(index, item)
                }
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.mapIndexed { index, measurable ->
            val item = bentoLayout.items[index]
            val itemWidth = (item.width / maxWidth * constraints.maxWidth).toInt()
            val itemHeight = (item.height / maxHeight * constraints.maxHeight).toInt()
            measurable.measure(constraints.copy(
                minWidth = itemWidth,
                maxWidth = itemWidth,
                minHeight = itemHeight,
                maxHeight = itemHeight
            ))
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val item = bentoLayout.items[index]
                val x = (item.startX / maxWidth * constraints.maxWidth).toInt()
                val y = (item.startY / maxHeight * constraints.maxHeight).toInt()
                placeable.place(x, y)
            }
        }
    }
}


@Composable
fun BentoCell(
    horizontalPadding: Dp,
    verticalPadding: Dp,
    roundCorner: Dp,
    content: @Composable BoxScope.() -> Unit
) {
//    val cellColor = remember { Color(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .clip(RoundedCornerShape(roundCorner))
//            .background(cellColor)
    ) {
        content()
    }
}

@Composable
fun BentoExample() {
    val bento = listOf(
        BentoLayout(bentoItems1),
        BentoLayout(bentoItems2),
        BentoLayout(bentoItems3),
        BentoLayout(bentoItems4),
        BentoLayout(bentoItems5),
        BentoLayout(bentoItems6, 3, 3),
        BentoLayout(bentoItems7, 3, 3)
    )

    val pagerState = rememberPagerState { bento.size }
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        PagerCard(pageOffset){
            BentoGrid(bento[page]){ index, item ->
                Text(text = item.id, Modifier.align(Alignment.Center))
            }
        }
    }

}

@Preview
@Composable
fun PreviewBentoScreen() {
//    BentoScreen()
    BentoExample()
}







