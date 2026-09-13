package com.alpha.showcase.common.ui.play.fold

import androidx.compose.runtime.Composable

// Uses Compose's Skia image filters, not UIKit effects with a newer iOS requirement.
@Composable
internal actual fun platformSupportsDuoFold(): Boolean = true
