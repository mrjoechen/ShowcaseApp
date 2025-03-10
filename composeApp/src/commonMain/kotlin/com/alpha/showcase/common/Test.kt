package com.alpha.showcase.common

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

fun main(){
    //https://www.icloud.com/sharedalbum/zh-cn/#xxx
    val album = ICloudSharedAlbum("xxx")
    runBlocking {
        val photos = album.getPhotos()
        photos.forEach {
            println(it)
        }
    }
}

class ICloudSharedAlbum(private val albumToken: String) {
    private val client = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val BASE_62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        private const val CHUNK_SIZE = 25
    }

    private fun base62ToInt(str: String): Int {
        var result = 0
        str.forEach { char ->
            result = result * 62 + BASE_62_CHARS.indexOf(char)
        }
        return result
    }

    private fun getBaseUrl(token: String): String {
        val firstChar = token[0]
        val serverPartition = if (firstChar == 'A') {
            base62ToInt(token[1].toString())
        } else {
            base62ToInt(token.substring(1, 3))
        }

        val cleanToken = token.split(";")[0]
        return buildString {
            append("https://p")
            append(if (serverPartition < 10) "0$serverPartition" else serverPartition)
            append("-sharedstreams.icloud.com/")
            append(cleanToken)
            append("/sharedstreams/")
        }
    }

    @Serializable
    private data class WebStreamResponse(
        val photos: List<Photo>,
        @SerialName("X-Apple-MMe-Host") val host: String? = null
    )

    @Serializable
    private data class Photo(
        val photoGuid: String,
        val caption: String? = null,
        val derivatives: Map<String, Derivative>
    )

    @Serializable
    private data class Derivative(
        val checksum: String,
        val fileSize: String
    )

    @Serializable
    private data class UrlResponse(
        val items: Map<String, UrlItem>
    )

    @Serializable
    private data class UrlItem(
        @SerialName("url_location") val urlLocation: String,
        @SerialName("url_path") val urlPath: String
    )

    data class PhotoInfo(
        val guid: String,
        val caption: String?,
        val url: String,
        val fileSize: Long
    )

    suspend fun getPhotos(): List<PhotoInfo> = coroutineScope {
        var baseUrl = getBaseUrl(albumToken)

        // 获取重定向的baseUrl
        val redirectedUrl = getRedirectedBaseUrl(baseUrl)
        if (redirectedUrl != null) {
            baseUrl = redirectedUrl
        }

        // 获取照片元数据
        val metadata = getPhotoMetadata(baseUrl)

        // 按chunks获取URL
        val photoGuids = metadata.map { it.photoGuid }
        val chunks = photoGuids.chunked(CHUNK_SIZE)

        val urlsDeferred = chunks.map { chunk ->
            async { getUrls(baseUrl, chunk) }
        }

        // 等待所有URL请求完成
        val allUrls = urlsDeferred.awaitAll().flatMap { it.entries }
            .associate { it.key to it.value }

        // 组合最终结果
        metadata.mapNotNull { photo ->
            val bestDerivative = photo.derivatives.maxByOrNull {
                it.value.fileSize.toLongOrNull() ?: 0L
            }?.value

            if (bestDerivative != null) {
                val url = allUrls[bestDerivative.checksum]
                if (url != null) {
                    PhotoInfo(
                        guid = photo.photoGuid,
                        caption = photo.caption,
                        url = url,
                        fileSize = bestDerivative.fileSize.toLongOrNull() ?: 0L
                    )
                } else null
            } else null
        }
    }

    private suspend fun getRedirectedBaseUrl(baseUrl: String): String? {
        val response = client.post("${baseUrl}webstream") {
            headers {
                append(HttpHeaders.Origin, "https://www.icloud.com")
                append(HttpHeaders.UserAgent, "Mozilla/5.0")
                append(HttpHeaders.ContentType, "text/plain")
                append(HttpHeaders.Accept, "*/*")
                append(HttpHeaders.Referrer, "https://www.icloud.com/sharedalbum/")
            }
            setBody("""{"streamCtag":null}""")
        }

        if (response.status == HttpStatusCode.fromValue(330)) {
            val webStreamResponse = json.decodeFromString<WebStreamResponse>(response.bodyAsText())
            return webStreamResponse.host?.let { host ->
                "https://$host/$albumToken/sharedstreams/"
            }
        }
        return null
    }

    private suspend fun getPhotoMetadata(baseUrl: String): List<Photo> {
        val response = client.post("${baseUrl}webstream") {
            headers {
                append(HttpHeaders.Origin, "https://www.icloud.com")
                append(HttpHeaders.UserAgent, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                append(HttpHeaders.ContentType, "text/plain")
                append(HttpHeaders.Accept, "*/*")
                append(HttpHeaders.Connection, "keep-alive")
                append(HttpHeaders.Referrer, "https://www.icloud.com/")
                append(HttpHeaders.AcceptEncoding, "gzip, deflate, br, zstd")
                append(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7,zh-TW;q=0.6")
//                append("Sec-Ch-Ua", "\"Chromium\";v=\"130\", \"Google Chrome\";v=\"130\", \"Not?A_Brand\";v=\"99\"")
//                append("Sec-Ch-Ua-Mobile", "?0")
//                append("Sec-Ch-ua-Platform", "\"macOS\"")
//                append("Sec-Fetch-Mode", "cors")
//                append("Sec-Fetch-Site", "same-site")
//                append(HttpHeaders.Host, "p123-sharedstreams.icloud.com")

            }
            setBody("""{"streamCtag":null}""")
        }

        val string = response.bodyAsText()
        val webStreamResponse = json.decodeFromString<WebStreamResponse>(string)
        return webStreamResponse.photos
    }

    private suspend fun getUrls(baseUrl: String, photoGuids: List<String>): Map<String, String> {
        val response = client.post("${baseUrl}webstream") {
            headers {
                append(HttpHeaders.Origin, "https://www.icloud.com")
                append(HttpHeaders.UserAgent, "Mozilla/5.0")
                append(HttpHeaders.ContentType, "text/plain")
                append(HttpHeaders.Accept, "*/*")
                append(HttpHeaders.Referrer, "https://www.icloud.com/sharedalbum/")
            }
            setBody("""{"photoGuids":${json.encodeToString(photoGuids)}}""")
        }

        val urlResponse = json.decodeFromString<UrlResponse>(response.bodyAsText())
        return urlResponse.items.mapValues { (_, item) ->
            "https://${item.urlLocation}${item.urlPath}"
        }
    }
}