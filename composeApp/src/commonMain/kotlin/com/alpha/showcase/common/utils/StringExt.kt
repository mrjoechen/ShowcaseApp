package com.alpha.showcase.common.utils

// 获取文件扩展名
fun String.getExtension(): String {
    val dotPos = lastIndexOf('.')
    return try {
        if (dotPos >= 0) {
            if (contains("?")) {
                val endIndex = lastIndexOf("?")
                if (endIndex < dotPos + 1) {
                    substring(endIndex + 1, length).getExtension()
                } else {
                    substring(dotPos + 1, endIndex)
                }
            } else {
                substring(dotPos + 1)
            }
        } else "unknown"
    } catch (e: Exception) {
        e.printStackTrace()
        "error"
    }
}

fun String.isUrl(): Boolean {
    return startsWith("http://") || startsWith("https://")
}

fun String.isUri(): Boolean {
    return startsWith("content://") || startsWith("file://")
}

fun String.encodeName(): String = this

fun String.decodeName(): String = this

fun String.encodePass(key: String, iv: String): String = this

fun String.decodePass(key: String, iv: String): String = this