package com.alpha.showcase.common.ui.play

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_CALENDER
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FRAME_WALL
import com.alpha.showcase.common.ui.view.DataNotFoundAnim
import com.alpha.showcase.common.ui.view.LoadingIndicator

@OptIn(ExperimentalCoilApi::class)
@Composable
fun PagerItem(
  modifier: Modifier = Modifier,
  data: Any,
  fitSize: Boolean = false,
  parentType: Int = -1,
  onComplete: (Any) -> Unit = {}
) {

//  val fetchFactory by remember {
//    mutableStateOf(CustomFetcher.CustomFetchFactory())
//  }
  val fit by remember {
    mutableStateOf(fitSize)
  }

  if (data.isImage()) {
    val imageDimensions = remember { mutableStateOf(Pair(0f, 0f)) }

    val painter = rememberAsyncImagePainter(
      model = ImageRequest.Builder(LocalPlatformContext.current)
        .crossfade(300).data(data = data).also {
          when(data) {
            is DataWithType -> {
              it.data(data.data)
            }
            is UrlWithAuth -> {
              data.apply {
                it.data(data.url).httpHeaders(NetworkHeaders.Builder().add(data.key, data.value).build())
              }
            }
            else -> {
              it.data(data)
            }
          }
        }.build(),
      onSuccess = {
        imageDimensions.value =
          Pair(it.painter.intrinsicSize.width, it.painter.intrinsicSize.height)
        onComplete(data)
      },
      onError = {
        onComplete(data)
      }
    )


    var scale by remember { mutableStateOf(if (fit) ContentScale.Fit else ContentScale.Crop) }

    Box(modifier = modifier) {
//      if (BuildConfig.DEBUG && error){
//        Text(text = data.toString())
//      }
      Image(
        painter = painter,
        contentDescription = null,
        modifier = Modifier
          .fillMaxSize()
          .clickable {
            scale = if (scale == ContentScale.Crop) {
              ContentScale.Fit
            } else {
              ContentScale.Crop
            }
          },
        contentScale = scale
      )

      when(val state = painter.state){
        is AsyncImagePainter.State.Success ->{
        }

        is AsyncImagePainter.State.Loading ->{
          LoadingIndicator()
        }

        is AsyncImagePainter.State.Error ->{
          state.result.throwable.printStackTrace()
          DataNotFoundAnim("Error")
        }

        is AsyncImagePainter.State.Empty->{
          DataNotFoundAnim("Empty")
        }
      }
    }
  }

}