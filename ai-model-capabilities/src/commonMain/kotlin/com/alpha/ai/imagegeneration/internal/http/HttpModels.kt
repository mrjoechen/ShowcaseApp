package com.alpha.ai.imagegeneration.internal.http

import io.ktor.http.ContentType
import io.ktor.http.Url

internal data class ResponseLimits(
    val jsonBytes: Long = 48L * 1024 * 1024,
    val imageBytes: Long = 32L * 1024 * 1024,
)

internal data class HttpRequest(
    val method: String,
    val url: Url,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
) {
    companion object {
        fun get(url: Url, headers: Map<String, String> = emptyMap()) =
            HttpRequest(method = "GET", url = url, headers = headers)

        fun post(url: Url, headers: Map<String, String> = emptyMap(), body: ByteArray? = null) =
            HttpRequest(method = "POST", url = url, headers = headers, body = body?.copyOf())
    }
}

internal data class HttpResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: ByteArray,
)

internal fun HttpResponse.hasJsonContentType(): Boolean {
    val mediaType = try {
        ContentType.parse(headers["content-type"] ?: return false)
    } catch (_: Exception) { return false }
    if (!mediaType.contentType.equals("application", ignoreCase = true)) return false
    val subtype = mediaType.contentSubtype.lowercase()
    return subtype == "json" || (subtype.length > "+json".length && subtype.endsWith("+json"))
}

/** Deliberately carries no request headers, URL query values, or raw response body. */
internal class ImageGenerationTransportException(message: String) : Exception(message)
