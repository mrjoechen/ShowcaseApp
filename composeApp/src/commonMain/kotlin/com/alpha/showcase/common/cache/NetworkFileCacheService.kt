@file:OptIn(ExperimentalTime::class)

package com.alpha.showcase.common.cache

import com.alpha.showcase.common.cache.entity.CacheMetadata
import com.alpha.showcase.common.cache.entity.CachedItem
import com.alpha.showcase.common.cache.entity.resolveCachedItemMediaKind
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.getType
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RssSource
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
import com.alpha.showcase.common.repo.BatchSourceRepository
import androidx.room3.withWriteTransaction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format.DateTimeComponents
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val DEFAULT_SYNC_BATCH_SIZE = 200
private const val REFRESH_RETRY_DELAY_MS = 5 * 60 * 1000L
private const val MIN_READY_COUNT = 50
private const val EARLY_RETURN_POLL_INTERVAL_MS = 300L
private const val EARLY_RETURN_TIMEOUT_MS = 30_000L

private class CacheSyncGenerationExhaustedException :
    IllegalStateException("Cache sync generation exhausted at Long.MAX_VALUE")

sealed class CacheSyncStatus {
    abstract val syncId: Long
    abstract val sourceKey: String

    data class Syncing(
        override val syncId: Long,
        override val sourceKey: String,
        val itemsSoFar: Int,
    ) : CacheSyncStatus()

    data class Completed(
        override val syncId: Long,
        override val sourceKey: String,
        val totalItems: Int,
    ) : CacheSyncStatus()

    data class Failed(
        override val syncId: Long,
        override val sourceKey: String,
        val error: Throwable,
    ) : CacheSyncStatus()
}

/**
 * Terminal result of a single sync run, delivered through the per-sync
 * [CompletableDeferred] returned in [CacheReadyResult.completion]. Unlike the
 * shared [NetworkFileCacheService.syncStatus] flow (progress only), this handle
 * belongs to exactly one sync, so a consumer can never observe another source's
 * — or a stale run's — terminal event.
 */
sealed class CacheSyncResult {
    data class Completed(val totalItems: Int) : CacheSyncResult()
    data class Failed(val error: Throwable) : CacheSyncResult()
    /** No background sync was started (cache already fresh). */
    object AlreadyFresh : CacheSyncResult()
}

/**
 * Returned by [NetworkFileCacheService.ensureCacheReady]. [completion] resolves
 * when THIS sync finishes (or immediately as [CacheSyncResult.AlreadyFresh] when
 * no sync was needed). Await it instead of racing the shared status flow.
 *
 * [pending] is true when the result was returned while the first sync is STILL
 * running and no media is available yet (slow source / large directory). The
 * caller should show a loading state and recover via [completion] when data
 * arrives, rather than treating the empty cache as a hard error.
 */
data class CacheReadyResult(
    val sourceType: String,
    val sourceKey: String,
    val completion: CompletableDeferred<CacheSyncResult>,
    val pending: Boolean = false,
    // The sync_version this read session should pin to (null when not yet
    // available, e.g. a still-pending first sync).
    val committedSyncVersion: Long? = null,
    // True when [committedSyncVersion] is an in-flight FIRST sync's version rather
    // than a committed one: the rows are still growing, so paged reads must use
    // append-stable insertion order (see CachedItemDao.getImagesPagedByIdAscVersion)
    // instead of a sort whose OFFSET windows shift as rows arrive.
    val initialSnapshot: Boolean = false,
)

