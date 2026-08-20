package com.alpha.showcase.common.ui.config

import com.alpha.showcase.common.networkfile.storage.remote.RssSource
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import com.alpha.showcase.common.networkfile.storage.remote.S3_DEFAULT_REGION
import com.alpha.showcase.common.utils.checkName
import com.alpha.showcase.common.utils.encodeName
import io.ktor.http.Url

internal data class S3ConfigDraft(
    val name: String,
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
    val region: String,
    val prefix: String,
    val useSSL: Boolean,
    val existingEncryptedSecretKey: String? = null,
    val secretKeyChanged: Boolean = true,
) {
    fun toSource(encryptSecret: (String) -> String): S3Source? {
        val trimmedEndpoint = endpoint.trim()
        val normalizedEndpoint = trimmedEndpoint.removeSuffix("/")
        val resolvedSecret = when {
            secretKeyChanged && secretKey.isNotBlank() -> encryptSecret(secretKey)
            !secretKeyChanged && !existingEncryptedSecretKey.isNullOrBlank() -> existingEncryptedSecretKey
            else -> null
        }
        if (
            !checkName(name) ||
            !isValidS3Endpoint(trimmedEndpoint) ||
            accessKey.isBlank() ||
            resolvedSecret.isNullOrBlank() ||
            bucket.isBlank()
        ) {
            return null
        }

        return S3Source(
            name = name.trim().encodeName(),
            endpoint = normalizedEndpoint,
            accessKey = accessKey.trim(),
            secretKey = resolvedSecret,
            bucket = bucket.trim(),
            region = region.trim().ifBlank { S3_DEFAULT_REGION },
            prefix = normalizeS3Prefix(prefix),
            useSSL = useSSL,
        )
    }
}

internal data class RssConfigDraft(
    val name: String,
    val url: String,
) {
    fun toSource(): RssSource? {
        val normalizedUrl = url.trim()
        if (!checkName(name) || !isValidHttpUrl(normalizedUrl)) return null
        return RssSource(name = name.trim().encodeName(), url = normalizedUrl)
    }
}

internal fun isValidS3Endpoint(endpoint: String): Boolean {
    val trimmed = endpoint.trim().removeSuffix("/")
    if (trimmed.isBlank() || trimmed.any { it.isWhitespace() }) return false

    val schemeSeparator = trimmed.indexOf("://")
    val authority = if (schemeSeparator >= 0) {
        val scheme = trimmed.substring(0, schemeSeparator).lowercase()
        if (scheme !in setOf("http", "https")) return false
        trimmed.substring(schemeSeparator + 3)
    } else {
        trimmed
    }
    if (
        authority.isBlank() ||
        authority.any { it == '/' || it == '\\' || it == '@' || it == '?' || it == '#' }
    ) {
        return false
    }

    val (host, port) = parseS3Authority(authority) ?: return false
    return isValidS3Host(host) && (port == null || port in 1..65535)
}

private fun parseS3Authority(authority: String): Pair<String, Int?>? {
    if (authority.startsWith('[')) {
        val closingBracket = authority.indexOf(']')
        if (closingBracket <= 1) return null
        val host = authority.substring(0, closingBracket + 1)
        val remainder = authority.substring(closingBracket + 1)
        if (remainder.isEmpty()) return host to null
        if (!remainder.startsWith(':')) return null
        return parseS3Port(remainder.substring(1))?.let { port -> host to port }
    }

    if ('[' in authority || ']' in authority) return null
    return when (authority.count { it == ':' }) {
        0 -> authority to null
        1 -> {
            val host = authority.substringBefore(':')
            parseS3Port(authority.substringAfter(':')).let { port ->
                if (port == null) null else host to port
            }
        }
        else -> null
    }
}

private fun parseS3Port(value: String): Int? {
    if (value.isEmpty() || value.any { !it.isDigit() }) return null
    return value.toIntOrNull()?.takeIf { it in 1..65535 }
}

private fun isValidS3Host(host: String): Boolean {
    if (host.startsWith('[') && host.endsWith(']')) {
        return isValidIpv6Literal(host.substring(1, host.lastIndex))
    }
    if (host.isEmpty() || host.length > 253 || ':' in host) return false

    if (host.all { it.isDigit() || it == '.' } && '.' in host) {
        return isValidIpv4Address(host)
    }

    return host.split('.').all { label ->
        label.length in 1..63 &&
            label.first().isAsciiLetterOrDigit() &&
            label.last().isAsciiLetterOrDigit() &&
            label.all { it.isAsciiLetterOrDigit() || it == '-' }
    }
}

private fun isValidIpv6Literal(value: String): Boolean {
    if (value.isBlank() || ':' !in value) return false
    val compressionIndex = value.indexOf("::")
    if (compressionIndex >= 0 && compressionIndex != value.lastIndexOf("::")) return false

    val groups = if (compressionIndex >= 0) {
        val left = value.substring(0, compressionIndex)
        val right = value.substring(compressionIndex + 2)
        val leftGroups = if (left.isEmpty()) emptyList() else left.split(':')
        val rightGroups = if (right.isEmpty()) emptyList() else right.split(':')
        if (leftGroups.any(String::isEmpty) || rightGroups.any(String::isEmpty)) return false
        leftGroups + rightGroups
    } else {
        if (value.startsWith(':') || value.endsWith(':')) return false
        value.split(':')
    }

    var groupCount = 0
    groups.forEachIndexed { index, group ->
        if ('.' in group) {
            if (index != groups.lastIndex || !isValidIpv4Address(group)) return false
            groupCount += 2
        } else {
            if (group.length !in 1..4 || group.any { !it.isHexDigit() }) return false
            groupCount += 1
        }
    }
    return if (compressionIndex >= 0) groupCount < 8 else groupCount == 8
}

private fun isValidIpv4Address(value: String): Boolean {
    val octets = value.split('.')
    return octets.size == 4 && octets.all { octet ->
        octet.isNotEmpty() &&
            octet.all { it in '0'..'9' } &&
            octet.toIntOrNull() in 0..255
    }
}

private fun Char.isAsciiLetterOrDigit(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

internal fun isValidHttpUrl(url: String): Boolean = runCatching {
    val parsed = Url(url)
    parsed.protocol.name.lowercase() in setOf("http", "https") && parsed.host.isNotBlank()
}.getOrDefault(false)

private fun normalizeS3Prefix(prefix: String): String =
    prefix.trim().trim('/').let { normalized ->
        if (normalized.isBlank()) "" else "$normalized/"
    }
