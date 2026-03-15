package com.alpha.showcase.common.update

import java.io.File
import java.security.MessageDigest

internal fun verifyFileDigestOrThrow(file: File, expectedDigest: String?) {
    if (expectedDigest.isNullOrBlank()) return

    val (algorithm, expectedValue) = parseExpectedDigest(expectedDigest)
    val actualValue = file.calculateDigest(algorithm)
    if (!actualValue.equals(expectedValue, ignoreCase = true)) {
        throw IllegalStateException("Digest verification failed for ${file.name} ($algorithm)")
    }
}

private fun parseExpectedDigest(rawDigest: String): Pair<String, String> {
    val trimmed = rawDigest.trim()
    require(trimmed.isNotBlank()) { "Digest is blank" }

    val parts = trimmed.split(":", limit = 2)
    return if (parts.size == 2) {
        normalizeAlgorithm(parts[0]) to parts[1].trim().removePrefix("0x")
    } else {
        // GitHub currently returns "sha256:<hex>", but keep backward compatibility
        // for plain hash values by defaulting to SHA-256.
        "SHA-256" to trimmed.removePrefix("0x")
    }
}

private fun normalizeAlgorithm(raw: String): String {
    return when (raw.trim().lowercase()) {
        "sha256", "sha-256" -> "SHA-256"
        "sha512", "sha-512" -> "SHA-512"
        "sha1", "sha-1" -> "SHA-1"
        else -> raw.trim().uppercase()
    }
}

private fun File.calculateDigest(algorithm: String): String {
    val messageDigest = MessageDigest.getInstance(algorithm)
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            messageDigest.update(buffer, 0, read)
        }
    }
    return messageDigest.digest().joinToString("") { byte ->
        "%02x".format(byte)
    }
}
