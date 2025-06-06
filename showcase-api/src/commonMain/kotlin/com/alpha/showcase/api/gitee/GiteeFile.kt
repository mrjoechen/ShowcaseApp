package com.alpha.showcase.api.gitee

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GiteeFile(
    @SerialName("name")
    val name: String,
    @SerialName("type")
    val type: String,
    @SerialName("path")
    val path: String,
    @SerialName("size")
    val size: Long?,
    @SerialName("download_url")
    val download_url: String?,
    @SerialName("sha")
    val sha: String?,
    @SerialName("url")
    val url: String?
)

const val FILE_TYPE_DIR = "dir"
const val FILE_TYPE_FILE = "file"