package com.alpha.showcase.api.rclone

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RcloneFileItem(
  @SerialName("IsBucket") val isBucket: Boolean = false,
  @SerialName("IsDir") val isDir: Boolean = false,
  @SerialName("MimeType") val mimeType: String = "",
  @SerialName("ModTime") val modTime: String = "",
  @SerialName("Name") val name: String = "",
  @SerialName("Path") val path: String = "",
  @SerialName("Size") val size: Long = 0,
  @SerialName("ID") val id: String = ""
)