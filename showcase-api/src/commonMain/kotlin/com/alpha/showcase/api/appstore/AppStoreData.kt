package com.alpha.showcase.api.appstore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppStoreLookupResponse(
    @SerialName("resultCount")
    val resultCount: Int = 0,
    @SerialName("results")
    val results: List<AppStoreResult> = emptyList()
)

@Serializable
data class AppStoreResult(
    @SerialName("version")
    val version: String = "",
    @SerialName("trackName")
    val trackName: String = "",
    @SerialName("releaseNotes")
    val releaseNotes: String = "",
    @SerialName("trackViewUrl")
    val trackViewUrl: String = "",
    @SerialName("currentVersionReleaseDate")
    val currentVersionReleaseDate: String = ""
)
