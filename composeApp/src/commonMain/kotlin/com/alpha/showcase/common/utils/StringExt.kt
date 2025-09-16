package com.alpha.showcase.common.utils

import io.ktor.util.decodeBase64String
import io.ktor.util.encodeBase64

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

fun String.checkName(
    name: String?,
    block: (() -> Unit)? = null
): Boolean {
    if (name.isNullOrEmpty()) {
        return false
    }

    if (name.contains("/") ||
        name.contains("\\") ||
        name.contains(":") ||
        name.contains("*") ||
        name.contains("?") ||
        name.contains("\"") ||
        name.contains("<") ||
        name.contains(">") ||
        name.contains("|") ||
        name.contains(" ")
    ) {
        return false
    }
    block?.invoke()
    return true
}

// URL安全的Base64编码，将 + 替换为 -，/ 替换为 _，去除 =
fun String.encodeBase64UrlSafe(): String = this.encodeBase64()
    .replace('+', '-')
    .replace('/', '_')
    .replace("=", "")

// URL安全的Base64解码，恢复标准Base64格式然后解码
fun String.decodeBase64UrlSafe(): String {
    val padded = this.replace('-', '+')
        .replace('_', '/')
        .let { 
            val padding = (4 - it.length % 4) % 4
            it + "=".repeat(padding)
        }
    return padded.decodeBase64String()
}