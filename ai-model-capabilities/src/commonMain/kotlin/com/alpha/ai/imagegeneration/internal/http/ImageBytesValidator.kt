package com.alpha.ai.imagegeneration.internal.http

import kotlin.io.encoding.Base64

internal data class ValidatedImage(val bytes: ByteArray, val mimeType: String)

internal object ImageBytesValidator {
    fun decodeBase64(value: String, declaredMimeType: String?, limitBytes: Long): ValidatedImage {
        if (limitBytes < 0 || estimatedDecodedSize(value) > limitBytes) {
            throw ImageGenerationTransportException("Image exceeds size limit")
        }
        val bytes = try {
            Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(value)
        } catch (_: IllegalArgumentException) {
            throw ImageGenerationTransportException("Malformed image data")
        }
        return validate(bytes, declaredMimeType, limitBytes)
    }

    fun validate(bytes: ByteArray, declaredMimeType: String?, limitBytes: Long): ValidatedImage {
        if (limitBytes < 0 || bytes.size.toLong() > limitBytes) {
            throw ImageGenerationTransportException("Image exceeds size limit")
        }
        val detectedMimeType = detectMimeType(bytes) ?: throw ImageGenerationTransportException("Unsupported image data")
        val normalizedDeclaredMimeType = declaredMimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
        if (normalizedDeclaredMimeType != null && normalizedDeclaredMimeType != detectedMimeType) {
            throw ImageGenerationTransportException("Image MIME type does not match data")
        }
        return ValidatedImage(bytes.copyOf(), detectedMimeType)
    }

    private fun estimatedDecodedSize(value: String): Long {
        val length = value.length.toLong()
        if (length > Long.MAX_VALUE / 3) return Long.MAX_VALUE
        val completeGroups = length / 4
        val remainder = (length % 4).toInt()
        val padding = when {
            value.endsWith("==") && remainder == 0 -> 2L
            value.endsWith("=") && remainder == 0 -> 1L
            value.endsWith("=") -> return Long.MAX_VALUE
            else -> 0L
        }
        return when (remainder) {
            0 -> completeGroups * 3 - padding
            2 -> completeGroups * 3 + 1
            3 -> completeGroups * 3 + 2
            else -> Long.MAX_VALUE
        }
    }

    private fun detectMimeType(bytes: ByteArray): String? = when {
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4e.toByte() && bytes[3] == 0x47.toByte() && bytes[4] == 0x0d.toByte() &&
            bytes[5] == 0x0a.toByte() && bytes[6] == 0x1a.toByte() && bytes[7] == 0x0a.toByte() -> "image/png"

        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"

        bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".encodeToByteArray()) &&
            bytes.copyOfRange(8, 12).contentEquals("WEBP".encodeToByteArray()) -> "image/webp"

        else -> null
    }
}
