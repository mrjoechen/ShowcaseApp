package com.alpha.showcase.repo.album

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistResponse(
    @SerialName("error")
    val error: String? = null,
    @SerialName("data")
    val data: List<PlaylistData>?
)

@Serializable
data class PlaylistData(
    @SerialName("name")
    val name: String,
    @SerialName("artist")
    val artist: String?,
    @SerialName("pic")
    val pic: String?,
    @SerialName("url")
    val url: String?,
    @SerialName("lrc")
    val lrc: String?
)