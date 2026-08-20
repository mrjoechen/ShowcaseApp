package com.alpha.showcase.common.ui.config

import kotlinx.coroutines.CancellationException

internal data class RemoteOptionsPage<T>(
    val items: List<T>,
    val hasMore: Boolean,
)

internal suspend fun <T> loadAllRemoteOptions(
    maxPages: Int = DEFAULT_MAX_REMOTE_OPTION_PAGES,
    loader: suspend (page: Int) -> RemoteOptionsPage<T>,
): List<T> {
    require(maxPages > 0) { "maxPages must be positive" }
    val options = mutableListOf<T>()
    for (page in 1..maxPages) {
        val result = loader(page)
        options += result.items
        if (!result.hasMore) break
    }
    return options
}

internal suspend fun <T> loadRemoteOptions(
    loader: suspend () -> List<T>
): Result<List<T>> = try {
    Result.success(loader())
} catch (error: CancellationException) {
    throw error
} catch (error: Throwable) {
    Result.failure(error)
}

private const val DEFAULT_MAX_REMOTE_OPTION_PAGES = 20
