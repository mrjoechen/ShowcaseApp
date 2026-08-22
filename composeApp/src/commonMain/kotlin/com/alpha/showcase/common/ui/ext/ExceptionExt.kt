package com.alpha.showcase.common.ui.ext

import io.ktor.client.plugins.ResponseException

private val HTTP_STATUS_PATTERN = Regex("""\b([45]\d{2})\s+([A-Za-z][A-Za-z -]*?)(?:\.|$)""")

fun Throwable.getSimpleMessage(): String {
    var current: Throwable? = this
    while (current != null) {
        if (current is ResponseException) {
            val status = current.response.status
            return "Request failed: HTTP ${status.value} ${status.description}"
        }
        current = current.cause
    }

    val firstLine = message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
    if (firstLine.startsWith("Client request(") || firstLine.startsWith("Server response(")) {
        val status = HTTP_STATUS_PATTERN.find(firstLine)
        return if (status != null) {
            val (code, description) = status.destructured
            "Request failed: HTTP $code $description"
        } else {
            "Network request failed"
        }
    }

    return firstLine.ifBlank { toString() }
}
