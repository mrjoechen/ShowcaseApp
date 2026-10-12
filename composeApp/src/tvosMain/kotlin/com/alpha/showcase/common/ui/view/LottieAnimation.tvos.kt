package com.alpha.showcase.common.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/** Keep loading feedback and completion callbacks without a tvOS Compottie artifact. */
@Composable
actual fun LottieAssetLoader(
    lottieAsset: String,
    modifier: Modifier,
    iterations: Int,
    contentScale: ContentScale,
    onFinished: (() -> Unit)?,
) {
    if ("loading" in lottieAsset) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
    LaunchedEffect(lottieAsset, iterations) {
        if (iterations != Int.MAX_VALUE) onFinished?.invoke()
    }
}
