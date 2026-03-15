package com.alpha.showcase.api.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubFile(
    @SerialName("name")
    val name: String,
    @SerialName("type")
    val type: String,
    @SerialName("path")
    val path: String,
    @SerialName("size")
    val size: Long,
    @SerialName("download_url")
    val download_url: String?
)

@Serializable
data class GithubRelease(
    @SerialName("tag_name")
    val tagName: String,
    @SerialName("name")
    val name: String? = null,
    @SerialName("body")
    val body: String? = null,
    @SerialName("html_url")
    val htmlUrl: String,
    @SerialName("prerelease")
    val prerelease: Boolean = false,
    @SerialName("draft")
    val draft: Boolean = false,
    @SerialName("published_at")
    val publishedAt: String? = null,
    @SerialName("assets")
    val assets: List<GithubReleaseAsset> = emptyList()
)

@Serializable
data class GithubReleaseAsset(
    @SerialName("name")
    val name: String,
    @SerialName("content_type")
    val contentType: String? = null,
    @SerialName("size")
    val size: Long = 0L,
    @SerialName("digest")
    val digest: String? = null,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String
)

const val FILE_TYPE_DIR = "dir"
const val FILE_TYPE_FILE = "file"
