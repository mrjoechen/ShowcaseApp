package com.alpha.ai.imagegeneration.internal.http

import io.ktor.http.fromHttpToGmtDate
import io.ktor.util.date.GMTDate

internal fun parseRetryAfter(value: String?, nowMillis: Long = GMTDate().timestamp): Long? {
    val seconds = value?.trim()?.toLongOrNull()
    if (seconds != null && seconds >= 0) {
        return if (seconds > Long.MAX_VALUE / 1_000) Long.MAX_VALUE else seconds * 1_000
    }
    return try {
        (value?.trim().orEmpty().fromHttpToGmtDate().timestamp - nowMillis).coerceAtLeast(0)
    } catch (_: Exception) { null }
}
