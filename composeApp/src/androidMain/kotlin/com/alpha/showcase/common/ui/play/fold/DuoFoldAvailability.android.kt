package com.alpha.showcase.common.ui.play.fold

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView

@Composable
internal actual fun platformSupportsDuoFold(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocalView.current.isHardwareAccelerated
