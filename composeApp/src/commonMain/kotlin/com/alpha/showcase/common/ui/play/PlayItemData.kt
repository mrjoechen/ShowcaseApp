package com.alpha.showcase.common.ui.play

import coil3.toUri
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.utils.IMAGE_EXT_SUPPORT
import com.alpha.showcase.common.utils.SUPPORT_MIME_FILTER_IMAGE
import com.alpha.showcase.common.utils.SUPPORT_MIME_FILTER_VIDEO
import com.alpha.showcase.common.utils.VIDEO_EXT_SUPPORT
import com.alpha.showcase.common.utils.getExtension
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UrlWithAuth(
    @SerialName("url")
    val url: String,
    @SerialName("key")
    val key: String,
    @SerialName("value")
    val value: String
) {
    override fun toString() = url
}

data class DataWithType(val data: Any, val type: String, val extra: Map<String, String>? = null)

fun DataWithType.isImage() =
    type.lowercase() in IMAGE_EXT_SUPPORT || type.lowercase() in SUPPORT_MIME_FILTER_IMAGE

fun DataWithType.isVideo() =
    type.lowercase() in VIDEO_EXT_SUPPORT || type.lowercase() in SUPPORT_MIME_FILTER_VIDEO

fun Any.isVideo() =
    (this is UrlWithAuth && this.url.isNotEmpty() && this.url.getExtension().lowercase() in VIDEO_EXT_SUPPORT)
            || (this is String && this.isNotEmpty() && this.getExtension().lowercase() in VIDEO_EXT_SUPPORT)
            || (this is DataWithType && this.isVideo())
            || (this is NetworkFile && (this.mimeType.lowercase() in SUPPORT_MIME_FILTER_VIDEO || this.path.getExtension().lowercase() in VIDEO_EXT_SUPPORT))


fun Any.isImage() = (this is UrlWithAuth && this.url.isNotEmpty() && this.url.getExtension().lowercase() in IMAGE_EXT_SUPPORT)
        || (this is String && this.isNotEmpty() && this.getExtension().lowercase() in IMAGE_EXT_SUPPORT)
        || (this is DataWithType && this.isImage())
        || (this is NetworkFile && (this.mimeType.lowercase() in SUPPORT_MIME_FILTER_IMAGE || this.path.getExtension().lowercase() in IMAGE_EXT_SUPPORT))


fun String.removeQueryParameter(): String {
    val uri = this.toUri()
    val scheme = uri.scheme
    val host = uri.authority
    val path = uri.path
    return "$scheme://$host$path"
}