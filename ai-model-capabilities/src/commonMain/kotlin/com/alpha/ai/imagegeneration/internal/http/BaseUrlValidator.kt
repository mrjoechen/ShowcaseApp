package com.alpha.ai.imagegeneration.internal.http

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom

internal object BaseUrlValidator {
    fun validate(value: String, allowInsecureHttp: Boolean): Url {
        // Ktor accepts relative references, so require an explicit absolute HTTP authority first.
        if (!value.matches(Regex("https?://[^/?#]+[^?#]*", RegexOption.IGNORE_CASE)) || value.any { it.isWhitespace() || it == '\\' }) {
            throw invalidRoot()
        }
        val url = try { Url(value) } catch (_: Exception) { throw invalidRoot() }
        if (url.host.isBlank() || url.user != null || url.password != null ||
            !url.parameters.isEmpty() || url.trailingQuery || url.fragment.isNotEmpty()) throw invalidRoot()
        if (url.protocol.name != "https" && !(allowInsecureHttp && url.protocol.name == "http")) throw invalidRoot()
        return url
    }

    fun validateRetainedRemoteUrl(value: String, allowInsecureHttp: Boolean): Url =
        validate(value, allowInsecureHttp)

    private fun invalidRoot() = ImageGenerationTransportException("Invalid provider URL")
}

/** Appends each model identifier as one escaped segment, including embedded slashes. */
internal class ProviderUrlBuilder(url: Url) {
    private val builder = URLBuilder().takeFrom(url)
    fun addPathSegment(value: String) = apply { builder.appendPathSegments(value, encodeSlash = true) }
    fun addQueryParameter(name: String, value: String) = apply { builder.parameters.append(name, value) }
    fun build(): Url = builder.build()
}

internal fun Url.toProviderUrlBuilder() = ProviderUrlBuilder(this)
