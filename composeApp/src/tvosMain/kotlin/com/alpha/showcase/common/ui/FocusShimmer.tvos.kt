package com.alpha.showcase.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Keep the shared focus-scale feedback; the optional shimmer library has no tvOS target.
@Composable
internal actual fun Modifier.platformFocusShimmer(): Modifier = this
