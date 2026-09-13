package com.alpha.showcase.common.ui.play.fold

import androidx.compose.runtime.Composable

// Compose Desktop's Skia renderer implements recorded layers and image filters.
@Composable
internal actual fun platformSupportsDuoFold(): Boolean = true
