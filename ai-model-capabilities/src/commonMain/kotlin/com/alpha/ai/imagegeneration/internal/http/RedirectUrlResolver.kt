package com.alpha.ai.imagegeneration.internal.http

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.http.parseQueryString
import io.ktor.http.takeFrom

/** Resolves HTTP Location references using RFC 3986 section 5.2, without inheriting unrelated query values. */
internal fun resolveRedirectUrl(base: Url, location: String): Url {
    val reference = location.trim()
    val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:").find(reference)?.value
    if (scheme != null || reference.startsWith("//")) {
        val absolute = if (scheme == null) "${base.protocol.name}:$reference" else reference
        if (!absolute.contains(Regex("^https?://[^/?#]+", RegexOption.IGNORE_CASE))) {
            throw ImageGenerationTransportException("Invalid redirect")
        }
        val builder = URLBuilder().takeFrom(absolute)
        builder.encodedPath = removeDotSegments(builder.encodedPath)
        return builder.build()
    }

    val pathAndQuery = reference.substringBefore('#')
    val path = pathAndQuery.substringBefore('?')
    if (':' in path.substringBefore('/')) throw ImageGenerationTransportException("Invalid redirect")
    val hasQuery = '?' in pathAndQuery
    val builder = URLBuilder().takeFrom(base)
    if (path.isNotEmpty()) {
        val mergedPath = if (path.startsWith('/')) path else base.encodedPath.substringBeforeLast('/', "") + "/" + path
        builder.encodedPath = removeDotSegments(mergedPath)
    }
    if (path.isNotEmpty() || hasQuery) {
        builder.encodedParameters.clear()
        builder.trailingQuery = hasQuery && pathAndQuery.substringAfter('?').isEmpty()
        if (hasQuery) builder.encodedParameters.appendAll(parseQueryString(pathAndQuery.substringAfter('?'), decode = false))
    }
    builder.encodedFragment = reference.substringAfter('#', "")
    return builder.build()
}

/** Paths are absolute here; preserve empty segments and remove only literal dot segments. */
private fun removeDotSegments(path: String): String {
    val result = mutableListOf<String>()
    val segments = path.split('/')
    segments.forEachIndexed { index, segment ->
        when (segment) {
            "." -> if (index == segments.lastIndex) result.add("")
            ".." -> {
                if (result.size > 1) result.removeAt(result.lastIndex)
                if (index == segments.lastIndex) result.add("")
            }
            else -> result.add(segment)
        }
    }
    return result.joinToString("/")
}
