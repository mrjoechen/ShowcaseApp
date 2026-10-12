package com.alpha.showcase.common.ui.view
/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieAnimationState
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.jetbrains.compose.resources.ExperimentalResourceApi
import showcaseapp.composeapp.generated.resources.Res


@OptIn(ExperimentalResourceApi::class)
@Composable
actual fun LottieAssetLoader(
    lottieAsset: String,
    modifier: Modifier,
    iterations: Int,
    contentScale: ContentScale,
    onFinished: (() -> Unit)?
) {

    val compositionResult = rememberLottieComposition(lottieAsset) {
        val lottieJson = Res.readBytes("files/$lottieAsset").decodeToString()
        LottieCompositionSpec.JsonString(lottieJson)
    }
    val composition by compositionResult
    val animationState = animateLottieCompositionAsState(
        composition = composition,
        iterations = iterations
    )
    val progressProvider = lottieProgressProvider(animationState)
    var hasFinished by remember(lottieAsset, iterations) { mutableStateOf(false) }
    LaunchedEffect(
        animationState.composition,
        animationState.isPlaying,
        animationState.isAtEnd,
        compositionResult.isFailure,
        iterations,
        hasFinished,
    ) {
        val animationFinished = animationState.composition != null &&
            !animationState.isPlaying &&
            animationState.isAtEnd
        if (!hasFinished &&
            onFinished != null &&
            iterations != Compottie.IterateForever &&
            (animationFinished || compositionResult.isFailure)
        ) {
            hasFinished = true
            onFinished()
        }
    }

    Image(
        painter = rememberLottiePainter(
            composition = composition,
            progress = progressProvider
        ),
        contentScale = contentScale,
        modifier = modifier,
        contentDescription = "Lottie animation"
    )
}

internal fun lottieProgressProvider(animationState: LottieAnimationState): () -> Float {
    return animationState::value
}
