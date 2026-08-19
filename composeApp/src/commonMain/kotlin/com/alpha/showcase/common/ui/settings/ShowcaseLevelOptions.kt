package com.alpha.showcase.common.ui.settings

import androidx.compose.runtime.Composable
import com.alpha.showcase.common.ui.view.LevelOption
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.fast
import showcaseapp.composeapp.generated.resources.large
import showcaseapp.composeapp.generated.resources.medium
import showcaseapp.composeapp.generated.resources.moderate
import showcaseapp.composeapp.generated.resources.prominent
import showcaseapp.composeapp.generated.resources.slow
import showcaseapp.composeapp.generated.resources.small
import showcaseapp.composeapp.generated.resources.subtle
import showcaseapp.composeapp.generated.resources.very_fast
import showcaseapp.composeapp.generated.resources.very_large
import showcaseapp.composeapp.generated.resources.very_slow
import showcaseapp.composeapp.generated.resources.very_small

@Composable
internal fun speedLevelOptions(values: List<Int>): List<LevelOption> {
    return levelOptions(
        values,
        listOf(
            stringResource(Res.string.very_slow),
            stringResource(Res.string.slow),
            stringResource(Res.string.medium),
            stringResource(Res.string.fast),
            stringResource(Res.string.very_fast)
        )
    )
}

@Composable
internal fun photoSizeLevelOptions(values: List<Int>): List<LevelOption> {
    return levelOptions(
        values,
        listOf(
            stringResource(Res.string.very_small),
            stringResource(Res.string.small),
            stringResource(Res.string.medium),
            stringResource(Res.string.large),
            stringResource(Res.string.very_large)
        )
    )
}

@Composable
internal fun focusLevelOptions(values: List<Int>): List<LevelOption> {
    return levelOptions(
        values,
        listOf(
            stringResource(Res.string.subtle),
            stringResource(Res.string.moderate),
            stringResource(Res.string.prominent)
        )
    )
}

@Composable
internal fun spacingLevelOptions(values: List<Int>): List<LevelOption> {
    val labels = if (values.size >= 5) {
        listOf(
            stringResource(Res.string.very_small),
            stringResource(Res.string.small),
            stringResource(Res.string.medium),
            stringResource(Res.string.large),
            stringResource(Res.string.very_large)
        )
    } else {
        listOf(
            stringResource(Res.string.small),
            stringResource(Res.string.medium),
            stringResource(Res.string.large)
        )
    }
    return levelOptions(values, labels)
}

internal fun levelOptions(values: List<Int>, labels: List<String>): List<LevelOption> {
    return values.indices.map { index ->
        val value = values[index]
        LevelOption(
            value = value,
            label = labels.getOrNull(index) ?: value.toString()
        )
    }
}
