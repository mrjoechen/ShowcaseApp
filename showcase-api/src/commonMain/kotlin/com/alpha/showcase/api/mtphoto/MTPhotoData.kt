package com.alpha.showcase.api.mtphoto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MTPhotoAlbum(
    val id: Int,
    val name: String,
    val cover: String? = null,
    val count: Int = 0,
    val startTime: String? = null,
    val endTime: String? = null,
)

@Serializable
data class MTPhotoFileItem(
    val id: Int,
    @SerialName("MD5") val md5: String,
    val status: Int = 0,
    val tokenAt: String = "",
    val fileType: String = "",
    val duration: Float? = null,
    val fileSize: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val fileName: String = "",
)

@Serializable
data class MTPhotoLoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class MTPhotoLoginResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("auth_code") val authCode: String? = null,
    val username: String? = null,
    val id: Int? = null,
    val isAdmin: Boolean? = null,
)

@Serializable
data class MTPhotoAuthCodeRequest(
    @SerialName("api_key") val apiKey: String,
)

@Serializable
data class MTPhotoAuthCodeResponse(
    @SerialName("auth_code") val authCode: String? = null,
    val msg: String? = null,
)
