package com.alpha.showcase.common.networkfile.storage.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("Immich")
open class ImmichSource(
    override val name: String,
    val url: String,
    val port: Int,
    val authType: String = IMMICH_AUTH_TYPE_BEARER,
    val apiKey: String? = null,
    val user: String? = null,
    val pass: String? = null,
    val album: String? = null
) : RemoteApi


const val IMMICH_AUTH_TYPE_API_KEY = "API_KEY"
const val IMMICH_AUTH_TYPE_BEARER = "BEARER"