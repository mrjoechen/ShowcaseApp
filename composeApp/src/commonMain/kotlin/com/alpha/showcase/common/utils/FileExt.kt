package com.alpha.showcase.common.utils


val SUPPORT_MIME_FILTER_IMAGE = listOf("image/jpeg", "image/webp", "image/png", "image/bmp", "image/gif", "image/dng", "image/heic", "image/heif")
val SUPPORT_MIME_FILTER_VIDEO = listOf("video/mp4", "video/x-matroska", "video/webm", "video/quicktime")
val IMAGE_EXT_SUPPORT =
    listOf("jpg", "png", "jpeg", "bmp", "webp", "heic", "gif", "dng", "svg")
val VIDEO_EXT_SUPPORT = listOf("mp4", "mkv", "webm", "mov")

fun getMimeType(fileName: String): String {
    return when {
        fileName.endsWith(".html", true) -> "text/html"
        fileName.endsWith(".htm", true) -> "text/html"
        fileName.endsWith(".css", true) -> "text/css"
        fileName.endsWith(".js", true) -> "application/javascript"
        fileName.endsWith(".json", true) -> "application/json"
        fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
        fileName.endsWith(".png", true) -> "image/png"
        fileName.endsWith(".webp", true) -> "image/webp"
        fileName.endsWith(".bmp", true) -> "image/bmp"
        fileName.endsWith(".heic", true) -> "image/heic"
        fileName.endsWith(".heif", true) -> "image/heif"
        fileName.endsWith(".gif", true) -> "image/gif"
        fileName.endsWith(".svg", true) -> "image/svg+xml"
        fileName.endsWith(".dng", true) -> "image/dng"
        fileName.endsWith(".ico", true) -> "image/x-icon"
        fileName.endsWith(".ttf", true) -> "font/ttf"
        fileName.endsWith(".otf", true) -> "font/otf"
        fileName.endsWith(".woff", true) -> "font/woff"
        fileName.endsWith(".woff2", true) -> "font/woff2"
        fileName.endsWith(".map", true) -> "application/json"
        fileName.endsWith(".txt", true) -> "text/plain"
        fileName.endsWith(".xml", true) -> "application/xml"
        fileName.endsWith(".mp4", true) -> "video/mp4"
        fileName.endsWith(".mkv", true) -> "video/x-matroska"
        fileName.endsWith(".webm", true) -> "video/webm"
        fileName.endsWith(".mov", true) -> "video/quicktime"
        else -> "application/octet-stream"
    }
}