package com.alpha.showcase.common.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bento
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.runtime.Composable
import com.alpha.showcase.common.ui.play.DEFAULT_PERIOD
import com.alpha.showcase.common.ui.play.bentoStyleMap
import com.alpha.showcase.common.ui.view.CheckItem
import com.alpha.showcase.common.ui.view.SlideItem
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.display_style_bento
import showcaseapp.composeapp.generated.resources.interval
import showcaseapp.composeapp.generated.resources.second

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