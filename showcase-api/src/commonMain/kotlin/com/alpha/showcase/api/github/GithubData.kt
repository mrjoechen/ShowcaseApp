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

const val FILE_TYPE_DIR = "dir"
const val FILE_TYPE_FILE = "file"