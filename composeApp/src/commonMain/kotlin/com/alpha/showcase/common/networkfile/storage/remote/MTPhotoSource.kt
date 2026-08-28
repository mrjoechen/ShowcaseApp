package com.alpha.showcase.common.networkfile.storage.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val MTPHOTO_AUTH_TYPE_API_KEY = "API_KEY"
const val MTPHOTO_AUTH_TYPE_PASSWORD = "PASSWORD"

@Serializable
@SerialName("MTPhoto")
data class MTPhotoSource(
    override val name: String,
    val url: String,
    val authType: String = MTPHOTO_AUTH_TYPE_API_KEY,
    val apiKey: String? = null,
    val user: String? = null,
    val pass: String? = null,
    val albumId: Int? = null,
    val albumName: String? = null,
) : RemoteApi
