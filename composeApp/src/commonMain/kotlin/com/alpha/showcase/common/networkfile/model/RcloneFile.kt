package com.alpha.showcase.common.networkfile.model

import com.alpha.showcase.api.rclone.Remote
import com.alpha.showcase.common.networkfile.storage.remote.RemoteStorage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


@Serializable
data class NetworkFile(
    var remote: RemoteStorage,
    @SerialName("path") val path: String,
    @SerialName("fileName") val fileName: String,
    @SerialName("isDir") val isDirectory: Boolean,
    @SerialName("size") val size: Long,
    @SerialName("mimeType") val mimeType: String,
    @SerialName("modTime") val modTime: String,
    @SerialName("isBucket") val isBucket: Boolean = false,
    @Transient
    val extra: Map<String, String>? = null
){
    val key = "${remote.user}@${remote.schema}${remote.host}:${remote.port}/$path"
}