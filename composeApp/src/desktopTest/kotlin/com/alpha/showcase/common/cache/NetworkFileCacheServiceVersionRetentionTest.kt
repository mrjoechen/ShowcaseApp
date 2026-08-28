package com.alpha.showcase.common.cache

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.cache.entity.CACHED_ITEM_MEDIA_KIND_IMAGE
import com.alpha.showcase.common.cache.entity.CacheMetadata
import com.alpha.showcase.common.cache.entity.CachedItem
import com.alpha.showcase.common.repo.BatchSourceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Success-path commit behavior: the atomic switch keeps the PREVIOUS committed
 * version's rows as a grace copy (read sessions opened before the sync stay
 * pinned to it), and purges it only when the NEXT sync commits.
 */
class NetworkFileCacheServiceVersionRetentionTest {

    private lateinit var db: SourceCacheDatabase
    private lateinit var service: NetworkFileCacheService

    private val source = UnSplashSource(
        name = "version-retention",
        photoType = "test",
        user = "user",
    )

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<SourceCacheDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(kotlinx.coroutines.Dispatchers.Default)
            .build()
        service = NetworkFileCacheService(db)
    }

    @AfterTest
    fun tearDown() = runBlocking {
        service.shutdownBackgroundRefreshes()
        db.close()
    }

    private data class SyncOutcome(val sourceType: String, val sourceKey: String, val committed: Long)

    private suspend fun runSuccessfulSync(): SyncOutcome {
        val ready = service.ensureCacheReady(
            remoteApi = source,
            recursive = false,
            repository = FixedBatchRepository(source),
            forceRefresh = true,
            supportVideo = false,
        ).getOrThrow()
        assertIs<CacheSyncResult.Completed>(ready.completion.await())
        val committed = service.resolveCommittedSyncVersion(ready.sourceType, ready.sourceKey)
        assertNotNull(committed)
        return SyncOutcome(ready.sourceType, ready.sourceKey, committed)
    }

    @Test
    fun firstSuccessfulEmptySyncReturnsCommittedReadyResult() = runBlocking {
        val ready = service.ensureCacheReady(
            remoteApi = source,
            recursive = false,
            repository = EmptyRepository(),
            supportVideo = false,
        ).getOrThrow()

        val completed = assertIs<CacheSyncResult.Completed>(ready.completion.await())
        assertEquals(0, completed.totalItems)
        assertFalse(ready.pending)
        assertFalse(ready.initialSnapshot)

        val committed = assertNotNull(ready.committedSyncVersion)
        assertEquals(
            service.resolveCommittedSyncVersion(ready.sourceType, ready.sourceKey),
            committed,
        )
        assertEquals(0, service.countMedia(ready.sourceType, ready.sourceKey, false, committed))
    }

    @Test
    fun terminalEmptyCompletionRefreshesStaleActiveVersionSnapshot() = runTest {
        val allowVersionBind = CompletableDeferred<Unit>()
        service.beforeSyncVersionBindForTest = {
            allowVersionBind.await()
        }
        service.afterReadyVersionSnapshotForTest = { activeVersion, completion ->
            assertNull(activeVersion, "the terminal run must complete after the stale null snapshot")
            service.beforeSyncVersionBindForTest = null
            service.afterReadyVersionSnapshotForTest = null
            allowVersionBind.complete(Unit)
            assertIs<CacheSyncResult.Completed>(completion.await())
        }

        val ready = service.ensureCacheReady(
            remoteApi = source,
            recursive = false,
            repository = EmptyRepository(),
            supportVideo = false,
        ).getOrThrow()

        val committed = assertNotNull(ready.committedSyncVersion)
        assertEquals(
            service.resolveCommittedSyncVersion(ready.sourceType, ready.sourceKey),
            committed,
        )
        assertEquals(0, service.countMedia(ready.sourceType, ready.sourceKey, false, committed))
    }

    @Test
    fun terminalFailedPartialSyncRefreshesStaleActiveVersionSnapshot() = runTest {
        val allowVersionBind = CompletableDeferred<Unit>()
        val failGate = CompletableDeferred<Unit>()
        val settledVersion = CompletableDeferred<Long>()
        val sourceType = service.resolveSourceType(source)
        val sourceKey = service.resolveSourceKey(source, recursive = false)
        service.beforeSyncVersionBindForTest = {
            allowVersionBind.await()
        }
        service.afterReadyVersionSnapshotForTest = { activeVersion, completion ->
            assertNull(activeVersion, "the failed run must settle after the stale null snapshot")
            service.beforeSyncVersionBindForTest = null
            service.afterReadyVersionSnapshotForTest = null
            failGate.complete(Unit)
            allowVersionBind.complete(Unit)
            assertIs<CacheSyncResult.Failed>(completion.await())
            val committed = assertNotNull(service.resolveCommittedSyncVersion(sourceType, sourceKey))
            assertEquals(1, service.countMedia(sourceType, sourceKey, false, committed))
            settledVersion.complete(committed)
        }

        val ready = service.ensureCacheReady(
            remoteApi = source,
            recursive = false,
            repository = FailAfterOneBatchRepository(source, failGate),
            supportVideo = false,
        ).getOrThrow()

        assertIs<CacheSyncResult.Failed>(ready.completion.await())
        assertFalse(ready.pending)
        assertFalse(ready.initialSnapshot)
        val committed = assertNotNull(ready.committedSyncVersion)
        assertEquals(settledVersion.await(), committed)
        assertEquals(
            service.resolveCommittedSyncVersion(ready.sourceType, ready.sourceKey),
            committed,
        )
        assertEquals(1, service.countMedia(ready.sourceType, ready.sourceKey, false, committed))
        assertEquals(
            listOf("https://images.example/failed-run.jpg"),
            db.cachedItemDao()
                .getBySourceVersion(ready.sourceType, ready.sourceKey, committed)
                .map { it.path },
        )
    }

    @Test
    fun firstSyncFailurePreservesTerminalCause() = runBlocking {
        val expected = Exception("network down")

        val result = service.ensureCacheReady(
            remoteApi = source,
            recursive = false,
            repository = AlwaysFailRepository(expected),
            supportVideo = false,
        )

        assertSame(expected, result.exceptionOrNull())
    }

    @Test
    fun successfulResyncRetainsPreviousCommittedVersionAndPurgesOlder() = runBlocking {
        val (sourceType, sourceKey, v1) = runSuccessfulSync()
        assertEquals(3, service.countMedia(sourceType, sourceKey, false, v1))

        // Second sync commits v2 but must RETAIN v1 as the grace copy: a session
        // pinned to v1 (e.g. an AlreadyFresh page that never re-pins) keeps paging.
        val v2 = runSuccessfulSync().committed
        assertEquals(3, service.countMedia(sourceType, sourceKey, false, v2))
        assertEquals(3, service.countMedia(sourceType, sourceKey, false, v1))

        // Third sync commits v3, retains v2, and finally purges v1.
        val v3 = runSuccessfulSync().committed
        assertEquals(3, service.countMedia(sourceType, sourceKey, false, v3))
        assertEquals(3, service.countMedia(sourceType, sourceKey, false, v2))
        assertEquals(0, service.countMedia(sourceType, sourceKey, false, v1))
    }

    @Test
    fun restartedProcessAllocatesAboveCommittedGraceAndAbandonedVersions() = runBlocking {
        val sourceType = service.resolveSourceType(source)
        val sourceKey = service.resolveSourceKey(source, recursive = false)
        // Simulate generations written by an earlier process whose wall clock was
        // far ahead of this process. The abandoned generation deliberately sits
        // one step above metadata: flooring from metadata alone would reuse it.
        val committedVersion = 4_000_000_000_000L
        val graceVersion = committedVersion - 1
        val abandonedVersion = committedVersion + 1
        val collidingPath = "https://images.example/collision.jpg"
        db.cachedItemDao().insertOrIgnore(
            listOf(
                cachedImage(sourceType, sourceKey, "committed.jpg", committedVersion),
                cachedImage(sourceType, sourceKey, "grace.jpg", graceVersion),
                cachedImage(sourceType, sourceKey, collidingPath, abandonedVersion),
            )
        )
        db.cacheMetadataDao().insertOrReplace(
            CacheMetadata(
                sourceType = sourceType,
                sourceKey = sourceKey,
                lastUpdated = 1,
                nextUpdateTime = 1,
                totalItems = 1,
                updateStrategy = CacheMetadata.STRATEGY_STALE_WHILE_REVALIDATE,
                isRecursive = false,
                status = CacheMetadata.STATUS_VALID,
                committedSyncVersion = committedVersion,
            )
        )

        val freshPaths = listOf(collidingPath, "https://images.example/fresh.jpg")
        val result = service.getOrLoad(
            remoteApi = source,
            recursive = false,
            filter = null,
            repository = PathRepository(source, freshPaths),
            forceRefresh = true,
        )

        assertEquals(freshPaths, result.getOrThrow().map { it.path })
        val metadata = assertNotNull(db.cacheMetadataDao().getBySource(sourceType, sourceKey))
        val newVersion = metadata.committedSyncVersion
        assertTrue(
            newVersion > abandonedVersion,
            "a restarted process must allocate above every persisted source generation",
        )
        assertEquals(freshPaths, db.cachedItemDao().getBySourceVersion(sourceType, sourceKey, newVersion).map { it.path })
        assertEquals(freshPaths.size, metadata.totalItems, "reused generations must never mix stale and fresh rows")
    }

    @Test
    fun committedEmptyMetadataAloneRaisesTheNextPersistentGeneration() = runBlocking {
        val sourceType = service.resolveSourceType(source)
        val sourceKey = service.resolveSourceKey(source, recursive = false)
        val metadataOnlyFloor = 4_100_000_000_000L
        db.cacheMetadataDao().insertOrReplace(
            CacheMetadata(
                sourceType = sourceType,
                sourceKey = sourceKey,
                lastUpdated = 1,
                nextUpdateTime = 1,
                totalItems = 0,
                updateStrategy = CacheMetadata.STRATEGY_STALE_WHILE_REVALIDATE,
                isRecursive = false,
                status = CacheMetadata.STATUS_VALID,
                committedSyncVersion = metadataOnlyFloor,
            )
        )
        assertEquals(0, db.cachedItemDao().countBySource(sourceType, sourceKey))

        val freshPath = "https://images.example/after-empty.jpg"
        val result = service.getOrLoad(
            remoteApi = source,
            recursive = false,
            filter = null,
            repository = PathRepository(source, listOf(freshPath)),
            forceRefresh = true,
        )

        assertEquals(listOf(freshPath), result.getOrThrow().map { it.path })
        val metadata = assertNotNull(db.cacheMetadataDao().getBySource(sourceType, sourceKey))
        assertTrue(
            metadata.committedSyncVersion > metadataOnlyFloor,
            "a committed-empty marker must raise the next generation even without item rows",
        )
        assertEquals(1, metadata.totalItems)
        assertEquals(
            listOf(freshPath),
            db.cachedItemDao()
                .getBySourceVersion(sourceType, sourceKey, metadata.committedSyncVersion)
                .map { it.path },
        )
    }

    @Test
    fun exhaustedPersistentGenerationFailsWithoutStartingOrHangingSync() = runBlocking {
        val sourceType = service.resolveSourceType(source)
        val sourceKey = service.resolveSourceKey(source, recursive = false)
        val existingPath = "https://images.example/max-generation.jpg"
        db.cachedItemDao().insertOrIgnore(
            listOf(cachedImage(sourceType, sourceKey, existingPath, Long.MAX_VALUE))
        )
        db.cacheMetadataDao().insertOrReplace(
            CacheMetadata(
                sourceType = sourceType,
                sourceKey = sourceKey,
                lastUpdated = 1,
                nextUpdateTime = 1,
                totalItems = 1,
                updateStrategy = CacheMetadata.STRATEGY_STALE_WHILE_REVALIDATE,
                isRecursive = false,
                status = CacheMetadata.STATUS_VALID,
                committedSyncVersion = Long.MAX_VALUE,
            )
        )
        val repository = PathRepository(source, listOf("https://images.example/must-not-run.jpg"))

        val ready = withTimeout(5_000) {
            service.ensureCacheReady(
                remoteApi = source,
                recursive = false,
                repository = repository,
                forceRefresh = true,
                supportVideo = false,
            ).getOrThrow()
        }
        val failed = assertIs<CacheSyncResult.Failed>(withTimeout(5_000) { ready.completion.await() })

        assertIs<IllegalStateException>(failed.error)
        assertEquals(0, repository.invocations, "version exhaustion must fail before source traversal")

        // The direct Result-returning API must not throw or hang. Its established
        // failed-refresh contract keeps serving the complete old snapshot; the
        // per-run ensure path above still exposes the precise terminal failure.
        val direct = withTimeout(5_000) {
            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = repository,
                forceRefresh = true,
            )
        }
        assertEquals(listOf(existingPath), direct.getOrThrow().map { it.path })
        assertEquals(0, repository.invocations)
        assertEquals(Long.MAX_VALUE, service.resolveCommittedSyncVersion(sourceType, sourceKey))
        assertEquals(
            listOf(existingPath),
            db.cachedItemDao().getBySourceVersion(sourceType, sourceKey, Long.MAX_VALUE).map { it.path },
        )
    }

    @Test
    fun failedResyncKeepsExistingCommittedCacheIntact() = runBlocking {
        val (sourceType, sourceKey, v1) = runSuccessfulSync()
        assertEquals(3, service.countMedia(sourceType, sourceKey, false, v1))

        // A forced refresh writes one partial row, then the source fails (rate
        // limit / network). The existing committed generation must win: same
        // committed version, same rows, failed run's rows rolled back.
        val failGate = CompletableDeferred<Unit>()
        val failing = FailAfterOneBatchRepository(source, failGate)
        val readyDeferred = async {
            service.ensureCacheReady(
                remoteApi = source,
                recursive = false,
                repository = failing,
                forceRefresh = true,
                supportVideo = false,
            ).getOrThrow()
        }
        failing.firstBatchWritten.await()
        failGate.complete(Unit)
        val ready = readyDeferred.await()
        assertIs<CacheSyncResult.Failed>(ready.completion.await())

        assertEquals(v1, ready.committedSyncVersion)
        assertEquals(v1, service.resolveCommittedSyncVersion(sourceType, sourceKey))
        assertEquals(3, service.countMedia(sourceType, sourceKey, false, v1))
        // No stray rows from the failed run survive anywhere.
        assertEquals(3, db.cachedItemDao().countMediaBySource(sourceType, sourceKey))
    }

    @Test
    fun emptyCommittedCacheDoesNotSwallowPartialResync() = runBlocking {
        // v1 commits EMPTY: the source legitimately had nothing at first sync.
        val empty = service.getOrLoad(
            remoteApi = source,
            recursive = false,
            filter = null,
            repository = EmptyRepository(),
            forceRefresh = true,
        )
        assertEquals(emptyList(), empty.getOrThrow())
        val sourceType = service.resolveSourceType(source)
        val sourceKey = service.resolveSourceKey(source, recursive = false)
        val emptyCommitted = service.resolveCommittedSyncVersion(sourceType, sourceKey)
        assertNotNull(emptyCommitted)

        // A re-sync fetches 60 rows and then fails (rate limit). The committed-
        // but-EMPTY v1 must NOT outrank them: the partial rows are committed so
        // the user finally sees content.
        //
        // Run the caller on a REAL dispatcher: under runTest's virtual clock the
        // 30s early-return window fast-forwards during Room hops and the call
        // always returns `pending` before the sync settles. On the wall clock the
        // starting pin (empty v1) can never satisfy the readiness count, so the
        // wait deterministically ends via the TERMINAL state — exercising the
        // run-bound pin adoption instead of bypassing it.
        val failGate = CompletableDeferred<Unit>()
        val failing = SixtyThenFailRepository(source, failGate)
        val readyDeferred = async(Dispatchers.Default) {
            service.ensureCacheReady(
                remoteApi = source,
                recursive = false,
                repository = failing,
                forceRefresh = true,
                supportVideo = false,
            ).getOrThrow()
        }
        failing.firstBatchWritten.await()
        failGate.complete(Unit)
        val ready = readyDeferred.await()
        assertIs<CacheSyncResult.Failed>(ready.completion.await())

        val committed = service.resolveCommittedSyncVersion(sourceType, sourceKey)
        assertNotNull(committed)
        assertTrue(committed != emptyCommitted, "partial rows must take over the empty committed generation")
        assertEquals(60, service.countMedia(sourceType, sourceKey, false, committed))
        // Run-bound terminal adoption: the ready result pins the freshly committed
        // partial generation — not the frozen empty pin, and not a failure.
        assertFalse(ready.pending)
        assertEquals(committed, ready.committedSyncVersion)
        assertFalse(ready.initialSnapshot)
    }

    @Test
    fun abandonedLeftoverVersionDoesNotFakeReadiness() = runBlocking {
        // Simulate a process death mid-first-sync: 60 leftover media rows of an
        // abandoned version plus UPDATING metadata with nothing committed.
        val sourceType = service.resolveSourceType(source)
        val sourceKey = service.resolveSourceKey(source, recursive = false)
        val leftoverVersion = 500L
        db.cachedItemDao().insertOrIgnore(
            (0 until 60).map { index ->
                CachedItem(
                    sourceType = sourceType,
                    sourceKey = sourceKey,
                    parentPath = "/",
                    name = "leftover-$index.jpg",
                    path = "https://images.example/leftover-$index.jpg",
                    isDirectory = false,
                    size = 1,
                    mimeType = "image/jpeg",
                    mediaKind = CACHED_ITEM_MEDIA_KIND_IMAGE,
                    modifiedTime = 0,
                    syncVersion = leftoverVersion,
                )
            }
        )
        db.cacheMetadataDao().insertOrReplace(
            CacheMetadata(
                sourceType = sourceType,
                sourceKey = sourceKey,
                lastUpdated = 1,
                nextUpdateTime = 1,
                totalItems = 0,
                updateStrategy = CacheMetadata.STRATEGY_STALE_WHILE_REVALIDATE,
                isRecursive = false,
                status = CacheMetadata.STATUS_UPDATING,
                committedSyncVersion = 0,
            )
        )

        // The new sync writes only 10 rows before finishing. Readiness must be
        // judged on the version the session will read — never on the leftover
        // generation's 60 rows, which previously produced pending=false with a
        // pin that had zero media ("No content" despite data on the way).
        val gate = CompletableDeferred<Unit>()
        val repository = TenThenSucceedRepository(source, gate)
        val readyDeferred = async(Dispatchers.Default) {
            service.ensureCacheReady(
                remoteApi = source,
                recursive = false,
                repository = repository,
                supportVideo = false,
            ).getOrThrow()
        }
        repository.firstBatchWritten.await()
        gate.complete(Unit)
        val ready = readyDeferred.await()
        assertIs<CacheSyncResult.Completed>(ready.completion.await())

        val committed = service.resolveCommittedSyncVersion(sourceType, sourceKey)
        assertNotNull(committed)
        assertTrue(committed != leftoverVersion)
        assertEquals(10, service.countMedia(sourceType, sourceKey, false, committed))
        // Whatever pin the early return produced must actually have media —
        // count and pin from the same version.
        assertFalse(ready.pending)
        val pin = assertNotNull(ready.committedSyncVersion)
        assertTrue(
            service.countMedia(sourceType, sourceKey, false, pin) > 0,
            "a non-pending ready result must never pin a version without media",
        )
    }

    @Test
    fun nullPagedPinDoesNotExposeAbandonedGeneration() = runBlocking {
        val sourceType = service.resolveSourceType(source)
        val sourceKey = service.resolveSourceKey(source, recursive = false)
        val leftoverVersion = 500L
        db.cachedItemDao().insertOrIgnore(
            listOf(
                CachedItem(
                    sourceType = sourceType,
                    sourceKey = sourceKey,
                    parentPath = "/",
                    name = "leftover.jpg",
                    path = "https://images.example/leftover.jpg",
                    isDirectory = false,
                    size = 1,
                    mimeType = "image/jpeg",
                    mediaKind = CACHED_ITEM_MEDIA_KIND_IMAGE,
                    modifiedTime = 0,
                    syncVersion = leftoverVersion,
                )
            )
        )
        db.cacheMetadataDao().insertOrReplace(
            CacheMetadata(
                sourceType = sourceType,
                sourceKey = sourceKey,
                lastUpdated = 1,
                nextUpdateTime = 1,
                totalItems = 0,
                updateStrategy = CacheMetadata.STRATEGY_STALE_WHILE_REVALIDATE,
                isRecursive = false,
                status = CacheMetadata.STATUS_UPDATING,
                committedSyncVersion = 0,
            )
        )
        assertEquals(1, db.cachedItemDao().countMediaBySource(sourceType, sourceKey))

        assertEquals(
            0,
            service.countMedia(
                sourceType = sourceType,
                sourceKey = sourceKey,
                supportVideo = false,
                syncVersion = null,
            )
        )
        assertEquals(
            emptyList(),
            service.loadMediaPage(
                remoteApi = source,
                sourceType = sourceType,
                sourceKey = sourceKey,
                supportVideo = false,
                sortRule = 1,
                offset = 0,
                limit = 10,
                syncVersion = null,
            )
        )
    }

    @Test
    fun failedRefreshKeepsCommittedEmptyCacheKnownEmpty() = runBlocking {
        // Commit a legitimately-EMPTY generation.
        service.getOrLoad(
            remoteApi = source,
            recursive = false,
            filter = null,
            repository = EmptyRepository(),
            forceRefresh = true,
        ).getOrThrow()
        val sourceType = service.resolveSourceType(source)
        val sourceKey = service.resolveSourceKey(source, recursive = false)
        val emptyCommitted = service.resolveCommittedSyncVersion(sourceType, sourceKey)
        assertNotNull(emptyCommitted)

        // A refresh fails outright (no rows fetched). The committed-empty marker
        // must survive — flipping to INVALID would destroy the offline
        // "known empty" answer.
        service.getOrLoad(
            remoteApi = source,
            recursive = false,
            filter = null,
            repository = AlwaysFailRepository(),
            forceRefresh = true,
        )
        assertEquals(emptyCommitted, service.resolveCommittedSyncVersion(sourceType, sourceKey))

        // The next non-forced access answers "known empty" from metadata without
        // touching the source at all.
        val counting = AlwaysFailRepository()
        val result = service.getOrLoad(
            remoteApi = source,
            recursive = false,
            filter = null,
            repository = counting,
            forceRefresh = false,
        )
        assertEquals(emptyList(), result.getOrThrow())
        assertEquals(0, counting.invocations, "known-empty must be served without re-traversing the source")
    }

    @Test
    fun invalidHistoricalMarkerCannotOutrankNewPartialGeneration() = runBlocking {
        val sourceType = service.resolveSourceType(source)
        val sourceKey = service.resolveSourceKey(source, recursive = false)
        val invalidVersion = 500L
        db.cachedItemDao().insertOrIgnore(
            (0 until 60).map { index ->
                CachedItem(
                    sourceType = sourceType,
                    sourceKey = sourceKey,
                    parentPath = "/",
                    name = "invalid-$index.jpg",
                    path = "https://images.example/invalid-$index.jpg",
                    isDirectory = false,
                    size = 1,
                    mimeType = "image/jpeg",
                    mediaKind = CACHED_ITEM_MEDIA_KIND_IMAGE,
                    modifiedTime = 0,
                    syncVersion = invalidVersion,
                )
            },
        )
        db.cacheMetadataDao().insertOrReplace(
            CacheMetadata(
                sourceType = sourceType,
                sourceKey = sourceKey,
                lastUpdated = 1,
                nextUpdateTime = 1,
                totalItems = 60,
                updateStrategy = CacheMetadata.STRATEGY_STALE_WHILE_REVALIDATE,
                isRecursive = false,
                status = CacheMetadata.STATUS_INVALID,
                committedSyncVersion = invalidVersion,
            ),
        )

        assertNull(
            service.resolveCommittedSyncVersion(sourceType, sourceKey),
            "an INVALID marker is not a displayable committed generation",
        )

        val failGate = CompletableDeferred<Unit>()
        val failing = SixtyThenFailRepository(source, failGate)
        val result = async(Dispatchers.Default) {
            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = failing,
                forceRefresh = true,
            )
        }
        failing.firstBatchWritten.await()
        failGate.complete(Unit)
        assertEquals(60, result.await().getOrThrow().size)

        val committed = assertNotNull(service.resolveCommittedSyncVersion(sourceType, sourceKey))
        assertTrue(committed != invalidVersion)
        assertEquals(60, service.countMedia(sourceType, sourceKey, false, committed))
    }

    @Test
    fun startingReplacementSyncClearsInvalidHistoricalCommitMarker() = runBlocking {
        val sourceType = service.resolveSourceType(source)
        val sourceKey = service.resolveSourceKey(source, recursive = false)
        val invalidVersion = 500L
        db.cachedItemDao().insertOrIgnore(
            listOf(
                CachedItem(
                    sourceType = sourceType,
                    sourceKey = sourceKey,
                    parentPath = "/",
                    name = "invalid.jpg",
                    path = "https://images.example/invalid.jpg",
                    isDirectory = false,
                    size = 1,
                    mimeType = "image/jpeg",
                    mediaKind = CACHED_ITEM_MEDIA_KIND_IMAGE,
                    modifiedTime = 0,
                    syncVersion = invalidVersion,
                )
            ),
        )
        db.cacheMetadataDao().insertOrReplace(
            CacheMetadata(
                sourceType = sourceType,
                sourceKey = sourceKey,
                lastUpdated = 1,
                nextUpdateTime = 1,
                totalItems = 1,
                updateStrategy = CacheMetadata.STRATEGY_STALE_WHILE_REVALIDATE,
                isRecursive = false,
                status = CacheMetadata.STATUS_INVALID,
                committedSyncVersion = invalidVersion,
            ),
        )

        val gate = CompletableDeferred<Unit>()
        val repository = GatedFailureRepository(gate)
        val owner = async(Dispatchers.Default) {
            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = repository,
                forceRefresh = true,
            )
        }
        repository.started.await()

        // syncCache writes UPDATING metadata before invoking the repository. An
        // INVALID marker must be cleared at that transition; otherwise UPDATING's
        // non-invalid status makes the historical rows displayable again to a
        // concurrent caller (and after a process death in this window).
        val updating = assertNotNull(db.cacheMetadataDao().getBySource(sourceType, sourceKey))
        assertEquals(CacheMetadata.STATUS_UPDATING, updating.status)
        assertEquals(0L, updating.committedSyncVersion)
        assertNull(service.resolveCommittedSyncVersion(sourceType, sourceKey))

        gate.complete(Unit)
        assertTrue(owner.await().isFailure)
        assertNull(service.resolveCommittedSyncVersion(sourceType, sourceKey))
    }

    private class EmptyRepository : BatchSourceRepository<UnSplashSource, NetworkFile> {
        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> = Result.success(0L)
    }

    private fun cachedImage(
        sourceType: String,
        sourceKey: String,
        path: String,
        syncVersion: Long,
    ) = CachedItem(
        sourceType = sourceType,
        sourceKey = sourceKey,
        parentPath = "/",
        name = path.substringAfterLast('/'),
        path = path,
        isDirectory = false,
        size = 1,
        mimeType = "image/jpeg",
        mediaKind = CACHED_ITEM_MEDIA_KIND_IMAGE,
        modifiedTime = 0,
        syncVersion = syncVersion,
    )

    private class PathRepository(
        private val source: UnSplashSource,
        private val paths: List<String>,
    ) : BatchSourceRepository<UnSplashSource, NetworkFile> {
        var invocations = 0
            private set

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> {
            invocations += 1
            onBatch(
                paths.map { path ->
                    NetworkFile(
                        remote = source,
                        path = path,
                        fileName = path.substringAfterLast('/'),
                        isDirectory = false,
                        size = 1,
                        mimeType = "image/jpeg",
                        modTime = "0",
                    )
                }
            )
            return Result.success(paths.size.toLong())
        }
    }

    private class GatedFailureRepository(
        private val gate: CompletableDeferred<Unit>,
    ) : BatchSourceRepository<UnSplashSource, NetworkFile> {
        val started = CompletableDeferred<Unit>()

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> {
            started.complete(Unit)
            gate.await()
            return Result.failure(Exception("network down"))
        }
    }

    private class AlwaysFailRepository(
        private val error: Exception = Exception("network down"),
    ) : BatchSourceRepository<UnSplashSource, NetworkFile> {
        var invocations = 0
            private set

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> {
            invocations++
            return Result.failure(error)
        }
    }

    private class SixtyThenFailRepository(
        private val source: UnSplashSource,
        private val failGate: CompletableDeferred<Unit>,
    ) : BatchSourceRepository<UnSplashSource, NetworkFile> {

        val firstBatchWritten = CompletableDeferred<Unit>()

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> {
            onBatch(
                (0 until 60).map { index ->
                    NetworkFile(
                        remote = source,
                        path = "https://images.example/partial-$index.jpg",
                        fileName = "partial-$index.jpg",
                        isDirectory = false,
                        size = 1,
                        mimeType = "image/jpeg",
                        modTime = "0",
                    )
                }
            )
            firstBatchWritten.complete(Unit)
            failGate.await()
            return Result.failure(Exception("rate limited"))
        }
    }

    private class TenThenSucceedRepository(
        private val source: UnSplashSource,
        private val gate: CompletableDeferred<Unit>,
    ) : BatchSourceRepository<UnSplashSource, NetworkFile> {

        val firstBatchWritten = CompletableDeferred<Unit>()

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> {
            onBatch(
                (0 until 10).map { index ->
                    NetworkFile(
                        remote = source,
                        path = "https://images.example/new-$index.jpg",
                        fileName = "new-$index.jpg",
                        isDirectory = false,
                        size = 1,
                        mimeType = "image/jpeg",
                        modTime = "0",
                    )
                }
            )
            firstBatchWritten.complete(Unit)
            gate.await()
            return Result.success(10L)
        }
    }

    private class FailAfterOneBatchRepository(
        private val source: UnSplashSource,
        private val failGate: CompletableDeferred<Unit>,
    ) : BatchSourceRepository<UnSplashSource, NetworkFile> {

        val firstBatchWritten = CompletableDeferred<Unit>()

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> {
            onBatch(
                listOf(
                    NetworkFile(
                        remote = source,
                        path = "https://images.example/failed-run.jpg",
                        fileName = "failed-run.jpg",
                        isDirectory = false,
                        size = 1,
                        mimeType = "image/jpeg",
                        modTime = "0",
                    )
                )
            )
            firstBatchWritten.complete(Unit)
            failGate.await()
            return Result.failure(Exception("rate limited"))
        }
    }

    private class FixedBatchRepository(
        private val source: UnSplashSource,
    ) : BatchSourceRepository<UnSplashSource, NetworkFile> {

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> {
            val batch = (0 until 3).map { index ->
                NetworkFile(
                    remote = source,
                    path = "https://images.example/$index.jpg",
                    fileName = "$index.jpg",
                    isDirectory = false,
                    size = 1,
                    mimeType = "image/jpeg",
                    modTime = "0",
                )
            }
            onBatch(batch)
            return Result.success(batch.size.toLong())
        }
    }
}
