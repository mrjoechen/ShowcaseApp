package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.repo.CachedSourceInfo
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

internal class PagedSourceSession(initial: CachedSourceInfo) {
    private val stateLock = SynchronizedObject()

    @Volatile
    var info: CachedSourceInfo = initial
        private set

    @Volatile
    var onPinLost: ((PagedSourceRecovery) -> Unit)? = null

    private var pendingRecovery: PagedSourceRecovery? = null

    fun candidateFor(version: Long): CachedSourceInfo = synchronized(stateLock) {
        info.copy(
            committedSyncVersion = version,
            initialSnapshot = false,
        )
    }

    /**
     * Install one pending recovery for the exact pinned object. Generation identity
     * determines whether recovery is needed: a different live version must recover
     * even if legacy data makes its numeric value lower than the pin. Once staged,
     * monotonic newly-allocated versions still let newer pending leases replace
     * older ones. The caller invokes [onPinLost] only after this lock is released.
     */
    fun stageRecovery(
        pinned: CachedSourceInfo,
        liveVersion: Long,
    ): PagedSourceRecovery? = synchronized(stateLock) {
        if (info !== pinned) return@synchronized null
        val pinnedVersion = pinned.committedSyncVersion ?: return@synchronized null
        if (liveVersion == pinnedVersion) return@synchronized null

        val pending = pendingRecovery
        if (
            pending != null &&
            pending.pinned === pinned &&
            liveVersion <= pending.liveVersion
        ) {
            return@synchronized null
        }

        PagedSourceRecovery(
            pinned = pinned,
            liveVersion = liveVersion,
            candidate = pinned.copy(
                committedSyncVersion = liveVersion,
                initialSnapshot = false,
            ),
        ).also { pendingRecovery = it }
    }

    fun isCurrent(recovery: PagedSourceRecovery): Boolean = synchronized(stateLock) {
        pendingRecovery === recovery && info === recovery.pinned
    }

    /** Commit only the still-current recovery lease; stale leases are rejected. */
    fun commitRecovery(recovery: PagedSourceRecovery): Boolean = synchronized(stateLock) {
        if (pendingRecovery !== recovery || info !== recovery.pinned) {
            return@synchronized false
        }
        info = recovery.candidate
        pendingRecovery = null
        true
    }

    /** Exact-lease cleanup: an older completion can never clear a newer marker. */
    fun abandonRecovery(recovery: PagedSourceRecovery) {
        synchronized(stateLock) {
            if (pendingRecovery === recovery) pendingRecovery = null
        }
    }

    /**
     * Commit a non-recovery candidate only while the exact object it was derived
     * from remains pinned and no newer recovery for that generation is pending.
     * Advancing the pin invalidates that generation's equal/older recovery marker.
     */
    fun commitCandidate(
        expectedPinned: CachedSourceInfo,
        candidate: CachedSourceInfo,
    ): Boolean = synchronized(stateLock) {
        if (info !== expectedPinned) return@synchronized false

        val pinned = expectedPinned
        val pending = pendingRecovery
        val candidateVersion = candidate.committedSyncVersion
        if (
            pending != null &&
            pending.pinned === pinned &&
            (candidateVersion == null || candidateVersion < pending.liveVersion)
        ) {
            return@synchronized false
        }

        info = candidate
        if (pending?.pinned === pinned) pendingRecovery = null
        true
    }
}

internal class PagedSourceRecovery internal constructor(
    internal val pinned: CachedSourceInfo,
    internal val liveVersion: Long,
    val candidate: CachedSourceInfo,
)

internal class PinnedSourceVersionExpiredException(
    cause: Throwable? = null,
) : Exception("Pinned cache generation was purged", cause)

internal suspend fun <T> loadPinnedPageOrStageRecovery(
    session: PagedSourceSession,
    loadPinned: suspend (CachedSourceInfo) -> List<T>,
    resolveLiveVersion: suspend (CachedSourceInfo) -> Long?,
): List<T> {
    val pinned = session.info
    val page = loadPinned(pinned)
    if (page.isEmpty() && pinned.committedSyncVersion != null) {
        val liveVersion = resolveLiveVersion(pinned)
        if (liveVersion != null && liveVersion != pinned.committedSyncVersion) {
            session.stageRecovery(pinned, liveVersion)?.let { recovery ->
                val callback = session.onPinLost
                if (callback == null) {
                    session.abandonRecovery(recovery)
                } else {
                    try {
                        callback(recovery)
                    } catch (e: CancellationException) {
                        session.abandonRecovery(recovery)
                        throw e
                    } catch (failure: Throwable) {
                        session.abandonRecovery(recovery)
                        throw PinnedSourceVersionExpiredException(failure)
                    }
                }
            }
            throw PinnedSourceVersionExpiredException()
        }
    }
    return page
}
