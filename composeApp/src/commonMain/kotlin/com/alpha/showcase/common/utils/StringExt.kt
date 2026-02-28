package com.alpha.showcase.common.utils

import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import io.ktor.util.decodeBase64String
import io.ktor.util.encodeBase64
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

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

private const val ENCRYPTED_PREFIX = "scenc:v1:"
private const val GCM_IV_SIZE = 12
private val aesGcm by lazy {
    CryptographyProvider.Default.get(AES.GCM)
}

private fun deriveRawKeyMaterial(key: String, iv: String): ByteArray {
    val seed = "$key:$iv".encodeToByteArray()
    if (seed.isEmpty()) {
        return ByteArray(32)
    }
    return ByteArray(32) { index ->
        val a = seed[index % seed.size].toInt()
        val b = seed[(index * 7 + 3) % seed.size].toInt()
        ((a xor b xor index) and 0xFF).toByte()
    }
}

@OptIn(ExperimentalEncodingApi::class, DelicateCryptographyApi::class)
fun String.encodePass(key: String, iv: String): String {
    if (isBlank()) return this
    if (startsWith(ENCRYPTED_PREFIX)) return this

    return runCatching {
        val keyBytes = deriveRawKeyMaterial(key, iv)
        val secretKey = aesGcm.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, keyBytes)
        val ivBytes = Random.nextBytes(GCM_IV_SIZE)
        val encrypted = secretKey.cipher().encryptWithIvBlocking(ivBytes, encodeToByteArray())
        val payload = ByteArray(ivBytes.size + encrypted.size).also { output ->
            ivBytes.copyInto(output, destinationOffset = 0)
            encrypted.copyInto(output, destinationOffset = ivBytes.size)
        }
        ENCRYPTED_PREFIX + Base64.UrlSafe.encode(payload)
    }.getOrElse {
        this
    }
}

@OptIn(ExperimentalEncodingApi::class, DelicateCryptographyApi::class)
fun String.decodePass(key: String, iv: String): String {
    if (isBlank()) return this
    if (!startsWith(ENCRYPTED_PREFIX)) return this

    return runCatching {
        val encodedPayload = removePrefix(ENCRYPTED_PREFIX)
        val payload = Base64.UrlSafe.decode(encodedPayload)
        if (payload.size <= GCM_IV_SIZE) {
            this
        } else {
            val ivBytes = payload.copyOfRange(0, GCM_IV_SIZE)
            val encrypted = payload.copyOfRange(GCM_IV_SIZE, payload.size)
            val keyBytes = deriveRawKeyMaterial(key, iv)
            val secretKey = aesGcm.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, keyBytes)
            val decrypted = secretKey.cipher().decryptWithIvBlocking(ivBytes, encrypted)
            decrypted.decodeToString()
        }
    }.getOrElse {
        this
    }
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
