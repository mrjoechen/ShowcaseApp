package com.alpha.showcase.common.ui.ext

import coil3.PlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.alpha.showcase.common.ui.play.DataWithType
import com.alpha.showcase.common.ui.play.UrlWithAuth
import com.alpha.showcase.common.ui.play.removeQueryParameter

fun buildImageRequest(context: PlatformContext, data: Any) = ImageRequest.Builder(context)
    .memoryCachePolicy(CachePolicy.ENABLED)
    .diskCachePolicy(CachePolicy.ENABLED)
    .apply{
        when(data) {
            is DataWithType -> {
                data(data.data)
                if (data.data is String && data.data.startsWith("http")){
                    val key = data.data
                    memoryCacheKey(key).diskCacheKey(key)
                }
            }
            is UrlWithAuth -> {
                data(data.url)
                memoryCacheKey(data.url).diskCacheKey(data.url)
                httpHeaders(NetworkHeaders.Builder().add(data.key, data.value).build())
            }
            is String -> {
                data(data)
                if (data.startsWith("http")){
                    memoryCacheKey(data).diskCacheKey(data)
                }
            }
            else -> {
                data(data)
            }
        }
    }
    .crossfade(600)
    .build()