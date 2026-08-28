package com.alpha.showcase.api.mtphoto

import com.alpha.showcase.api.BaseHttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.withTimeoutOrNull

class MTPhotoMetadataTimeoutException : Exception("MTPhoto request timed out")

class MTPhotoApi(
    private val metadataRequestTimeoutMillis: Long = DEFAULT_METADATA_REQUEST_TIMEOUT_MILLIS,
    private val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val socketTimeoutMillis: Long = DEFAULT_SOCKET_TIMEOUT_MILLIS,
) : BaseHttpClient() {
    override fun configureClient(config: HttpClientConfig<*>) {
        config.install(HttpTimeout) {
            // Request deadlines are applied explicitly below. Keeping the global value unset is
            // important: some engines otherwise close a lazily consumed media channel using the
            // metadata deadline even when the request tries to override it.
            requestTimeoutMillis = null
            connectTimeoutMillis = this@MTPhotoApi.connectTimeoutMillis
            socketTimeoutMillis = this@MTPhotoApi.socketTimeoutMillis
        }
    }

    suspend fun getAlbums(
        baseUrl: String,
        headerName: String,
        headerValue: String,
    ): List<MTPhotoAlbum> = withMetadataTimeout {
        get("${normalizeMTPhotoBaseUrl(baseUrl)}/api-album") {
            header(headerName, headerValue)
        }
    }

    suspend fun getAlbumFiles(
        baseUrl: String,
        albumId: Int,
        headerName: String,
        headerValue: String,
    ): List<MTPhotoFileItem> = withMetadataTimeout {
        get("${normalizeMTPhotoBaseUrl(baseUrl)}/api-album/filesFlat/$albumId") {
            header(headerName, headerValue)
        }
    }

    suspend fun login(
        baseUrl: String,
        request: MTPhotoLoginRequest,
    ): MTPhotoLoginResponse = withMetadataTimeout {
        post("${normalizeMTPhotoBaseUrl(baseUrl)}/auth/login") {
            setBody(request)
        }
    }

    suspend fun getAuthCode(
        baseUrl: String,
        apiKey: String,
    ): MTPhotoAuthCodeResponse = withMetadataTimeout {
        post("${normalizeMTPhotoBaseUrl(baseUrl)}/auth/auth_code") {
            setBody(MTPhotoAuthCodeRequest(apiKey))
        }
    }

    private suspend fun <T> withMetadataTimeout(block: suspend () -> T): T {
        // withTimeoutOrNull converts only this deadline to null. Cancellation initiated by the
        // caller still propagates, while our deadline becomes a normal, renderable UI failure.
        return withTimeoutOrNull(metadataRequestTimeoutMillis) {
            CompletedMetadataRequest(block())
        }?.value ?: throw MTPhotoMetadataTimeoutException()
    }
}

private data class CompletedMetadataRequest<T>(val value: T)

private const val DEFAULT_METADATA_REQUEST_TIMEOUT_MILLIS = 15_000L
private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000L
private const val DEFAULT_SOCKET_TIMEOUT_MILLIS = 30_000L

const val MTPHOTO_RENDER_TYPE_ORIGINAL = "ori"

fun normalizeMTPhotoBaseUrl(baseUrl: String): String = baseUrl.trim().trimEnd('/')

fun buildMTPhotoGatewayUrl(
    baseUrl: String,
    fileId: Int,
    md5: String,
    albumId: Int,
    authCode: String,
    renderType: String = MTPHOTO_RENDER_TYPE_ORIGINAL,
): String = buildString {
    append(normalizeMTPhotoBaseUrl(baseUrl))
    append("/gateway/file/")
    append(fileId)
    append('/')
    append(md5.encodeURLParameter())
    append("?albumId=")
    append(albumId)
    append("&type=")
    append(renderType.encodeURLParameter())
    append("&auth_code=")
    append(authCode.encodeURLParameter())
}
