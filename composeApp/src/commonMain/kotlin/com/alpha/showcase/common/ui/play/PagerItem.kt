package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.alpha.showcase.common.ui.ext.buildImageRequest
import com.alpha.showcase.common.ui.view.DataNotFoundAnim
import com.alpha.showcase.common.ui.view.LoadingIndicator
import com.alpha.showcase.common.utils.ToastUtil
import isDesktop

@Composable
fun PagerItem(
  modifier: Modifier = Modifier,
  data: Any,
  fitSize: Boolean = false,
  parentType: Int = -1,
  onComplete: (Any) -> Unit = {}
) {
  val scale = if (fitSize) ContentScale.Fit else ContentScale.Crop

  if (data.isImage()) {
//    val painter = rememberAsyncImagePainter(
//      model = ImageRequest.Builder(LocalPlatformContext.current)
//        .crossfade(300)
//        .data(
//          when (data) {
//            is DataWithType -> data.data
//            is UrlWithAuth -> data.url
//            else -> data
//          }
//        )
//        .apply {
//          if (data is UrlWithAuth) {
//            httpHeaders(NetworkHeaders.Builder().add(data.key, data.value).build())
//          }
//        }
//        .build(),
//      onSuccess = { onComplete(data) },
//      onError = { onComplete(data) }
//    )

    var currentScale by remember { mutableStateOf(scale) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var errorInfo by remember { mutableStateOf("") }

    Box(modifier = modifier) {
      AsyncImage(
        model = buildImageRequest(LocalPlatformContext.current, data),
        contentDescription = null,
        onSuccess = {
          loading = false
          onComplete(data)
        },
        onError = {
          it.result.throwable.cause?.printStackTrace()
          errorInfo = it.result.throwable.message ?: "Error"
          loading = false
          error = true
          onComplete(data)
//          ToastUtil.error(it.result.throwable.message ?: "Error")
        },
        onLoading = { loading = true },
        contentScale = currentScale,
        modifier = Modifier
          .fillMaxSize()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = if (isDesktop()) null else LocalIndication.current,
          ) {
            currentScale = if (currentScale == ContentScale.Crop) {
              ContentScale.Fit
            } else {
              ContentScale.Crop
            }
          },
      )

      AnimatedVisibility(visible = loading, enter = fadeIn(), exit = fadeOut()) {
        LoadingIndicator()
      }

      AnimatedVisibility(visible = error, enter = fadeIn(), exit = fadeOut()) {
        DataNotFoundAnim(errorInfo)
      }
    }



//    Box(modifier = modifier) {
//      Image(
//        painter = painter,
//        contentDescription = null,
//        modifier = Modifier
//          .fillMaxSize()
//          .clickable {
//            currentScale = if (currentScale == ContentScale.Crop) {
//              ContentScale.Fit
//            } else {
//              ContentScale.Crop
//            }
//          },
//        contentScale = currentScale
//      )
//      when (val state = painter.state) {
//        is AsyncImagePainter.State.Success -> { /* Do nothing */ }
//        is AsyncImagePainter.State.Loading -> {
//          LoadingIndicator()
//        }
//        is AsyncImagePainter.State.Error -> {
//          state.result.throwable.printStackTrace()
//          DataNotFoundAnim("Error")
//        }
//        is AsyncImagePainter.State.Empty -> {
//          DataNotFoundAnim("Empty")
//        }
//      }
//    }
  }else {
    DataNotFoundAnim("Unsupported data")
  }
}
