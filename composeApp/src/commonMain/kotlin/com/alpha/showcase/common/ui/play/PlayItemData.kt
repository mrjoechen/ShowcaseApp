package com.alpha.showcase.common.ui.play

import coil3.toUri
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

data class DataWithType(
    val data: Any,
    val type: String
)

const val CONTENT_TYPE_VIDEO = "video"
const val CONTENT_TYPE_IMAGE = "image"

fun DataWithType.isImage() = type == CONTENT_TYPE_IMAGE
fun DataWithType.isVideo() = type == CONTENT_TYPE_VIDEO

fun Any.isVideo() = (this is UrlWithAuth && this.url.isNotEmpty() && this.url.getExtension().lowercase() in VIDEO_EXT_SUPPORT)
        || (this is String && this.isNotEmpty() && this.getExtension().lowercase() in VIDEO_EXT_SUPPORT)
        || (this is DataWithType && this.isVideo())


fun Any.isImage() = (this is UrlWithAuth && this.url.isNotEmpty() && this.url.getExtension().lowercase() in IMAGE_EXT_SUPPORT)
        || (this is String && this.isNotEmpty() && this.getExtension().lowercase() in IMAGE_EXT_SUPPORT)
        || (this is DataWithType && this.isImage())

fun String.removeQueryParameter(): String {
    val uri = this.toUri()
    val scheme = uri.scheme
    val host = uri.authority
    val path = uri.path
    return "$scheme://$host$path"
}