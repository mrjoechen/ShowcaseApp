package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.utils.getExtension

data class UrlWithAuth(val url: String, val key: String, val value: String) {
    override fun toString() = url
}


data class DataWithType(val data: Any, val type: String)

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