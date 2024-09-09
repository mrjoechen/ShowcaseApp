package com.alpha.showcase.common.networkfile.storage.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("TMDB")
open class TMDBSource(
    override val name: String,
    val contentType: String?,
    val language: String?,
    val region: String?,
    val imageType: String?
) : RemoteApi