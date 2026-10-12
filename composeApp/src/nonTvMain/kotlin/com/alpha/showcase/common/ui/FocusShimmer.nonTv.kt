package com.alpha.showcase.common.ui
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.valentinilk.shimmer.shimmer
@Composable
internal actual fun Modifier.platformFocusShimmer(): Modifier = shimmer()
