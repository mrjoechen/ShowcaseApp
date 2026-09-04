package com.alpha.showcase.common.utils

import kotlinx.coroutines.withTimeoutOrNull

internal const val CONNECTION_PROBE_TIMEOUT_MILLIS = 10_000L

internal class ConnectionProbeTimeoutException(timeoutMillis: Long) :
    Exception("Connection probe timed out after ${timeoutMillis / 1_000} seconds")

private data class CompletedConnectionProbe<T>(val result: Result<T>)

internal suspend fun <T> runConnectionProbe(
    timeoutMillis: Long = CONNECTION_PROBE_TIMEOUT_MILLIS,
    block: suspend () -> Result<T>,
): Result<T> {
    val completed = withTimeoutOrNull(timeoutMillis) {
        CompletedConnectionProbe(block())
    }
    return completed?.result ?: Result.failure(ConnectionProbeTimeoutException(timeoutMillis))
}
