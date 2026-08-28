package com.alpha.showcase.common.repo

internal class SignedS3ObjectUrl(
    url: String,
    val expiresAtEpochMillis: Long,
) {
    private val url = url

    internal fun urlForFetch(): String = url

    override fun toString(): String =
        "SignedS3ObjectUrl(url=<redacted>, expiresAtEpochMillis=$expiresAtEpochMillis)"
}
