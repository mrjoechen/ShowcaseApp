package com.alpha.ai.imagegeneration.internal

internal object SensitiveDataRedactor {
    private const val Redacted = "[REDACTED]"
    private val authorization = Regex("(?i)(authorization\\s*:\\s*)[^\\s,;]+(?:\\s+[^\\s,;]+)?")
    private val apiKeyHeader = Regex("(?i)((?:x-api-key|x-goog-api-key|api[-_]?key|apikey)\\s*[:=]\\s*)[^\\s,;]+")
    private val urlQuery = Regex("([A-Za-z][A-Za-z0-9+.-]*:[^\\s?#]*)\\?[^\\s#]+")
    private val sensitiveQueryValue = Regex(
        "(?i)([?&]?(?:x-amz-[a-z0-9-]*|signature|token|api[_-]?key|apikey|key)=)[^&#\\s,;]+",
    )
    private val base64 = Regex("(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{32,}={0,2}(?![A-Za-z0-9+/])")

    fun redact(value: String, secrets: List<String>): String {
        var redacted = value
        secrets.filter { it.isNotEmpty() }.forEach { secret ->
            redacted = redacted.replace(secret, Redacted)
        }
        return redacted
            .replace(authorization, "$1$Redacted")
            .replace(apiKeyHeader, "$1$Redacted")
            .replace(urlQuery, "$1?$Redacted")
            .replace(sensitiveQueryValue, "$1$Redacted")
            .replace(base64, Redacted)
    }

    fun redactAndBound(value: String?, secrets: List<String>, maxLength: Int): String? =
        value?.let { redact(it, secrets).take(maxLength) }
}
