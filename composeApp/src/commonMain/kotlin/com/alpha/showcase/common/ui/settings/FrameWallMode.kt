package com.alpha.showcase.common.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material.icons.outlined.Splitscreen
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.alpha.showcase.common.data.Settings
import com.alpha.showcase.common.ui.view.MultiCheckContent
import com.alpha.showcase.common.ui.view.SlideItem


val MATRIX_LIST = (2..8).map { it }

@Composable
fun FrameWallModeView(frameWallMode: Settings.FrameWallMode, onSet: (String, Any) -> Unit) {

//    CheckItem(
//        if (frameWallMode.frameStyle == FrameWallMode.FixSize.value) Icons.Outlined.ViewCompact else Icons.Outlined.AutoAwesomeMosaic,
//        FrameWallMode.fromValue(frameWallMode.frameStyle).toPair(),
//        stringResource(R.string.frame_style),
//        listOf(FrameWallMode.FixSize.toPair(), FrameWallMode.Random.toPair()),
//        onCheck = {
//            onSet(FrameWallMode.key, it.first)
//        }
//    )
    var rowState by remember { mutableStateOf(if (frameWallMode.matrixSizeRows <= 0) 2 else frameWallMode.matrixSizeRows) }
    var columnState by remember { mutableStateOf(if (frameWallMode.matrixSizeColumns <= 0) 2 else frameWallMode.matrixSizeColumns) }

    MultiCheckContent(
        icon = Icons.Outlined.GridOn,
        desc = "Matrix Size",
        checkContent = listOf(MATRIX_LIST.map { it to "$it" }, MATRIX_LIST.map { it to "$it" }),
        checkContentDesc = listOf(
            "Row",
            "Column"
        ),
        checked = listOf("row" to "$rowState", "column" to "$columnState"),
        checkIcons = listOf(Icons.Outlined.Splitscreen, Icons.Outlined.ViewColumn),
        onCheckChanged = { index, item ->
            if (index == 0) {
                onSet(MatrixSize.Row, item.first)
                rowState = item.first as Int
            }
            if (index == 1) {
                onSet(MatrixSize.Column, item.first)
                columnState = item.first as Int
            }
        }
    ) { items ->
        val result = StringBuilder()
        items.forEachIndexed { index, item ->
            result.append("${item.second}${if (items.size - 1 == index) "" else " x "}")
        }
        result.toString()
    }

    SlideItem(
        Icons.Outlined.HistoryToggleOff,
        desc = "Interval",
        value = if (frameWallMode.interval == 0) 5 else frameWallMode.interval,
        range = 5f..60f,
        step = 10,
        unit = "s",
        onValueChanged = {
            onSet(Interval.key, it)
        }
    )
}