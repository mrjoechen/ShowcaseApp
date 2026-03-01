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
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.jetbrains.compose.resources.ExperimentalResourceApi
import showcaseapp.composeapp.generated.resources.Res


@Composable
fun LabeledAnimation(label: String, lottieAsset: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = label,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp),
                textAlign = TextAlign.Center)
//      LottieAssetLoader(lottieAsset, repeat)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.weight(1f))
                LottieAssetLoader(lottieAsset, Modifier.weight(1f).fillMaxWidth())
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }

}

@Composable
fun LabeledAnimation(
    msg: String = "",
    lottieAsset: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (msg.isNotBlank()) {
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.weight(1f))
                LottieAssetLoader(lottieAsset, modifier.weight(1f))
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }

}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun LottieAssetLoader(
    lottieAsset: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    iterations: Int = Compottie.IterateForever,
    contentScale: ContentScale = ContentScale.Crop,
    onFinished: (() -> Unit)? = null
) {

    var lottieJson by remember { mutableStateOf("") }
    LaunchedEffect(lottieAsset) {
        lottieJson = Res.readBytes("files/$lottieAsset").decodeToString()
    }
    val composition by rememberLottieComposition { LottieCompositionSpec.JsonString(lottieJson) }
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = iterations
    )
    var hasFinished by remember(lottieAsset, iterations) { mutableStateOf(false) }
    LaunchedEffect(progress, composition, iterations, hasFinished) {
        if (!hasFinished &&
            onFinished != null &&
            composition != null &&
            iterations != Compottie.IterateForever &&
            progress >= 0.999f
        ) {
            hasFinished = true
            onFinished()
        }
    }

    Image(
        painter = rememberLottiePainter(
            composition = composition,
            progress = { progress }
        ),
        contentScale = contentScale,
        modifier = modifier,
        contentDescription = "Lottie animation"
    )
}
