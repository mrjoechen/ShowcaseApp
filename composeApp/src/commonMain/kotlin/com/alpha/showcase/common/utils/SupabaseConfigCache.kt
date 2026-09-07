package com.alpha.showcase.common.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** In-memory configuration cache, isolated by authenticated user and configuration category. */
internal class SupabaseConfigCache(
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val allowStaleOnFailure: (Exception) -> Boolean = { true },
    private val onFailure: () -> Unit = {},
) {
    private data class Entry(val value: String?, val timestamp: Long, val failed: Boolean)

    private val mutex = Mutex()
    private var ownerId: String? = null
    private val entries = mutableMapOf<String, Entry>()
    private var lastFailureLoggedAt: Long? = null

    suspend fun getValue(
        userId: String,
        key: String,
        forceRefresh: Boolean = false,
        load: suspend () -> String?,
    ): String? = mutex.withLock {
        if (ownerId != userId) {
            entries.clear()
            ownerId = userId
        }
        val cached = entries[key]
        val now = nowMillis()
        val ttl = if (cached?.failed == true) 30_000L else 5 * 60_000L
        if (!forceRefresh && cached != null && now - cached.timestamp in 0..ttl) {
            return@withLock cached.value
        }

        try {
            load().also { entries[key] = Entry(it, nowMillis(), failed = false) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val fallback = cached?.value?.takeIf { allowStaleOnFailure(error) }
            val failedAt = nowMillis()
            entries[key] = Entry(fallback, failedAt, failed = true)
            if (lastFailureLoggedAt == null || failedAt - lastFailureLoggedAt!! >= 60_000L) {
                lastFailureLoggedAt = failedAt
                onFailure()
            }
            fallback
        }
    }

    suspend fun clear() = mutex.withLock {
        entries.clear()
        ownerId = null
    }
}
