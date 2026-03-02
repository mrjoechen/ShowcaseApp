package com.alpha.showcase.common.utils

import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.random.CryptographyRandom
import io.ktor.util.decodeBase64String
import io.ktor.util.encodeBase64
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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

private const val ENCRYPTED_PREFIX_V2 = "scenc:v2:"
private const val ENCRYPTED_PREFIX_V1 = "scenc:v1:"
private const val LEGACY_TEST_KEY = "1234567890123456"
private const val LEGACY_TEST_IV = "0123456789abcdef"
private const val GCM_IV_SIZE = 12
private const val AES_KEY_SIZE = 32
private val aesGcm by lazy {
    CryptographyProvider.Default.get(AES.GCM)
}

private fun deriveLegacyRawKeyMaterial(key: String, iv: String): ByteArray {
    val seed = "$key:$iv".encodeToByteArray()
    if (seed.isEmpty()) {
        return ByteArray(AES_KEY_SIZE)
    }
    return ByteArray(AES_KEY_SIZE) { index ->
        val a = seed[index % seed.size].toInt()
        val b = seed[(index * 7 + 3) % seed.size].toInt()
        ((a xor b xor index) and 0xFF).toByte()
    }
}

@OptIn(ExperimentalEncodingApi::class, DelicateCryptographyApi::class)
fun String.encodePass(keyMaterial: ByteArray): String {
    if (isBlank()) return this
    if (startsWith(ENCRYPTED_PREFIX_V2)) return this
    require(keyMaterial.size == AES_KEY_SIZE) {
        "Config encryption key must be $AES_KEY_SIZE bytes. Current size: ${keyMaterial.size}"
    }

    val secretKey = aesGcm.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, keyMaterial)
    val ivBytes = CryptographyRandom.Default.nextBytes(GCM_IV_SIZE)
    val encrypted = secretKey.cipher().encryptWithIvBlocking(ivBytes, encodeToByteArray())
    val payload = ByteArray(ivBytes.size + encrypted.size).also { output ->
        ivBytes.copyInto(output, destinationOffset = 0)
        encrypted.copyInto(output, destinationOffset = ivBytes.size)
    }
    return ENCRYPTED_PREFIX_V2 + Base64.UrlSafe.encode(payload)
}

@OptIn(ExperimentalEncodingApi::class, DelicateCryptographyApi::class)
fun String.decodePass(keyMaterial: ByteArray): String {
    if (isBlank()) return this
    return when {
        startsWith(ENCRYPTED_PREFIX_V2) -> {
            require(keyMaterial.size == AES_KEY_SIZE) {
                "Config encryption key must be $AES_KEY_SIZE bytes. Current size: ${keyMaterial.size}"
            }
            decryptPayload(removePrefix(ENCRYPTED_PREFIX_V2), keyMaterial)
        }
        startsWith(ENCRYPTED_PREFIX_V1) -> {
            val legacyKeyMaterial = deriveLegacyRawKeyMaterial(LEGACY_TEST_KEY, LEGACY_TEST_IV)
            decryptPayload(removePrefix(ENCRYPTED_PREFIX_V1), legacyKeyMaterial)
        }
        else -> this
    }
}

@OptIn(ExperimentalEncodingApi::class, DelicateCryptographyApi::class)
private fun decryptPayload(encodedPayload: String, keyMaterial: ByteArray): String {
    val payload = Base64.UrlSafe.decode(encodedPayload)
    require(payload.size > GCM_IV_SIZE) { "Invalid encrypted payload." }

    val ivBytes = payload.copyOfRange(0, GCM_IV_SIZE)
    val encrypted = payload.copyOfRange(GCM_IV_SIZE, payload.size)
    val secretKey = aesGcm.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, keyMaterial)
    return secretKey.cipher().decryptWithIvBlocking(ivBytes, encrypted).decodeToString()
}

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
