package com.alpha.showcase.common.ui.view

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import showcaseapp.composeapp.generated.resources.Res
import com.alpha.showcase.common.theme.Dimen
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.data_not_found
import showcaseapp.composeapp.generated.resources.data_under_construct

@Composable
fun DataNotFoundAnim() {
    LabeledAnimation(stringResource(Res.string.data_not_found), "lottie/lottie_error_screen.json")
}

@Composable
fun DataNotFoundAnim(msg: String) {
    LabeledAnimation(msg, "lottie/lottie_error_screen.json")
}

@Composable
fun UnderConstructionAnim() {
    LabeledAnimation(stringResource(Res.string.data_under_construct), "lottie/lottie_building_screen.json")
}

@Composable
fun CircleLoadingIndicator(size: Dp = Dimen.spaceXXL) {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.Center)
                .size(size)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContainedLoadingIndicator(size: Dp = 128.dp) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.ContainedLoadingIndicator(
            modifier = Modifier
                .align(Alignment.Center)
                .size(size),
            containerColor = Color.Transparent
        )
    }
}

@Composable
fun LoadingIndicator(){
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.weight(2f))
            LottieAssetLoader("lottie/lottie_loading.json", modifier = Modifier.weight(1f).padding(0.dp, 10.dp))
            Spacer(modifier = Modifier.weight(2f))
        }
    }
}
