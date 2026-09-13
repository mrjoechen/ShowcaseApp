package com.alpha.showcase.common.ui.play.fold

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode

/** App/preview hosts may disable the feature, but cannot override missing platform support. */
internal val LocalDuoFoldEnabled = staticCompositionLocalOf { true }

/** Gate before allocating recording layers or native effects, including direct pager entry. */
@Composable
internal fun isDuoFoldAvailable(): Boolean {
    if (!LocalDuoFoldEnabled.current || !platformSupportsDuoFold()) return false
    return remember { BlurEffect(1f, 1f, TileMode.Decal).isSupported() }
}

@Composable
internal expect fun platformSupportsDuoFold(): Boolean
