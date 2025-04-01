package com.alpha.showcase.common.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material.icons.outlined.Splitscreen
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.alpha.showcase.common.ui.view.CheckItem
import com.alpha.showcase.common.ui.view.MultiCheckContent
import com.alpha.showcase.common.ui.view.SlideItem
import com.alpha.showcase.ui.ViewRow
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.column
import showcaseapp.composeapp.generated.resources.display_mode
import showcaseapp.composeapp.generated.resources.interval
import showcaseapp.composeapp.generated.resources.matrix_size
import showcaseapp.composeapp.generated.resources.row


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
    var rowState by remember { mutableStateOf(if (frameWallMode.matrixSizeRow <= 0) 2 else frameWallMode.matrixSizeRow) }
    var columnState by remember { mutableStateOf(if (frameWallMode.matrixSizeColumn <= 0) 2 else frameWallMode.matrixSizeColumn) }

    MultiCheckContent(
        icon = Icons.Outlined.GridOn,
        desc = stringResource(Res.string.matrix_size),
        checkContent = listOf(MATRIX_LIST.map { it to "$it" }, MATRIX_LIST.map { it to "$it" }),
        checkContentDesc = listOf(
            stringResource(Res.string.row),
            stringResource(Res.string.column)
        ),
        checked = listOf(stringResource(Res.string.row) to "$rowState", stringResource(Res.string.column) to "$columnState"),
        checkIcons = listOf(ViewRow, Icons.Outlined.ViewColumn),
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

    CheckItem(
        if (frameWallMode.displayMode == DisplayMode.FitScreen.value) Icons.Outlined.FitScreen else Icons.Outlined.FullscreenExit,
        (if (frameWallMode.displayMode == DisplayMode.FitScreen.value) DisplayMode.Full else DisplayMode.CenterCrop).toPairWithResString(),
        stringResource(Res.string.display_mode),
        listOf(
            DisplayMode.Full.toPairWithResString(),
            DisplayMode.CenterCrop.toPairWithResString()
        ),
        onCheck = {
            onSet(DisplayMode.key, it.first)
        }
    )

    SlideItem(
        Icons.Outlined.HistoryToggleOff,
        desc = stringResource(Res.string.interval),
        value = if (frameWallMode.interval == 0) 5 else frameWallMode.interval,
        range = 5f..60f,
        step = 10,
        unit = "s",
        onValueChanged = {
            onSet(Interval.key, it)
        }
    )
}