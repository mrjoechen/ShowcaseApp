package com.alpha.showcase.api

import com.alpha.showcase.api.mtphoto.MTPhotoAlbum
import com.alpha.showcase.api.mtphoto.MTPhotoFileItem
import com.alpha.showcase.api.mtphoto.MTPhotoLoginResponse
import com.alpha.showcase.api.mtphoto.buildMTPhotoGatewayUrl
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MTPhotoDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun responsesDecodeMTPhotoFieldNames() {
        val album = json.decodeFromString<MTPhotoAlbum>(
            """{"id":17,"name":"旅行","cover":"cover.jpg","count":2,"startTime":"2026-01-01","endTime":null}"""
        )
        val file = json.decodeFromString<MTPhotoFileItem>(
            """{"id":23,"MD5":"abc123","status":1,"tokenAt":"2026-01-02","fileType":"image/jpeg","duration":null,"fileSize":"2048","width":1600,"height":900}"""
        )
        val login = json.decodeFromString<MTPhotoLoginResponse>(
            """{"access_token":"token","refresh_token":"refresh","expires_in":86400,"auth_code":"code","username":"joe","id":1,"isAdmin":true}"""
        )

        assertEquals(17, album.id)
        assertEquals("abc123", file.md5)
        assertEquals("image/jpeg", file.fileType)
        assertEquals("code", login.authCode)
    }

    @Test
    fun gatewayUrlNormalizesBaseUrlAndEncodesAuthCode() {
        assertEquals(
            "https://photos.example/gateway/file/23/abc123?albumId=17&type=ori&auth_code=code%20with%20spaces",
            buildMTPhotoGatewayUrl(
                baseUrl = "https://photos.example/",
                fileId = 23,
                md5 = "abc123",
                albumId = 17,
                authCode = "code with spaces",
            )
        )
    }
}
