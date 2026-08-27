package com.alpha.showcase.api.mtphoto

import com.alpha.showcase.api.BaseHttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.encodeURLParameter

class MTPhotoApi : BaseHttpClient() {

    suspend fun getAlbums(
        baseUrl: String,
        headerName: String,
        headerValue: String,
    ): List<MTPhotoAlbum> = get("${normalizeMTPhotoBaseUrl(baseUrl)}/api-album") {
        header(headerName, headerValue)
    }

    suspend fun getAlbumFiles(
        baseUrl: String,
        albumId: Int,
        headerName: String,
        headerValue: String,
    ): List<MTPhotoFileItem> = get(
        "${normalizeMTPhotoBaseUrl(baseUrl)}/api-album/filesFlat/$albumId"
    ) {
        header(headerName, headerValue)
    }

    suspend fun login(
        baseUrl: String,
        request: MTPhotoLoginRequest,
    ): MTPhotoLoginResponse = post("${normalizeMTPhotoBaseUrl(baseUrl)}/auth/login") {
        setBody(request)
    }

    suspend fun getAuthCode(
        baseUrl: String,
        apiKey: String,
    ): MTPhotoAuthCodeResponse = post("${normalizeMTPhotoBaseUrl(baseUrl)}/auth/auth_code") {
        setBody(MTPhotoAuthCodeRequest(apiKey))
    }

    suspend fun downloadFile(
        baseUrl: String,
        fileId: Int,
        md5: String,
        albumId: Int,
        authCode: String,
        headerName: String,
        headerValue: String,
    ): ByteArray = client.get(
        buildMTPhotoGatewayUrl(baseUrl, fileId, md5, albumId, authCode)
    ) {
        header(headerName, headerValue)
    }.body()
}

fun normalizeMTPhotoBaseUrl(baseUrl: String): String = baseUrl.trim().trimEnd('/')

fun buildMTPhotoGatewayUrl(
    baseUrl: String,
    fileId: Int,
    md5: String,
    albumId: Int,
    authCode: String,
): String = buildString {
    append(normalizeMTPhotoBaseUrl(baseUrl))
    append("/gateway/file/")
    append(fileId)
    append('/')
    append(md5.encodeURLParameter())
    append("?albumId=")
    append(albumId)
    append("&type=ori&auth_code=")
    append(authCode.encodeURLParameter())
}