class NetworkFileCacheService(
    private val database: SourceCacheDatabase = SourceCacheDatabaseProvider.database
) {

    companion object {
        /**
         * Process-wide instance for the default database. The per-source sync
         * mutual exclusion ([inFlightRuns]) is instance state, so every consumer
         * of the default DB MUST share this instance —
         * two services syncing the same source would delete each other's version
         * rows and commit incomplete data. Tests construct private instances on
         * their own in-memory databases, which keeps them isolated by design.
         */
        val shared: NetworkFileCacheService by lazy { NetworkFileCacheService() }
    }

    private val itemDao = database.cachedItemDao()
    private val metadataDao = database.cacheMetadataDao()

    private val refreshJob = SupervisorJob()
    private val refreshScope = CoroutineScope(refreshJob + Dispatchers.Default)
    private val refreshLock = Mutex()

    /**
     * Stops this instance's fire-and-forget refreshes and waits for their
     * non-cancellable database settlement before its database is closed.
     * Production uses [shared] for process lifetime; private test instances call
     * this from teardown.
     */
    internal suspend fun shutdownBackgroundRefreshes() {
        refreshJob.cancelAndJoin()
    }

    /**
     * Identity of one sync generation. Completion and active version must travel
     * together: after C1 finishes and is removed from [inFlightRuns], C2 may be
     * registered immediately for the same source key. A caller still settling C1
     * must keep reading C1's version, never look C2 up by key and pair it with
     * C1's terminal result.
     *
     * [activeVersion] is guarded by [refreshLock]. The value intentionally stays
     * on this record after registry removal so delayed C1 callers retain their
     * generation identity through the final pin read.
     */
    private class SyncRun(
        val completion: CompletableDeferred<CacheSyncResult> = CompletableDeferred(),
        var activeVersion: Long? = null,
        // Set when a non-forced owner discovers that another run committed fresh
        // data after this caller's initial metadata read but before acquisition.
        // First-sync waiters then pin that committed generation instead of
        // interpreting an AlreadyFresh run with no active version as a failure.
        var reusedCommittedVersion: Long? = null,
        // Any forceRefresh joiner upgrades the shared run before the owner decides
        // whether it may settle as AlreadyFresh. Guarded by refreshLock.
        var forceRequested: Boolean = false,
        // Marks the atomic no-op decision. A late forced joiner replaces this run
        // in the registry instead of joining an AlreadyFresh completion.
        var alreadyFreshSettled: Boolean = false,
    )

    private val inFlightRuns = mutableMapOf<String, SyncRun>()

    /**
     * Test-only suspension point immediately before a caller enters the refresh
     * registry. It makes the metadata-read -> registry-acquire TOCTOU window
     * deterministic in concurrency tests; production code always leaves it null.
     */
    internal var beforeRefreshAcquireForTest: (suspend () -> Unit)? = null

    /** Test-only pause after a fresh owner recheck but before it settles no-op. */
    internal var beforeAlreadyFreshSettlementForTest: (suspend () -> Unit)? = null

    /** Test-only pause after no-op settlement but before the owner's final release. */
    internal var afterAlreadyFreshSettlementForTest: (suspend () -> Unit)? = null

    /** Test-only signal/pause immediately after registry acquisition or join. */
    internal var afterRefreshAcquireForTest: (suspend () -> Unit)? = null

    /** Test-only pause before a sync version is bound to its run identity. */
    internal var beforeSyncVersionBindForTest: (suspend () -> Unit)? = null

    /** Test-only pause after the first-sync readiness path snapshots its run. */
    internal var afterReadyVersionSnapshotForTest:
        (suspend (Long?, CompletableDeferred<CacheSyncResult>) -> Unit)? = null

    private val _syncStatus = MutableSharedFlow<CacheSyncStatus>(replay = 1, extraBufferCapacity = 8)
    val syncStatus: SharedFlow<CacheSyncStatus> = _syncStatus.asSharedFlow()

    // Process-wide CAS keeps concurrent different-source runs unique. Before each
    // run the counter is also raised above that source's persistent floor, so a
    // restart or wall-clock rollback can never reuse an existing DB generation.
    // Used both as the DB sync_version and the CacheSyncStatus syncId.
    private val syncIdCounter = atomic(currentTimeMillis())

    private fun nextSyncId(persistedFloor: Long): Long {
        while (true) {
            val observed = syncIdCounter.value
            val floor = maxOf(observed, currentTimeMillis(), persistedFloor)
            if (floor == Long.MAX_VALUE) throw CacheSyncGenerationExhaustedException()
            val next = floor + 1
            if (syncIdCounter.compareAndSet(observed, next)) return next
        }
    }

    private val metadataJson = Json {
        ignoreUnknownKeys = true
    }

    /**
     * Runs [block] inside a single writer transaction: either every statement in it
     * commits, or none do. DAO calls made within the block join the transaction via
     * the coroutine context. The cache-switch steps (metadata commit + old-version
     * cleanup + access-time touch) must be atomic — individually they can leave
     * metadata pointing at deleted rows, or trip the catch-side rollback into
     * deleting an already-committed new version.
     */
    private suspend fun <R> withCacheTransaction(block: suspend () -> R): R =
        database.withWriteTransaction { block() }

    suspend fun <T : RemoteApi> getOrLoad(
        remoteApi: T,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?,
        repository: BatchSourceRepository<T, NetworkFile>,
        forceRefresh: Boolean = false,
    ): Result<List<NetworkFile>> {
        val serializedSource = StorageSourceSerializer.sourceJson.encodeToString(
            RemoteApi.serializer(),
            remoteApi
        )
        val sourceType = resolveSourceType(remoteApi)
        val sourceKey = buildSourceKey(serializedSource, recursive)
        val configHash = buildConfigHash(serializedSource)
        val policy = resolvePolicy(remoteApi, recursive)

        val metadata = metadataDao.getBySource(sourceType, sourceKey)
        // Do not materialize abandoned/in-flight generations. A caller can only
        // serve the exact committed version named by displayable metadata, and a
        // forced refresh does not need an eager snapshot at all (terminal fallback
        // is re-read after the sync).
        val initialCommittedVersion = metadata
            ?.takeIf { it.hasDisplayableCache() }
            ?.committedSyncVersion
            ?.takeIf { it > 0 }
        val now = currentTimeMillis()

        if (!forceRefresh) {
            val cacheFresh = isCacheFresh(metadata, configHash, now)
            if (cacheFresh) {
                itemDao.updateLastAccessed(sourceType, sourceKey, now)
                val freshFiles = initialCommittedVersion?.let { version ->
                    loadVersionFiles(remoteApi, sourceType, sourceKey, version)
                } ?: emptyList()
                return Result.success(applyFilter(freshFiles, filter))
            }

            // "Known empty" requires a COMMITTED terminal sync that found 0 items;
            // an UPDATING first sync also has totalItems == 0 but proves nothing.
            val knownEmptyCache = metadata != null && metadata.hasDisplayableCache() && metadata.totalItems == 0
            // EXISTS preserves the old corruption guard (metadata says non-empty
            // but its committed rows are gone) while stopping at the first indexed
            // row and avoiding any NetworkFile conversion before registry choice.
            val hasDisplayableRows = initialCommittedVersion?.let { version ->
                !knownEmptyCache && itemDao.hasAnyBySourceVersion(sourceType, sourceKey, version)
            } == true
            if (hasDisplayableRows || knownEmptyCache) {
                itemDao.updateLastAccessed(sourceType, sourceKey, now)
                launchBackgroundRefresh(
                    remoteApi = remoteApi,
                    recursive = recursive,
                    sourceType = sourceType,
                    sourceKey = sourceKey,
                    configHash = configHash,
                    policy = policy,
                    repository = repository,
                )
                // Another run may have committed between the metadata snapshot and
                // registry acquisition. Resolve the final visible generation first,
                // then materialize exactly ONE list. Loading the old generation
                // before this decision caused a transient ~2N object peak whenever
                // a large cache switched versions in this window.
                val visibleVersion = resolveCommittedSyncVersion(sourceType, sourceKey)
                    ?: initialCommittedVersion
                val visibleFiles = visibleVersion?.let { version ->
                    loadVersionFiles(remoteApi, sourceType, sourceKey, version)
                } ?: emptyList()
                return Result.success(applyFilter(visibleFiles, filter))
            }
        }

        val syncResult = refreshNow(
            remoteApi = remoteApi,
            recursive = recursive,
            sourceType = sourceType,
            sourceKey = sourceKey,
            configHash = configHash,
            policy = policy,
            repository = repository,
            collectResult = true,
            filter = filter,
            forceRefresh = forceRefresh,
        )

        if (syncResult.isSuccess) {
            return syncResult
        }

        // The sync may have committed a partial result, or another joined run may
        // have advanced the committed generation while this caller was waiting.
        // Re-read terminal fallback state instead of using the pre-sync snapshot.
        val fallbackFiles = loadCommittedFiles(remoteApi, sourceType, sourceKey)
        if (fallbackFiles.isNotEmpty()) {
            return Result.success(applyFilter(fallbackFiles, filter))
        }

        val fallbackMetadata = metadataDao.getBySource(sourceType, sourceKey)
        val knownEmptyCache = fallbackMetadata != null &&
            fallbackMetadata.hasDisplayableCache() && fallbackMetadata.totalItems == 0
        if (knownEmptyCache) {
            return Result.success(emptyList())
        }

        return Result.failure(syncResult.exceptionOrNull() ?: Exception("Cache sync failed"))
    }

    private suspend fun <T : RemoteApi> refreshNow(
        remoteApi: T,
        recursive: Boolean,
        sourceType: String,
        sourceKey: String,
        configHash: String,
        policy: CachePolicy,
        repository: BatchSourceRepository<T, NetworkFile>,
        collectResult: Boolean,
        filter: ((NetworkFile) -> Boolean)?,
        forceRefresh: Boolean,
    ): Result<List<NetworkFile>> {
        // Use the same run registry as the paged path. A non-owner must await the
        // real terminal result: returning the current DB snapshot here can produce
        // a fake empty success before a first sync has inserted its first batch.
        beforeRefreshAcquireForTest?.invoke()
        val (run, isOwner) = acquireRun(sourceKey, forceRefresh)
        afterRefreshAcquireForTest?.invoke()
        if (!isOwner) {
            return awaitJoinedRefresh(remoteApi, sourceType, sourceKey, filter, run)
        }

        return try {
            if (completeAlreadyFreshIfApplicable(
                    sourceType = sourceType,
                    sourceKey = sourceKey,
                    configHash = configHash,
                    run = run,
                )
            ) {
                val committedVersion = refreshLock.withLock { run.reusedCommittedVersion }
                val files = committedVersion?.let { version ->
                    loadVersionFiles(remoteApi, sourceType, sourceKey, version)
                } ?: emptyList()
                Result.success(applyFilter(files, filter))
            } else {
                syncCache(
                    remoteApi = remoteApi,
                    recursive = recursive,
                    sourceType = sourceType,
                    sourceKey = sourceKey,
                    configHash = configHash,
                    policy = policy,
                    repository = repository,
                    collectResult = collectResult,
                    filter = filter,
                    run = run,
                )
            }
        } finally {
            releaseRun(sourceKey, run)
        }
    }

    private suspend fun acquireRun(
        sourceKey: String,
        forceRefresh: Boolean,
    ): Pair<SyncRun, Boolean> =
        refreshLock.withLock {
            val existing = inFlightRuns[sourceKey]
            if (existing != null) {
                if (forceRefresh && existing.alreadyFreshSettled) {
                    // The previous owner has atomically decided to perform no I/O.
                    // Replace its still-cleaning registry entry so a force request
                    // arriving in this narrow window starts a real successor run.
                    val forced = SyncRun(forceRequested = true)
                    inFlightRuns[sourceKey] = forced
                    forced to true
                } else {
                    if (forceRefresh) existing.forceRequested = true
                    existing to false
                }
            } else {
                val fresh = SyncRun(forceRequested = forceRefresh)
                inFlightRuns[sourceKey] = fresh
                fresh to true
            }
        }

    private suspend fun releaseRun(sourceKey: String, run: SyncRun) {
        // Owner cancellation must not strand a completed run in the registry. A
        // contended Mutex acquisition is cancellable, so perform terminal resolve
        // and identity-checked removal in a non-cancellable cleanup section.
        withContext(NonCancellable) {
            // Resolve before exposing an empty registry slot, so a new C2 cannot be
            // acquired while C1 joiners still have an unresolved terminal handle.
            if (!run.completion.isCompleted) {
                run.completion.complete(CacheSyncResult.Failed(Exception("Sync ended without result")))
            }
            refreshLock.withLock {
                if (inFlightRuns[sourceKey] === run) {
                    inFlightRuns.remove(sourceKey)
                }
            }
        }
    }

    /**
     * Closes the metadata-read -> registry-acquire TOCTOU window. Only the caller
     * that actually became owner performs this second check, and forced refreshes
     * deliberately bypass it. A fresh result is recorded on the run before its
     * completion resolves so every joiner can pin the same committed generation.
     */
    private suspend fun completeAlreadyFreshIfApplicable(
        sourceType: String,
        sourceKey: String,
        configHash: String,
        run: SyncRun,
    ): Boolean {
        if (refreshLock.withLock { run.forceRequested }) return false

        val checkedAt = currentTimeMillis()
        val latestMetadata = metadataDao.getBySource(sourceType, sourceKey)
        if (!isCacheFresh(latestMetadata, configHash, checkedAt)) return false

        val committedVersion = latestMetadata?.committedSyncVersion?.takeIf { it > 0 }
            ?: return false
        beforeAlreadyFreshSettlementForTest?.invoke()
        itemDao.updateLastAccessed(sourceType, sourceKey, checkedAt)

        // Re-check and mark the no-op decision under the SAME registry lock used
        // by joiners. A force request can neither slip between this check and the
        // marker nor join the completed no-op: late force callers replace it in
        // acquireRun above and become owner of a successor run.
        val settledAlreadyFresh = refreshLock.withLock {
            if (run.forceRequested) {
                false
            } else {
                run.reusedCommittedVersion = committedVersion
                run.alreadyFreshSettled = true
                true
            }
        }
        if (!settledAlreadyFresh) return false

        run.completion.complete(CacheSyncResult.AlreadyFresh)
        afterAlreadyFreshSettlementForTest?.invoke()
        return true
    }

    private suspend fun awaitJoinedRefresh(
        remoteApi: RemoteApi,
        sourceType: String,
        sourceKey: String,
        filter: ((NetworkFile) -> Boolean)?,
        run: SyncRun,
    ): Result<List<NetworkFile>> {
        val terminal = run.completion.await()
        // Only a committed generation is a terminal fallback. An unversioned read
        // after failed C1 could otherwise consume C2's newly-active partial rows,
        // recreating the same ABA mismatch outside ensureCacheReady.
        val fallback = loadCommittedFiles(remoteApi, sourceType, sourceKey)
        val metadata = metadataDao.getBySource(sourceType, sourceKey)
        val knownEmptyCache = metadata != null &&
            metadata.hasDisplayableCache() && metadata.totalItems == 0

        return when {
            terminal is CacheSyncResult.Completed ->
                Result.success(applyFilter(fallback, filter))
            fallback.isNotEmpty() || knownEmptyCache ->
                Result.success(applyFilter(fallback, filter))
            terminal is CacheSyncResult.Failed -> Result.failure(terminal.error)
            else -> Result.success(applyFilter(fallback, filter))
        }
    }

    /**
     * Fire-and-forget background refresh (callers that don't need the result, e.g.
     * stale-but-present cache). Joins an in-flight sync for the same source.
     */
    private suspend fun <T : RemoteApi> launchBackgroundRefresh(
        remoteApi: T,
        recursive: Boolean,
        sourceType: String,
        sourceKey: String,
        configHash: String,
        policy: CachePolicy,
        repository: BatchSourceRepository<T, NetworkFile>,
    ) {
        // startOrJoinRefresh only performs registry acquisition synchronously; the
        // actual traversal still runs in refreshScope. Avoiding another outer launch
        // lets getOrLoad re-check which committed generation is current after this
        // decision without waiting for network I/O.
        startOrJoinRefresh(
            remoteApi = remoteApi,
            recursive = recursive,
            sourceType = sourceType,
            sourceKey = sourceKey,
            configHash = configHash,
            policy = policy,
            repository = repository,
            forceRefresh = false,
        )
    }

    /**
     * Returns the run record of the refresh for [sourceKey]: either the one already
     * in flight (so this caller observes the REAL running sync), or a new one whose
     * sync this call starts. Guarded by [refreshLock] so the
     * check-and-register is atomic — two concurrent same-source callers always end
     * up sharing exactly one handle and one sync.
     */
    private suspend fun <T : RemoteApi> startOrJoinRefresh(
        remoteApi: T,
        recursive: Boolean,
        sourceType: String,
        sourceKey: String,
        configHash: String,
        policy: CachePolicy,
        repository: BatchSourceRepository<T, NetworkFile>,
        forceRefresh: Boolean,
    ): SyncRun {
        beforeRefreshAcquireForTest?.invoke()
        val (run, isOwner) = acquireRun(sourceKey, forceRefresh)
        afterRefreshAcquireForTest?.invoke()

        if (!isOwner) return run

        refreshScope.launch {
            try {
                if (!completeAlreadyFreshIfApplicable(
                        sourceType = sourceType,
                        sourceKey = sourceKey,
                        configHash = configHash,
                        run = run,
                    )
                ) {
                    syncCache(
                        remoteApi = remoteApi,
                        recursive = recursive,
                        sourceType = sourceType,
                        sourceKey = sourceKey,
                        configHash = configHash,
                        policy = policy,
                        repository = repository,
                        collectResult = false,
                        filter = null,
                        run = run,
                    )
                }
            } catch (e: Exception) {
                // syncCache normally resolves completion itself; this is a safety net.
                val terminalError = if (e is CancellationException) {
                    Exception("Sync cancelled before completion", e)
                } else {
                    e
                }
                run.completion.complete(CacheSyncResult.Failed(terminalError))
                if (e is CancellationException) throw e
            } finally {
                releaseRun(sourceKey, run)
            }
        }

        return run
    }

    private suspend fun <T : RemoteApi> syncCache(
        remoteApi: T,
        recursive: Boolean,
        sourceType: String,
        sourceKey: String,
        configHash: String,
        policy: CachePolicy,
        repository: BatchSourceRepository<T, NetworkFile>,
        collectResult: Boolean,
        filter: ((NetworkFile) -> Boolean)?,
        run: SyncRun,
    ): Result<List<NetworkFile>> {
        val existingMetadata = metadataDao.getBySource(sourceType, sourceKey)
        val syncVersion = try {
            // Metadata covers committed-empty generations that have no item rows;
            // MAX(cached_items) additionally covers grace, abandoned and partial
            // generations. Both are required: flooring from metadata alone can
            // reuse a higher abandoned version and make insertOrIgnore mix rows.
            val persistedFloor = maxOf(
                existingMetadata?.committedSyncVersion ?: 0L,
                itemDao.maxSyncVersionBySource(sourceType, sourceKey) ?: 0L,
            )
            // Allocate before mutating metadata or traversing the source.
            nextSyncId(persistedFloor)
        } catch (cancellation: CancellationException) {
            // Preserve structured cancellation. The owner path's existing outer
            // settlement resolves joiners and removes the run registry entry.
            throw cancellation
        } catch (failure: Exception) {
            // Preflight failures have no version to roll back. Resolve this exact
            // run explicitly so background callers and joiners cannot hang or be
            // downgraded to releaseRun's generic safety-net error.
            run.completion.complete(CacheSyncResult.Failed(failure))
            return Result.failure(failure)
        }
        val syncId = syncVersion
        val startedAt = currentTimeMillis()

        // Bind the version to this exact completion/run identity. It deliberately
        // remains readable from the run record after registry cleanup.
        beforeSyncVersionBindForTest?.invoke()
        refreshLock.withLock { run.activeVersion = syncVersion }

        metadataDao.insertOrReplace(
            buildMetadata(
                existing = existingMetadata,
                sourceType = sourceType,
                sourceKey = sourceKey,
                lastUpdated = startedAt,
                nextUpdateTime = startedAt,
                totalItems = existingMetadata?.totalItems ?: 0,
                policy = policy,
                recursive = recursive,
                status = CacheMetadata.STATUS_UPDATING,
                configHash = configHash,
                version = existingMetadata?.version,
                // STATUS_UPDATING itself is considered usable so a normal stale
                // committed generation stays visible during refresh. Therefore an
                // INVALID row's historical marker must be cleared at this exact
                // transition; otherwise changing INVALID -> UPDATING would
                // accidentally resurrect its abandoned rows for concurrent callers
                // (and after a process death in this window).
                committedSyncVersion = existingMetadata
                    ?.takeIf { it.hasDisplayableCache() }
                    ?.committedSyncVersion
                    ?: 0L,
            )
        )

        val collected = mutableListOf<NetworkFile>()
        var totalItems = 0

        return try {
            val streamResult = repository.streamItems(
                remoteApi = remoteApi,
                recursive = recursive,
                filter = null,
                batchSize = DEFAULT_SYNC_BATCH_SIZE,
            ) { batch ->
                if (batch.isEmpty()) return@streamItems

                val now = currentTimeMillis()
                val cacheBatch = batch.map {
                    it.toCachedItem(
                        sourceType = sourceType,
                        sourceKey = sourceKey,
                        isRecursive = recursive,
                        syncVersion = syncVersion,
                        now = now,
                        metadataJson = metadataJson
                    )
                }
                val insertResults = itemDao.insertOrIgnore(cacheBatch)
                val insertedItems = batch.zip(insertResults).mapNotNull { (item, rowId) ->
                    item.takeIf { rowId != -1L }
                }
                // Count only AFTER the insert succeeded: if it throws, the failure
                // path must not treat never-persisted rows as a committable partial
                // result (totalItems > 0 with an empty version).
                totalItems += insertedItems.size

                _syncStatus.tryEmit(CacheSyncStatus.Syncing(syncId, sourceKey, totalItems))

                if (collectResult) {
                    if (filter == null) {
                        collected.addAll(insertedItems)
                    } else {
                        collected.addAll(insertedItems.filter(filter))
                    }
                }
            }

            if (streamResult.isFailure) {
                throw streamResult.exceptionOrNull() ?: Exception("Stream items failed")
            }

            // Atomic switch, in ONE transaction: commit the new version in metadata,
            // purge versions older than the previous committed one, and touch the
            // access time. Transactionality matters twice over: a reader can never
            // observe metadata pointing at half-deleted rows, and a failure in any
            // step rolls the whole switch back — so the catch below (which deletes
            // THIS run's rows) can never destroy an already-committed new version.
            //
            // The PREVIOUS committed version's rows are deliberately RETAINED as a
            // grace copy: read sessions opened before this sync (including
            // AlreadyFresh sessions that never observe future syncs) stay pinned to
            // it and would otherwise page into emptiness the moment we delete it.
            // It is purged when the NEXT sync commits.
            val finishedAt = currentTimeMillis()
            val graceVersion = existingMetadata?.committedSyncVersion ?: 0L
            withCacheTransaction {
                metadataDao.insertOrReplace(
                    buildMetadata(
                        existing = metadataDao.getBySource(sourceType, sourceKey),
                        sourceType = sourceType,
                        sourceKey = sourceKey,
                        lastUpdated = finishedAt,
                        nextUpdateTime = finishedAt + policy.ttlMillis,
                        totalItems = totalItems,
                        policy = policy,
                        recursive = recursive,
                        status = CacheMetadata.STATUS_VALID,
                        configHash = configHash,
                        version = existingMetadata?.version,
                        // Sync succeeded: this version is now the committed one that
                        // paged reads should pin to.
                        committedSyncVersion = syncVersion,
                    )
                )
                itemDao.deleteBySourceExceptVersions(sourceType, sourceKey, syncVersion, graceVersion)
                itemDao.updateLastAccessed(sourceType, sourceKey, finishedAt)
            }

            _syncStatus.tryEmit(CacheSyncStatus.Completed(syncId, sourceKey, totalItems))
            run.completion.complete(CacheSyncResult.Completed(totalItems))

            Result.success(if (collectResult) collected else emptyList())
        } catch (e: Exception) {
            e.printStackTrace()
            // Cancellation is allowed to stop traversal, but not the atomic DB
            // settlement that decides which version survives. Otherwise a cancel
            // during a suspended DAO call can leave UPDATING metadata and orphaned
            // partial rows that a later unversioned fallback would expose.
            withContext(NonCancellable) {
                val failedAt = currentTimeMillis()
                // Any complete committed generation outranks this run's partial
                // rows. A committed-but-empty cache (a successful sync that found
                // 0 items) may still yield to inserted partial rows so fetched data
                // survives and the observer can pick it up.
                val previousCommittedVersion = existingMetadata
                    ?.takeIf { it.hasDisplayableCache() }
                    ?.committedSyncVersion
                    ?: 0L
                val previousCommittedHasRows = previousCommittedVersion > 0L &&
                    itemDao.hasAnyBySourceVersion(
                        sourceType = sourceType,
                        sourceKey = sourceKey,
                        syncVersion = previousCommittedVersion,
                    )
                val thisRunHasRows = totalItems > 0
                val shouldCommitPartialResult = thisRunHasRows && !previousCommittedHasRows

                // Settle the DB in one transaction (rollback of this run's rows +
                // metadata rewrite are all-or-nothing), but never let a failing
                // cleanup escape this catch: the sync must still resolve its
                // completion as Failed below so observers reconcile and joiners
                // don't hang. The success path can only throw BEFORE its commit
                // transaction applies, so this cleanup never sees (and can never
                // delete) an already-committed new version.
                runCatching {
                    withCacheTransaction {
                        if (!shouldCommitPartialResult) {
                            // Existing committed cache wins over a partial failed
                            // refresh: clean up THIS run's rows and keep the old
                            // committed version pinned.
                            itemDao.deleteBySourceAndSyncVersion(sourceType, sourceKey, syncVersion)
                        }

                        val failedMetadata = metadataDao.getBySource(sourceType, sourceKey)
                        val committedVersion = if (shouldCommitPartialResult) {
                            // First sync produced inserted rows before a later page
                            // failed (for example API rate limit). Keep that partial
                            // result and schedule a retry soon.
                            syncVersion
                        } else {
                            previousCommittedVersion
                        }
                        val survivorCount = if (shouldCommitPartialResult) {
                            totalItems
                        } else {
                            existingMetadata
                                ?.takeIf { previousCommittedVersion > 0L }
                                ?.totalItems
                                ?: 0
                        }
                        metadataDao.insertOrReplace(
                            buildMetadata(
                                existing = failedMetadata,
                                sourceType = sourceType,
                                sourceKey = sourceKey,
                                lastUpdated = failedAt,
                                nextUpdateTime = failedAt + REFRESH_RETRY_DELAY_MS,
                                totalItems = survivorCount,
                                policy = policy,
                                recursive = recursive,
                                // A surviving committed generation keeps the cache
                                // VALID even when it is EMPTY (survivorCount == 0):
                                // flipping a legitimate committed-empty cache to
                                // INVALID on a failed refresh would destroy the
                                // offline "known empty" answer.
                                status = if (survivorCount > 0 || committedVersion > 0) {
                                    CacheMetadata.STATUS_VALID
                                } else {
                                    CacheMetadata.STATUS_INVALID
                                },
                                configHash = configHash,
                                version = failedMetadata?.version,
                                committedSyncVersion = committedVersion,
                            )
                        )
                    }
                }.onFailure { it.printStackTrace() }

                // Joiners must never receive a raw CancellationException as their
                // failure cause: the OWNER was cancelled, not the joiner, and a
                // cause that upstream code rethrows as cancellation would silently
                // kill an unrelated coroutine. Wrap it; the owner itself still
                // rethrows the original below.
                val terminalError = if (e is CancellationException) {
                    Exception("Sync cancelled before completion", e)
                } else {
                    e
                }
                _syncStatus.tryEmit(CacheSyncStatus.Failed(syncId, sourceKey, terminalError))
                run.completion.complete(CacheSyncResult.Failed(terminalError))
            }

            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private fun isCacheFresh(metadata: CacheMetadata?, configHash: String, now: Long): Boolean {
        if (metadata == null) return false
        // Displayable (committed) — not merely "not invalid": STATUS_UPDATING with
        // no committed version yet (first sync in progress) must never be served
        // as fresh cache.
        if (!metadata.hasDisplayableCache()) return false
        if (metadata.isSourceConfigChanged(configHash)) return false
        return metadata.nextUpdateTime > now
    }

    private suspend fun loadVersionFiles(
        remoteApi: RemoteApi,
        sourceType: String,
        sourceKey: String,
        syncVersion: Long,
    ): List<NetworkFile> {
        return itemDao.getBySourceVersion(sourceType, sourceKey, syncVersion)
            .map { it.toNetworkFile(remoteApi, metadataJson) }
    }

    /**
     * Loads terminal fallback rows only. This never reads an uncommitted active
     * generation when metadata has no committed version; that distinction prevents
     * a failed C1 joiner from consuming C2's partial rows after the source-key
     * registry slot has been reused.
     */
    private suspend fun loadCommittedFiles(
        remoteApi: RemoteApi,
        sourceType: String,
        sourceKey: String,
    ): List<NetworkFile> {
        val committed = resolveCommittedSyncVersion(sourceType, sourceKey) ?: return emptyList()
        return loadVersionFiles(remoteApi, sourceType, sourceKey, committed)
    }

    private fun applyFilter(
        files: List<NetworkFile>,
        filter: ((NetworkFile) -> Boolean)?
    ): List<NetworkFile> {
        return if (filter == null) files else files.filter(filter)
    }

    private fun resolvePolicy(remoteApi: RemoteApi, recursive: Boolean): CachePolicy {
        val ttl = when (remoteApi) {
            is Smb -> if (recursive) 60 * 60 * 1000L else 20 * 60 * 1000L
            is WebDav -> if (recursive) 45 * 60 * 1000L else 15 * 60 * 1000L
            is RssSource -> remoteApi.refreshInterval.coerceAtLeast(0L)
            is S3Source -> if (recursive) 30 * 60 * 1000L else 10 * 60 * 1000L
            else -> 10 * 60 * 1000L
        }
        return CachePolicy(CacheMetadata.STRATEGY_STALE_WHILE_REVALIDATE, ttl)
    }

    private fun buildSourceKey(serializedSource: String, recursive: Boolean): String {
        return "$serializedSource|recursive=$recursive".encodeUtf8().sha256().hex()
    }

    // Internal (not private) so service-level tests can seed rows for a source
    // without replicating the type-name mapping.
    internal fun resolveSourceType(remoteApi: RemoteApi): String {
        return getType(remoteApi.getType()).typeName
    }

    /**
     * Test hook: runs [block] while HOLDING the refresh-registry lock, forcing a
     * concurrent [releaseRun] down the Mutex's suspending (cancellable) path.
     * Exists so the cancellation-cleanup test doesn't reach for reflection on a
     * private field; never call from production code.
     */
    internal suspend fun <T> withRefreshRegistryLockedForTest(block: suspend () -> T): T =
        refreshLock.withLock { block() }

    private fun buildConfigHash(serializedSource: String): String {
        return serializedSource.encodeUtf8().sha256().hex()
    }

    private fun buildMetadata(
        existing: CacheMetadata?,
        sourceType: String,
        sourceKey: String,
        lastUpdated: Long,
        nextUpdateTime: Long,
        totalItems: Int,
        policy: CachePolicy,
        recursive: Boolean,
        status: String,
        configHash: String,
        version: String?,
        // Defaults to preserving the existing committed version: an UPDATING write
        // (sync in progress) must NOT advance it. Pass a new version only when it
        // is safe for readers to pin to those rows: either after a successful sync
        // or after a first-sync partial result that is better than clearing the UI.
        committedSyncVersion: Long = existing?.committedSyncVersion ?: 0L,
    ): CacheMetadata {
        return CacheMetadata(
            id = existing?.id ?: 0,
            sourceType = sourceType,
            sourceKey = sourceKey,
            lastUpdated = lastUpdated,
            nextUpdateTime = nextUpdateTime,
            totalItems = totalItems,
            updateStrategy = policy.strategy,
            isRecursive = recursive,
            version = version,
            status = status,
            sourceConfigHash = configHash,
            committedSyncVersion = committedSyncVersion,
        )
    }

    @Serializable
    private data class CachedFileMetadata(
        val modTime: String,
        val isBucket: Boolean,
        // Source-specific durable metadata (notably S3 object key + ETag). A
        // default keeps rows written by older app versions JSON-compatible.
        val sourceExtra: Map<String, String>? = null,
    )

    private data class CachePolicy(
        val strategy: String,
        val ttlMillis: Long,
    )

    private fun NetworkFile.toCachedItem(
        sourceType: String,
        sourceKey: String,
        isRecursive: Boolean,
        syncVersion: Long,
        now: Long,
        metadataJson: Json,
    ): CachedItem {
        return CachedItem(
            sourceType = sourceType,
            sourceKey = sourceKey,
            parentPath = extractParentPath(path),
            name = fileName,
            path = path,
            isDirectory = isDirectory,
            size = size,
            mimeType = mimeType,
            mediaKind = resolveCachedItemMediaKind(fileName, mimeType, isDirectory),
            modifiedTime = parseModifiedTimeMillis(modTime),
            createdAt = now,
            lastAccessed = now,
            isRecursive = isRecursive,
            metadata = metadataJson.encodeToString(CachedFileMetadata(modTime, isBucket, extra)),
            syncVersion = syncVersion,
        )
    }

    /**
     * Sources report modification time in different shapes: epoch millis (rclone
     * style), ISO-8601 instants (Unsplash `created_at`), or plain dates (TMDB
     * `release_date`). A bare `toLongOrNull()` zeroed everything non-numeric,
     * silently degrading the date sorts into path order.
     */
    private fun parseModifiedTimeMillis(modTime: String): Long {
        if (modTime.isBlank()) return 0L
        modTime.toLongOrNull()?.let { return it }
        runCatching { Instant.parse(modTime).toEpochMilliseconds() }.getOrNull()?.let { return it }
        // WebDAV `getlastmodified` is an RFC-1123 HTTP date
        // ("Tue, 04 Nov 2025 10:00:00 GMT").
        runCatching {
            DateTimeComponents.Formats.RFC_1123.parse(modTime).toInstantUsingOffset().toEpochMilliseconds()
        }.getOrNull()?.let { return it }
        return runCatching {
            LocalDate.parse(modTime).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        }.getOrDefault(0L)
    }

    private fun CachedItem.toNetworkFile(remoteApi: RemoteApi, metadataJson: Json): NetworkFile {
        val extra = metadata?.let {
            runCatching { metadataJson.decodeFromString<CachedFileMetadata>(it) }.getOrNull()
        }

        val modTimeValue = extra?.modTime ?: if (modifiedTime > 0) modifiedTime.toString() else ""
        return NetworkFile(
            remote = remoteApi,
            path = path,
            fileName = name,
            isDirectory = isDirectory,
            size = size,
            mimeType = mimeType,
            modTime = modTimeValue,
            isBucket = extra?.isBucket ?: false,
            extra = extra?.sourceExtra,
        )
    }

    private fun extractParentPath(path: String): String {
        val normalized = if (path.length > 1) path.trimEnd('/') else path
        val lastSlash = normalized.lastIndexOf('/')
        return when {
            lastSlash <= 0 -> "/"
            else -> normalized.substring(0, lastSlash)
        }
    }

    /**
     * Ensures cache is populated and reasonably fresh without loading all data into memory.
     * Returns the sourceType/sourceKey for subsequent paged queries, plus a
     * per-sync [CacheReadyResult.completion] handle the caller can await to learn
     * when (and how) the background sync finished — scoped to THIS sync only.
     *
     * For first-time syncs, starts background sync and returns as soon as
     * [MIN_READY_COUNT] media items are available in the DB, so the UI can start
     * displaying content immediately while sync continues. The wait also ends
     * early if the sync fails or completes with no media (no full 30s stall).
     */
    suspend fun <T : RemoteApi> ensureCacheReady(
        remoteApi: T,
        recursive: Boolean,
        repository: BatchSourceRepository<T, NetworkFile>,
        forceRefresh: Boolean = false,
        supportVideo: Boolean = false,
    ): Result<CacheReadyResult> {
        val serializedSource = StorageSourceSerializer.sourceJson.encodeToString(
            RemoteApi.serializer(),
            remoteApi
        )
        val sourceType = resolveSourceType(remoteApi)
        val sourceKey = buildSourceKey(serializedSource, recursive)
        val configHash = buildConfigHash(serializedSource)
        val policy = resolvePolicy(remoteApi, recursive)

        val metadata = metadataDao.getBySource(sourceType, sourceKey)
        val now = currentTimeMillis()

        if (!forceRefresh) {
            val cacheFresh = isCacheFresh(metadata, configHash, now)
            if (cacheFresh) {
                itemDao.updateLastAccessed(sourceType, sourceKey, now)
                // A refresh may have JUST been registered (in inFlightRuns)
                // before it flipped metadata to "updating" — the freshness read
                // above could be stale. If one is in flight, hand back its real
                // completion so this caller still gets notified; otherwise it's
                // genuinely fresh. Read the in-flight handle BEFORE the committed
                // version: if a running sync commits between the two reads, the
                // session then pins the NEW committed rows while awaiting the real
                // completion; the reverse order could pair an old pin with a fake
                // AlreadyFresh and never learn about the commit. (Even then the
                // old pin stays readable — grace retention — and the dead-pin
                // re-pin recovers it, but there is no reason to accept the skew.)
                val inFlight = refreshLock.withLock { inFlightRuns[sourceKey] }
                val completion: CompletableDeferred<CacheSyncResult> =
                    inFlight?.completion ?: CompletableDeferred<CacheSyncResult>().apply {
                        complete(CacheSyncResult.AlreadyFresh)
                    }
                val committedVersion = resolveCommittedSyncVersion(sourceType, sourceKey)
                return Result.success(
                    CacheReadyResult(sourceType, sourceKey, completion, committedSyncVersion = committedVersion)
                )
            }

            // A source is only "cached" when a committed, displayable generation
            // exists. STATUS_UPDATING with committed == 0 (first sync running, or a
            // process killed mid-first-sync) must fall through to the first-sync
            // wait below: a concurrent caller then JOINS the in-flight sync and
            // polls for data (or returns pending) instead of being handed
            // pending=false with a null pin — which surfaced as an instant
            // "No content" or an unpinned read mixing leftover + new rows.
            val hasCache = metadata != null && metadata.hasDisplayableCache()
            if (hasCache) {
                itemDao.updateLastAccessed(sourceType, sourceKey, now)
                val run = startOrJoinRefresh(
                    remoteApi = remoteApi,
                    recursive = recursive,
                    sourceType = sourceType,
                    sourceKey = sourceKey,
                    configHash = configHash,
                    policy = policy,
                    repository = repository,
                    forceRefresh = false,
                )
                // Resolve AFTER acquisition. If another run committed and left the
                // registry in the metadata-read -> acquire gap, the owner recheck
                // above settles AlreadyFresh and this session pins that new version
                // rather than the stale grace generation from the first read.
                val committedVersion = resolveCommittedSyncVersion(sourceType, sourceKey)
                return Result.success(
                    CacheReadyResult(
                        sourceType,
                        sourceKey,
                        run.completion,
                        committedSyncVersion = committedVersion,
                    )
                )
            }
        }

        // No usable cache — start (or join) the background sync and wait for the
        // first batch. The returned handle is the REAL running sync's, shared with
        // any concurrent same-source caller.
        val run = startOrJoinRefresh(
            remoteApi = remoteApi,
            recursive = recursive,
            sourceType = sourceType,
            sourceKey = sourceKey,
            configHash = configHash,
            policy = policy,
            repository = repository,
            forceRefresh = forceRefresh,
        )
        val completion = run.completion

        // Readiness is judged on the rows the session will actually READ — the
        // committed version if a terminal event already landed, else the version
        // the RUNNING sync is writing. Counting across all versions would let an
        // abandoned generation (leftover rows after a process death mid-sync)
        // report "ready" while the pin targets a new version with no media yet,
        // surfacing as an instant "No content" despite data being on the way.
        //
        // The committed version observed before acquisition is stable grace data;
        // otherwise the readable version belongs to this exact run record. Never
        // re-query an active version by source key here: C1 may have been removed
        // and C2 registered while this caller is still settling C1.
        // An INVALID metadata row can still carry a historical committed marker
        // (for example after upgrading an old failed cache). Such rows are not a
        // displayable generation and must not satisfy first-sync readiness or be
        // exposed while the replacement sync is still running.
        val startingCommittedVersion = metadata
            ?.takeIf { it.hasDisplayableCache() }
            ?.committedSyncVersion
            ?.takeIf { it > 0 }
        suspend fun readableVersion(): Long? {
            return startingCommittedVersion ?: refreshLock.withLock {
                run.reusedCommittedVersion ?: run.activeVersion
            }
        }

        withTimeoutOrNull(EARLY_RETURN_TIMEOUT_MS) {
            while (true) {
                val version = readableVersion()
                val mediaCount = version?.let {
                    countMedia(sourceType, sourceKey, supportVideo, it)
                } ?: 0
                if (mediaCount >= MIN_READY_COUNT) return@withTimeoutOrNull
                // The sync reached a terminal state — stop waiting immediately
                // instead of polling for the full timeout. `isCompleted` is stable
                // API (no experimental opt-in). Terminal metadata (commit, partial
                // commit or rollback) settles BEFORE the completion resolves, so
                // the post-completion read below sees the surviving truth.
                if (completion.isCompleted) return@withTimeoutOrNull
                delay(EARLY_RETURN_POLL_INTERVAL_MS)
            }
        }

        // Snapshot the running path once after the wait. Terminal settlement
        // refreshes this same local run because it may bind after this snapshot.
        val (reusedAfterWait, activeAfterWait) = refreshLock.withLock {
            run.reusedCommittedVersion to run.activeVersion
        }
        afterReadyVersionSnapshotForTest?.invoke(activeAfterWait, completion)

        suspend fun settleTerminalResult(terminal: CacheSyncResult): Result<CacheReadyResult> {
            // A run may bind and settle after the readiness timeout snapshots a
            // null active version. Refresh THIS run record only after its handle is
            // terminal; looking up by source key could accidentally adopt C2.
            val (terminalReusedVersion, terminalRunVersion) = refreshLock.withLock {
                run.reusedCommittedVersion to run.activeVersion
            }
            val committedVersion = resolveCommittedSyncVersion(sourceType, sourceKey)
            val committedByTerminalRun = committedVersion
                ?.takeIf { it == terminalRunVersion }

            when (terminal) {
                is CacheSyncResult.Completed -> when {
                    committedVersion == null ->
                        return Result.failure(Exception("Cache sync completed without committed metadata"))
                    terminalRunVersion == null ->
                        return Result.failure(Exception("Cache sync completed without a bound run version"))
                    committedVersion != terminalRunVersion ->
                        return Result.failure(
                            Exception("Committed version does not belong to the completed cache sync")
                        )
                }
                is CacheSyncResult.Failed -> {
                    val committedBelongsToKnownGeneration = committedVersion == null ||
                        committedVersion == terminalRunVersion ||
                        committedVersion == terminalReusedVersion ||
                        committedVersion == startingCommittedVersion
                    if (!committedBelongsToKnownGeneration) {
                        return Result.failure(
                            Exception("Committed version does not belong to the failed cache sync")
                        )
                    }
                }
                CacheSyncResult.AlreadyFresh -> {
                    if (terminalReusedVersion == null) {
                        return Result.failure(Exception("Cache sync failed: no data available"))
                    }
                }
            }

            val terminalPinVersion = terminalReusedVersion ?:
                committedByTerminalRun ?:
                startingCommittedVersion
            val terminalMediaCount = terminalPinVersion?.let {
                countMedia(sourceType, sourceKey, supportVideo, it)
            } ?: 0

            return when {
                terminalMediaCount > 0 ||
                    terminal is CacheSyncResult.Completed ||
                    terminalReusedVersion != null ->
                    Result.success(
                        CacheReadyResult(
                            sourceType = sourceType,
                            sourceKey = sourceKey,
                            completion = completion,
                            committedSyncVersion = terminalPinVersion,
                        )
                    )
                terminal is CacheSyncResult.Failed -> Result.failure(terminal.error)
                else -> Result.failure(Exception("Cache sync failed: no data available"))
            }
        }

        // Await only a handle already known to be terminal. A still-running sync
        // must keep the existing 30s readiness budget and pending behavior.
        if (completion.isCompleted) {
            return settleTerminalResult(completion.await())
        }

        // If THIS run already reached a terminal state that committed ITS OWN
        // version (success, or a rate-limited partial commit), that generation
        // supersedes the starting pin: without it, a forced refresh over a
        // committed-but-EMPTY cache would keep counting the frozen empty pin and
        // return failure despite freshly committed rows. Run-bound on purpose —
        // only a committed version EQUAL to this run's own version is adopted, so
        // a later run's commit (equal to ITS version, never ours) can never be
        // paired with this run's completion (no cross-generation ABA).
        val committedByThisRun = resolveCommittedSyncVersion(sourceType, sourceKey)
            ?.takeIf { it == activeAfterWait }
        val pinVersion = reusedAfterWait ?:
            committedByThisRun ?:
            startingCommittedVersion ?:
            activeAfterWait
        val pinnedMediaCount = pinVersion?.let {
            countMedia(sourceType, sourceKey, supportVideo, it)
        } ?: 0
        return when {
            // A second owner check found a fresh committed generation. A valid
            // committed-empty cache is also a successful ready result, so this
            // branch intentionally precedes the media-count test.
            reusedAfterWait != null ->
                Result.success(
                    CacheReadyResult(
                        sourceType,
                        sourceKey,
                        completion,
                        committedSyncVersion = reusedAfterWait,
                    )
                )
            pinnedMediaCount > 0 && !completion.isCompleted ->
                Result.success(
                    CacheReadyResult(
                        sourceType, sourceKey, completion,
                        committedSyncVersion = pinVersion,
                        // Snapshot ordering only when the pin is the still-growing
                        // in-flight version: page it in append-stable insertion
                        // order (a name/date sort would shift OFFSET windows as
                        // later batches insert earlier-sorting rows).
                        initialSnapshot = committedByThisRun == null &&
                            startingCommittedVersion == null &&
                            !completion.isCompleted,
                    )
                )
            completion.isCompleted -> settleTerminalResult(completion.await())
            // Timed out but the sync is STILL running and may yet produce data.
            // Return a pending result so the UI shows loading and recovers via the
            // completion handle instead of getting stuck on an error.
            else ->
                Result.success(CacheReadyResult(sourceType, sourceKey, completion, pending = true))
        }
    }

    fun resolveSourceKey(remoteApi: RemoteApi, recursive: Boolean): String {
        val serializedSource = StorageSourceSerializer.sourceJson.encodeToString(
            RemoteApi.serializer(),
            remoteApi
        )
        return buildSourceKey(serializedSource, recursive)
    }

    /**
     * Count media items (images or images+videos) in the cache.
     */
    /**
     * The committed sync_version for a source, or null if no displayable version is
     * available yet. Read from metadata — NOT MAX(sync_version), which during a
     * background refresh would return the in-flight, partially-written version and
     * let a reader pin to incomplete data. A read session captures this once so all
     * its paged/count reads see ONE stable version.
     */
    suspend fun resolveCommittedSyncVersion(sourceType: String, sourceKey: String): Long? =
        metadataDao.getBySource(sourceType, sourceKey)
            ?.takeIf { it.hasDisplayableCache() }
            ?.committedSyncVersion
            ?.takeIf { it > 0 }

    /**
     * The CURRENT ordinal of the media at [path] under [syncVersion] and the
     * session's ordering, or null when the item no longer exists there (or isn't
     * displayable media). Lets pagers re-anchor onto the SAME picture after a
     * refresh even when the page containing it isn't loaded in memory.
     */
    suspend fun locateMediaIndex(
        sourceType: String,
        sourceKey: String,
        supportVideo: Boolean,
        sortRule: Int,
        syncVersion: Long,
        insertionOrder: Boolean,
        path: String,
    ): Int? {
        val target = itemDao.getByPathVersion(sourceType, sourceKey, syncVersion, path) ?: return null
        if (target.isDirectory) return null
        val maxMediaKind = if (supportVideo) 2 else 1
        if (target.mediaKind < 1 || target.mediaKind > maxMediaKind) return null
        return if (insertionOrder) {
            itemDao.countMediaBeforeIdVersion(sourceType, sourceKey, syncVersion, maxMediaKind, target.id)
        } else when (sortRule) {
            2 -> itemDao.countMediaBeforeNameDescVersion(sourceType, sourceKey, syncVersion, maxMediaKind, target.name, target.path)
            3 -> itemDao.countMediaBeforeDateAscVersion(sourceType, sourceKey, syncVersion, maxMediaKind, target.modifiedTime, target.path)
            4 -> itemDao.countMediaBeforeDateDescVersion(sourceType, sourceKey, syncVersion, maxMediaKind, target.modifiedTime, target.path)
            else -> itemDao.countMediaBeforeNameAscVersion(sourceType, sourceKey, syncVersion, maxMediaKind, target.name, target.path)
        }
    }

    suspend fun countMedia(
        sourceType: String,
        sourceKey: String,
        supportVideo: Boolean,
        syncVersion: Long? = null,
    ): Int {
        val pinnedVersion = syncVersion ?: return 0
        return if (supportVideo) {
            itemDao.countMediaBySourceVersion(sourceType, sourceKey, pinnedVersion)
        } else {
            itemDao.countImagesBySourceVersion(sourceType, sourceKey, pinnedVersion)
        }
    }

    /**
     * Load a page of media files from the cache.
     * sortRule: -1 or 0 = random/default (name asc), 1 = name asc, 2 = name desc, 3 = date asc, 4 = date desc
     * Reads require a concrete [syncVersion]. A null pin means no generation is
     * displayable yet and returns an empty page; it must never degrade into a
     * cross-generation query that can expose abandoned or in-flight rows.
     * [insertionOrder] (requires [syncVersion]) reads in append-stable id order —
     * used by first-sync snapshot sessions whose version is still being written,
     * where any content sort would shift OFFSET windows between calls.
     */
    suspend fun loadMediaPage(
        remoteApi: RemoteApi,
        sourceType: String,
        sourceKey: String,
        supportVideo: Boolean,
        sortRule: Int,
        offset: Int,
        limit: Int,
        syncVersion: Long? = null,
        insertionOrder: Boolean = false,
    ): List<NetworkFile> {
        val pinnedVersion = syncVersion ?: return emptyList()
        val items = if (insertionOrder) {
            if (supportVideo) {
                itemDao.getMediaPagedByIdAscVersion(sourceType, sourceKey, pinnedVersion, limit, offset)
            } else {
                itemDao.getImagesPagedByIdAscVersion(sourceType, sourceKey, pinnedVersion, limit, offset)
            }
        } else {
            if (supportVideo) {
                when (sortRule) {
                    2 -> itemDao.getMediaPagedByNameDescVersion(sourceType, sourceKey, pinnedVersion, limit, offset)
                    3 -> itemDao.getMediaPagedByDateAscVersion(sourceType, sourceKey, pinnedVersion, limit, offset)
                    4 -> itemDao.getMediaPagedByDateDescVersion(sourceType, sourceKey, pinnedVersion, limit, offset)
                    else -> itemDao.getMediaPagedByNameAscVersion(sourceType, sourceKey, pinnedVersion, limit, offset)
                }
            } else {
                when (sortRule) {
                    2 -> itemDao.getImagesPagedByNameDescVersion(sourceType, sourceKey, pinnedVersion, limit, offset)
                    3 -> itemDao.getImagesPagedByDateAscVersion(sourceType, sourceKey, pinnedVersion, limit, offset)
                    4 -> itemDao.getImagesPagedByDateDescVersion(sourceType, sourceKey, pinnedVersion, limit, offset)
                    else -> itemDao.getImagesPagedByNameAscVersion(sourceType, sourceKey, pinnedVersion, limit, offset)
                }
            }
        }
        return items.map { it.toNetworkFile(remoteApi, metadataJson) }
    }

    private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
