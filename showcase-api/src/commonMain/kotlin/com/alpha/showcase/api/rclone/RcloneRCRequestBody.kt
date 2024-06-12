package com.alpha.showcase.api.rclone

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RcloneRCRequestBody(
    @SerialName("fs") val fs: String,
    @SerialName("remote") val remote: String,
    @SerialName("opt") val opt: Opt? = null
)

@Serializable
data class Opt(
    @SerialName("recurse") val recurse: Boolean,
    @SerialName("filesOnly") val filesOnly: Boolean
)
