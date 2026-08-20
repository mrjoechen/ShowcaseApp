package com.alpha.showcase.common.ui.ext

import coil3.PlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import com.alpha.showcase.common.repo.S3_OBJECT_KEY
import com.alpha.showcase.common.repo.presignS3ObjectUrl
import com.alpha.showcase.common.ui.play.DataWithType
import com.alpha.showcase.common.ui.play.UrlWithAuth

internal fun resolveS3NetworkFileUrl(
    file: NetworkFile,
    signer: (S3Source, String) -> String = ::presignS3ObjectUrl,
): String {
    val source = file.remote as? S3Source ?: return file.path
    val objectKey = file.extra?.get(S3_OBJECT_KEY) ?: file.path
    return signer(source, objectKey)
}

internal fun s3NetworkFileCacheKey(file: NetworkFile): String {
    val source = file.remote as S3Source
    val objectKey = file.extra?.get(S3_OBJECT_KEY) ?: file.path
    val version = file.extra?.get("etag")?.takeIf { it.isNotBlank() }
        ?: "${file.modTime}:${file.size}"
    return "s3:${source.useSSL}:${source.endpoint}:${source.region}:${source.bucket}:$objectKey:$version"
}

fun buildImageRequest(context: PlatformContext, data: Any) = ImageRequest.Builder(context)
    .memoryCachePolicy(CachePolicy.ENABLED)
    .diskCachePolicy(CachePolicy.ENABLED)
    .apply{
        when(data) {
            is DataWithType -> {
                val value = data.data
                if (value is NetworkFile && value.remote is S3Source) {
                    data(resolveS3NetworkFileUrl(value))
                    val key = s3NetworkFileCacheKey(value)
                    memoryCacheKey(key).diskCacheKey(key)
                } else {
                    data(value)
                }
                if (value is String && value.startsWith("http")){
                    val key = value
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
            is UrlWithAuth -> {
                data(data.url)
                val key = data.url
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
