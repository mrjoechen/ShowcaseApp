package com.alpha.showcase.common.mtphoto

data class MTPhotoFile(
    val sourceKey: String,
    val albumId: Int,
    val fileId: Int,
    val md5: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val duration: Float? = null,
    val fileSize: String? = null,
) {
    val cacheKey: String
        get() = "mtphoto://$sourceKey/$albumId/$fileId/$md5"

    val isImage: Boolean
        get() = mimeType.startsWith("image/", ignoreCase = true)

    val isVideo: Boolean
        get() = mimeType.startsWith("video/", ignoreCase = true)
}
