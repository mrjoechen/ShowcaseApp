package com.alpha.showcase.common

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Exclusive leases: no network call or wait holds the monitor needed to return a lease. */
internal class NetworkConnectionPool<T : Any>(
    maxConnections: Int,
    private val create: () -> T,
    private val valid: (T) -> Boolean,
    private val destroy: (T) -> Unit,
) {
    private data class Idle<T>(val value: T, val since: Long)
    private val permits = Semaphore(maxConnections)
    private val lock = Any()
    private val all = mutableSetOf<T>()
    private val idle = ArrayDeque<Idle<T>>()
    private var closed = false

    inner class Lease(val value: T) {
        private val returned = AtomicBoolean()
        fun release(reusable: Boolean = true) {
            if (!returned.compareAndSet(false, true)) return
            try {
                val keep = synchronized(lock) {
                    if (reusable && !closed) {
                        idle.addLast(Idle(value, System.currentTimeMillis()))
                        true
                    } else { all.remove(value); false }
                }
                if (!keep) runCatching { destroy(value) }
            } finally { permits.release() }
        }
    }

    suspend fun borrow(): Lease {
        var acquired = false
        try {
            withTimeout(60_000) { permits.acquire(); acquired = true }
        } catch (failure: Throwable) {
            if (acquired) permits.release()
            throw failure
        }
        var value: T? = null
        try {
            while (value == null) {
                currentCoroutineContext().ensureActive()
                val candidate = synchronized(lock) {
                    check(!closed) { "Connection pool closed" }
                    idle.removeFirstOrNull()?.value
                }
                if (candidate == null) {
                    val created = create()
                    value = created
                    synchronized(lock) {
                        check(!closed) { "Connection pool closed" }
                        all.add(created)
                    }
                } else if (runCatching { valid(candidate) }.getOrDefault(false)) {
                    value = candidate
                } else {
                    synchronized(lock) { all.remove(candidate) }
                    runCatching { destroy(candidate) }
                }
            }
            currentCoroutineContext().ensureActive()
            return synchronized(lock) {
                check(!closed) { "Connection pool closed" }
                Lease(value)
            }
        } catch (failure: Throwable) {
            value?.let { item -> synchronized(lock) { all.remove(item) }; runCatching { destroy(item) } }
            permits.release()
            throw failure
        }
    }

    fun cleanupIdle(now: Long, idleMillis: Long) {
        val expired = synchronized(lock) {
            val expired = idle.filter { now - it.since >= idleMillis }
            idle.removeAll(expired.toSet())
            expired.map { it.value }.also { all.removeAll(it.toSet()) }
        }
        // Never probe a leased FTP control connection while its data transfer is in progress.
        expired.forEach { runCatching { destroy(it) } }
    }

    fun size(): Int = synchronized(lock) { all.size }
    fun close() {
        val values = synchronized(lock) {
            closed = true
            // Active owners finish/close their own streams. Destroying their connection here
            // would race reads, share creation and their eventual release.
            idle.map { it.value }.also { all.removeAll(it.toSet()); idle.clear() }
        }
        values.forEach { runCatching { destroy(it) } }
    }
}

/** Close exactly once and release ownership even when a read/close/transfer reply fails. */
internal class ReleasingInputStream(
    input: InputStream,
    private val release: (healthy: Boolean) -> Unit,
) : FilterInputStream(input) {
    private val closed = AtomicBoolean()
    private var healthy = true
    override fun read(): Int = checked { `in`.read() }
    override fun read(b: ByteArray, off: Int, len: Int): Int = checked { `in`.read(b, off, len) }
    private inline fun <T> checked(block: () -> T): T = try { block() } catch (failure: Throwable) {
        healthy = false
        throw failure
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        var failure: Throwable? = null
        try { `in`.close() } catch (error: Throwable) { healthy = false; failure = error }
        try { release(healthy) } catch (error: Throwable) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
        failure?.let { throw it }
    }
}
