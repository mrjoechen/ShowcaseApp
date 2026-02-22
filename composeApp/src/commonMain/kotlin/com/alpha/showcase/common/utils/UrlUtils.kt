package com.alpha.showcase.common.utils

/**
 * Decode percent-encoded URL path segments (e.g. %E4%B8%AD%E6%96%87 → 中文).
 * This is a multiplatform-compatible implementation.
 */
fun decodeUrlPath(encoded: String): String {
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < encoded.length) {
        if (encoded[i] == '%' && i + 2 < encoded.length) {
            val hex = encoded.substring(i + 1, i + 3)
            try {
                bytes.add(hex.toInt(16).toByte())
                i += 3
                continue
            } catch (_: NumberFormatException) {
                // not a valid percent-encoded sequence, treat as literal
            }
        }
        // flush any accumulated bytes before adding a regular char
        if (bytes.isNotEmpty()) {
            // should not happen in well-formed sequences, but handle gracefully
        }
        bytes.addAll(encoded[i].code.let {
            if (it <= 0x7F) listOf(it.toByte())
            else encoded[i].toString().encodeToByteArray().toList()
        })
        i++
    }
    return bytes.toByteArray().decodeToString()
}
