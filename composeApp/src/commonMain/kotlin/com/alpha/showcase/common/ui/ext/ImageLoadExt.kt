package com.alpha.showcase.common.ui.ext

import coil3.PlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.alpha.showcase.common.ui.play.DataWithType
import com.alpha.showcase.common.ui.play.ResolvedImageModel
import com.alpha.showcase.common.ui.play.UrlWithAuth
import com.alpha.showcase.common.ui.play.withMediaMetadata
import okio.ByteString.Companion.encodeUtf8

private fun ImageRequest.Builder.resolvedImage(model: ResolvedImageModel) {
    data(model)
    memoryCacheKey(model.cacheKey).diskCacheKey(model.cacheKey)
}

fun buildImageRequest(context: PlatformContext, data: Any) = ImageRequest.Builder(context)
    .memoryCachePolicy(CachePolicy.ENABLED)
    .diskCachePolicy(CachePolicy.ENABLED)
    .apply{
        when(data) {
            is DataWithType -> {
                val value = data.data
                when (value) {
                    is ResolvedImageModel -> resolvedImage(value)
                    else -> data(value)
                }
                if (value is String && value.startsWith("http")){
                    val key = authenticatedImageKey(value, data.extra.orEmpty())
                    data.extra?.let {
                        NetworkHeaders.Builder()
                    }?.let { headerBuilder ->
                        data.extra.forEach{ entry ->
                            headerBuilder.add(entry.key, entry.value)
                        }
                        httpHeaders(headerBuilder.build())
                    }
                    memoryCacheKey(key).diskCacheKey(key)
                }
            }
            is ResolvedImageModel -> resolvedImage(data)
            is UrlWithAuth -> {
                data(data.url)
                val key = data.cacheKey ?: authenticatedImageKey(data.url, mapOf(data.key to data.value))
                memoryCacheKey(key).diskCacheKey(key)
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

internal fun buildMediaImageRequest(context: PlatformContext, data: Any) =
    buildImageRequest(context, data).newBuilder().withMediaMetadata().build()

private fun authenticatedImageKey(url: String, headers: Map<String, String>): String {
    if (headers.isEmpty()) return url
    val identity = url + "\u0000" + headers.entries.sortedBy { it.key.lowercase() }
        .joinToString("\u0000") { "${it.key.lowercase()}:${it.value}" }
    return "authenticated-image:${identity.encodeUtf8().sha256().hex()}"
}
