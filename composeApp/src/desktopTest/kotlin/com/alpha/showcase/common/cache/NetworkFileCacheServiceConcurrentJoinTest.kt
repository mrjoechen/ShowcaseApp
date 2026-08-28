package com.alpha.showcase.common.cache

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.alpha.showcase.common.cache.entity.CacheMetadata
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.repo.BatchSourceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Two concurrent same-source callers must share ONE sync: the second caller
 * arrives while the first sync is mid-flight (metadata UPDATING, nothing
 * committed) and has to JOIN it — not treat the UPDATING metadata as usable
 * cache (instant empty page) and not start a competing sync that would
 * interleave version rows with the first one.
 */
class NetworkFileCacheServiceConcurrentJoinTest {

    private lateinit var db: SourceCacheDatabase
    private lateinit var service: NetworkFileCacheService

    private val source = UnSplashSource(
        name = "concurrent-join",
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

    @Test
    fun concurrentSameSourceCallersShareOneSyncAndOneCompletion() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val repository = GatedRepository(source, gate)

        // Kick off the FIRST sync on a real clock. These are Room integration tests:
        // a virtual runTest clock can fast-forward Room's connection-acquire timeout
        // while the real-dispatcher writer is only briefly holding the single
        // in-memory connection, creating a test-only timeout/logging storm.
        val starter = async {
            service.ensureCacheReady(
                remoteApi = source,
                recursive = false,
                repository = repository,
                supportVideo = false,
            ).getOrThrow()
        }
        repository.firstBatchWritten.await()

        // Two callers arrive while that first sync is mid-flight (metadata is
        // UPDATING, nothing committed, 60 rows already present): both must JOIN.
        val first = async {
            service.ensureCacheReady(
                remoteApi = source,
                recursive = false,
                repository = repository,
                supportVideo = false,
            ).getOrThrow()
        }
        val second = async {
            service.ensureCacheReady(
                remoteApi = source,
                recursive = false,
                repository = repository,
                supportVideo = false,
            ).getOrThrow()
        }

        val readyA = first.await()
        val readyB = second.await()
        assertSame(readyA.completion, starter.await().completion)

        // Exactly one traversal of the source, one shared completion handle.
        assertEquals(1, repository.invocations.get())
        assertSame(readyA.completion, readyB.completion)

        // Both callers pinned to the same in-flight snapshot version (nothing is
        // committed yet), reading in append-stable insertion order.
        assertNotNull(readyA.committedSyncVersion)
        assertEquals(readyA.committedSyncVersion, readyB.committedSyncVersion)
        assertTrue(readyA.initialSnapshot)
        assertTrue(readyB.initialSnapshot)

        gate.complete(Unit)
        assertIs<CacheSyncResult.Completed>(readyA.completion.await())
        assertEquals(
            60,
            service.countMedia(
                readyA.sourceType,
                readyA.sourceKey,
                supportVideo = false,
                syncVersion = readyA.committedSyncVersion,
            )
        )
    }

    @Test
    fun concurrentGetOrLoadWaitsForOwnerThenReloadsCommittedRows() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val repository = BeforeFirstBatchGatedRepository(source, gate)

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

        val joiner = async(Dispatchers.Default) {
            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = repository,
                forceRefresh = true,
            )
        }

        // Give the joiner enough real-dispatcher time to reach refreshNow. It must
        // stay suspended on the owner's terminal handle while no rows exist yet.
        withContext(Dispatchers.Default) { delay(100) }
        assertFalse(joiner.isCompleted, "a first-sync joiner must not return an empty success")

        gate.complete(Unit)
        val ownerFiles = owner.await().getOrThrow()
        val joinedFiles = joiner.await().getOrThrow()

        assertEquals(1, repository.invocations.get())
        assertEquals(ownerFiles.map { it.path }, joinedFiles.map { it.path })
        assertEquals(3, joinedFiles.size)
    }

    @Test
    fun defaultGetOrLoadWaitsWhenFirstSyncHasOnlyActiveRows() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val repository = GatedRepository(source, gate)

        val owner = async(Dispatchers.Default) {
            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = repository,
                forceRefresh = true,
            )
        }
        repository.firstBatchWritten.await()

        // The first batch is visible in the active generation, but metadata is
        // still UPDATING and there is no committed generation. A default caller
        // must join the owner instead of treating those rows as stale cache.
        val joiner = async(Dispatchers.Default) {
            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = repository,
            )
        }

        withContext(Dispatchers.Default) { delay(100) }
        assertFalse(joiner.isCompleted, "uncommitted active rows must not be returned as stale cache")

        gate.complete(Unit)
        val ownerFiles = owner.await().getOrThrow()
        val joinedFiles = joiner.await().getOrThrow()

        assertEquals(1, repository.invocations.get())
        assertEquals(ownerFiles.map { it.path }.toSet(), joinedFiles.map { it.path }.toSet())
        assertEquals(60, joinedFiles.size)
    }

    @Test
    fun nonForcedOwnerRechecksFreshnessAfterPreviousRunLeavesRegistry() = runBlocking {
        // Establish a displayable generation. C1 below is forced so it starts even
        // though this cache is fresh, then keeps the old generation visible while
        // its replacement is being written.
        service.getOrLoad(
            remoteApi = source,
            recursive = false,
            filter = null,
            repository = CountingFixedRepository(source),
            forceRefresh = true,
        ).getOrThrow()

        val c1Gate = CompletableDeferred<Unit>()
        val c1Repository = GatedRepository(source, c1Gate)
        val c1 = async(Dispatchers.Default) {
            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = c1Repository,
                forceRefresh = true,
            )
        }
        c1Repository.firstBatchWritten.await()

        // B has already observed C1's UPDATING metadata, but is paused before it
        // acquires the registry. Let C1 commit and fully release first. B will then
        // become owner based on a stale decision; it must re-read freshness before
        // invoking its repository or it immediately performs a redundant C2 sync.
        val bReachedAcquire = CompletableDeferred<Unit>()
        val allowBAcquire = CompletableDeferred<Unit>()
        service.beforeRefreshAcquireForTest = {
            service.beforeRefreshAcquireForTest = null
            bReachedAcquire.complete(Unit)
            allowBAcquire.await()
        }
        val c2Repository = CountingFixedRepository(source)
        val b = async(Dispatchers.Default) {
            service.ensureCacheReady(
                remoteApi = source,
                recursive = false,
                repository = c2Repository,
                supportVideo = false,
            ).getOrThrow()
        }
        bReachedAcquire.await()

        c1Gate.complete(Unit)
        c1.await().getOrThrow() // refreshNow returns only after C1 releases its run.
        val latestCommitted = assertNotNull(
            service.resolveCommittedSyncVersion(
                service.resolveSourceType(source),
                service.resolveSourceKey(source, recursive = false),
            )
        )

        allowBAcquire.complete(Unit)
        val ready = b.await()

        assertIs<CacheSyncResult.AlreadyFresh>(ready.completion.await())
        assertEquals(0, c2Repository.invocations.get())
        assertEquals(latestCommitted, ready.committedSyncVersion)
    }

    @Test
    fun nonForcedGetOrLoadOwnerRechecksFreshnessBeforeFirstSyncReplacement() = runBlocking {
        val c1Gate = CompletableDeferred<Unit>()
        val c1Repository = GatedRepository(source, c1Gate)
        val c1 = async(Dispatchers.Default) {
            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = c1Repository,
                forceRefresh = true,
            )
        }
        c1Repository.firstBatchWritten.await()

        // With no committed cache, B takes getOrLoad's synchronous refreshNow
        // path. Pause it after reading C1's uncommitted metadata, then let C1
        // commit and release. B must consume that fresh generation instead of
        // becoming a redundant replacement owner.
        val bReachedAcquire = CompletableDeferred<Unit>()
        val allowBAcquire = CompletableDeferred<Unit>()
        service.beforeRefreshAcquireForTest = {
            service.beforeRefreshAcquireForTest = null
            bReachedAcquire.complete(Unit)
            allowBAcquire.await()
        }
        val c2Repository = CountingFixedRepository(source)
        val b = async(Dispatchers.Default) {
            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = c2Repository,
            )
        }
        bReachedAcquire.await()

        c1Gate.complete(Unit)
        val c1Files = c1.await().getOrThrow()
        allowBAcquire.complete(Unit)
        val bFiles = b.await().getOrThrow()

        assertEquals(0, c2Repository.invocations.get())
        assertEquals(c1Files.map { it.path }.toSet(), bFiles.map { it.path }.toSet())
        assertEquals(60, bFiles.size)
    }

    @Test
    fun staleKnownEmptyGetOrLoadAdoptsVersionCommittedBeforeRefreshAcquire() = runBlocking {
        val initial = service.getOrLoad(
            remoteApi = source,
            recursive = false,
            filter = null,
            repository = EmptyRepository(),
            forceRefresh = true,
        ).getOrThrow()
        assertTrue(initial.isEmpty())

        val c1Gate = CompletableDeferred<Unit>()
        val c1Repository = GatedRepository(source, c1Gate)
        val c1 = async(Dispatchers.Default) {
            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = c1Repository,
                forceRefresh = true,
            )
        }
        c1Repository.firstBatchWritten.await()

        // B snapshots the still-committed empty generation, then pauses before
        // joining/starting its background refresh. C1 commits 60 displayable rows
        // in that gap. B should return those rows, not the obsolete empty snapshot.
        val bReachedAcquire = CompletableDeferred<Unit>()
        val allowBAcquire = CompletableDeferred<Unit>()
        service.beforeRefreshAcquireForTest = {
            service.beforeRefreshAcquireForTest = null
            bReachedAcquire.complete(Unit)
            allowBAcquire.await()
        }
        val c2Repository = CountingFixedRepository(source)
        val b = async(Dispatchers.Default) {
            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = c2Repository,
            ).getOrThrow()
        }
        bReachedAcquire.await()

        c1Gate.complete(Unit)
        val c1Files = c1.await().getOrThrow()
        allowBAcquire.complete(Unit)
        val bFiles = b.await()

        assertEquals(c1Files.map { it.path }.toSet(), bFiles.map { it.path }.toSet())
        assertEquals(60, bFiles.size)
    }

    @Test
    fun forcedJoinerUpgradesOwnerBeforeAlreadyFreshSettlement() = runBlocking {
        service.getOrLoad(
            remoteApi = source,
            recursive = false,
            filter = null,
            repository = CountingFixedRepository(source),
            forceRefresh = true,
        ).getOrThrow()

        val sourceType = service.resolveSourceType(source)
        val sourceKey = service.resolveSourceKey(source, recursive = false)
        val metadataDao = db.cacheMetadataDao()
        val committed = assertNotNull(metadataDao.getBySource(sourceType, sourceKey))
        metadataDao.insertOrReplace(committed.copy(nextUpdateTime = 1))

        // Make the non-forced caller decide from stale metadata, then make that
        // same generation fresh before it acquires the registry. It becomes a
        // no-op candidate and pauses immediately before settling AlreadyFresh.
        val ownerAtAcquire = CompletableDeferred<Unit>()
        val allowOwnerAcquire = CompletableDeferred<Unit>()
        service.beforeRefreshAcquireForTest = {
            service.beforeRefreshAcquireForTest = null
            ownerAtAcquire.complete(Unit)
            allowOwnerAcquire.await()
        }
        val ownerAtSettlement = CompletableDeferred<Unit>()
        val allowOwnerSettlement = CompletableDeferred<Unit>()
        service.beforeAlreadyFreshSettlementForTest = {
            service.beforeAlreadyFreshSettlementForTest = null
            ownerAtSettlement.complete(Unit)
            allowOwnerSettlement.await()
        }

        val ownerRepository = CountingFixedRepository(source)
        val nonForced = async(Dispatchers.Default) {
            service.ensureCacheReady(
                remoteApi = source,
                recursive = false,
                repository = ownerRepository,
                supportVideo = false,
            ).getOrThrow()
        }
        ownerAtAcquire.await()
        metadataDao.insertOrReplace(committed.copy(nextUpdateTime = Long.MAX_VALUE))
        allowOwnerAcquire.complete(Unit)
        val nonForcedReady = nonForced.await()
        ownerAtSettlement.await()

        // A forced caller joins while the owner is about to no-op. Its intent must
        // upgrade the shared run so the owner performs a real traversal instead of
        // resolving both callers as AlreadyFresh.
        val forcedAcquired = CompletableDeferred<Unit>()
        val allowForcedAfterAcquire = CompletableDeferred<Unit>()
        service.afterRefreshAcquireForTest = {
            service.afterRefreshAcquireForTest = null
            forcedAcquired.complete(Unit)
            allowForcedAfterAcquire.await()
        }
        val forcedRepository = CountingFixedRepository(source)
        val forced = async(Dispatchers.Default) {
            service.ensureCacheReady(
                remoteApi = source,
                recursive = false,
                repository = forcedRepository,
                forceRefresh = true,
                supportVideo = false,
            ).getOrThrow()
        }
        forcedAcquired.await()
        allowOwnerSettlement.complete(Unit)
        allowForcedAfterAcquire.complete(Unit)

        val terminal = nonForcedReady.completion.await()
        val forcedReady = forced.await()
        assertSame(nonForcedReady.completion, forcedReady.completion)
        assertIs<CacheSyncResult.Completed>(terminal)
        assertEquals(1, ownerRepository.invocations.get())
        assertEquals(0, forcedRepository.invocations.get())
    }

    @Test
    fun lateForcedCallerReplacesSettledRunAndOldReleasePreservesSuccessor() = runBlocking {
        val ownerAtAcquire = CompletableDeferred<Unit>()
        val allowOwnerAcquire = CompletableDeferred<Unit>()
        val ownerSettled = CompletableDeferred<Unit>()
        val allowOwnerRelease = CompletableDeferred<Unit>()
        val forcedAcquired = CompletableDeferred<Unit>()
        val allowForcedAfterAcquire = CompletableDeferred<Unit>()
        val forcedGate = CompletableDeferred<Unit>()

        service.beforeRefreshAcquireForTest = {
            service.beforeRefreshAcquireForTest = null
            ownerAtAcquire.complete(Unit)
            allowOwnerAcquire.await()
        }
        service.afterAlreadyFreshSettlementForTest = {
            service.afterAlreadyFreshSettlementForTest = null
            ownerSettled.complete(Unit)
            allowOwnerRelease.await()
        }

        try {
            // This caller reads an empty database, then pauses before registering.
            // A separate forced run commits a fresh generation in that gap, so the
            // paused caller later becomes an AlreadyFresh no-op owner.
            val oldOwnerRepository = CountingFixedRepository(source)
            val oldOwner = async(Dispatchers.Default) {
                service.getOrLoad(
                    remoteApi = source,
                    recursive = false,
                    filter = null,
                    repository = oldOwnerRepository,
                )
            }
            ownerAtAcquire.await()

            val bootstrapGate = CompletableDeferred<Unit>().apply { complete(Unit) }
            val bootstrapRepository = GatedRepository(source, bootstrapGate)
            val bootstrapFiles = service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = bootstrapRepository,
                forceRefresh = true,
            ).getOrThrow()
            assertEquals(60, bootstrapFiles.size)

            allowOwnerAcquire.complete(Unit)
            ownerSettled.await()

            // The old run is settled but deliberately still registered. A forced
            // request must replace it with a real successor instead of joining its
            // AlreadyFresh completion.
            service.afterRefreshAcquireForTest = {
                service.afterRefreshAcquireForTest = null
                forcedAcquired.complete(Unit)
                allowForcedAfterAcquire.await()
            }
            val forcedRepository = GatedRepository(source, forcedGate)
            val forced = async(Dispatchers.Default) {
                service.ensureCacheReady(
                    remoteApi = source,
                    recursive = false,
                    repository = forcedRepository,
                    forceRefresh = true,
                    supportVideo = false,
                ).getOrThrow()
            }
            forcedAcquired.await()

            // Let the old owner's finally block run after the successor is already
            // installed. Awaiting getOrLoad proves that identity-checked release is
            // complete before the next non-forced caller inspects the registry.
            allowOwnerRelease.complete(Unit)
            oldOwner.await().getOrThrow()

            val joinerRepository = CountingFixedRepository(source)
            val joinerReady = service.ensureCacheReady(
                remoteApi = source,
                recursive = false,
                repository = joinerRepository,
                supportVideo = false,
            ).getOrThrow()
            assertFalse(
                joinerReady.completion.isCompleted,
                "old release must not erase the forced successor and expose fake AlreadyFresh",
            )

            allowForcedAfterAcquire.complete(Unit)
            val forcedReady = forced.await()
            assertSame(forcedReady.completion, joinerReady.completion)

            forcedRepository.firstBatchWritten.await()
            assertFalse(forcedReady.completion.isCompleted)
            assertEquals(0, oldOwnerRepository.invocations.get())
            assertEquals(0, joinerRepository.invocations.get())
            assertEquals(1, forcedRepository.invocations.get())

            forcedGate.complete(Unit)
            assertIs<CacheSyncResult.Completed>(forcedReady.completion.await())
        } finally {
            service.beforeRefreshAcquireForTest = null
            service.beforeAlreadyFreshSettlementForTest = null
            service.afterAlreadyFreshSettlementForTest = null
            service.afterRefreshAcquireForTest = null
            allowOwnerAcquire.complete(Unit)
            allowOwnerRelease.complete(Unit)
            allowForcedAfterAcquire.complete(Unit)
            forcedGate.complete(Unit)
        }
        Unit
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun failedRunCallerNeverPinsNextRunsActiveVersion() = runBlocking {
        val repository = AbaRepository(source)
        val c1CallerScheduler = TestCoroutineScheduler()
        val c1CallerScope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(c1CallerScheduler)
        )

        // getOrLoad owns C1 synchronously, which lets the test await its return as
        // proof that C1's registry cleanup has finished.
        val c1Owner = async(Dispatchers.Default) {
            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = repository,
                forceRefresh = true,
            )
        }
        repository.c1Started.await()

        // This caller joins C1 and enters the first-sync readiness poll.
        val c1Caller = c1CallerScope.async {
            service.ensureCacheReady(
                remoteApi = source,
                recursive = false,
                repository = repository,
                forceRefresh = true,
                supportVideo = false,
            )
        }
        c1CallerScheduler.runCurrent()
        withContext(Dispatchers.Default) { delay(100) }
        c1CallerScheduler.runCurrent()
        assertFalse(c1Caller.isCompleted)

        repository.allowC1Failure.complete(Unit)
        assertTrue(c1Owner.await().isFailure)

        // C1 is now fully cleaned up. Register C2 and leave its 60-row version
        // active so C1's caller performs its final pin read during the ABA window.
        val c2Owner = async(Dispatchers.Default) {
            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = repository,
                forceRefresh = true,
            )
        }
        repository.c2BatchWritten.await()

        try {
            // The C1 caller's virtual clock has stayed frozen while C1 cleaned up
            // and C2 became active. Wake exactly its next readiness poll now.
            c1CallerScheduler.advanceTimeBy(300)
            var attempts = 0
            while (!c1Caller.isCompleted && attempts < 100) {
                c1CallerScheduler.runCurrent()
                if (!c1Caller.isCompleted) {
                    withContext(Dispatchers.Default) { delay(10) }
                }
                attempts++
            }
            assertTrue(c1Caller.isCompleted, "C1 caller did not settle after its next poll")
            assertTrue(
                c1Caller.await().isFailure,
                "a caller holding C1 completion must not return C2's active version",
            )
        } finally {
            repository.allowC2Completion.complete(Unit)
            c2Owner.await().getOrThrow()
            c1CallerScope.cancel()
        }
    }

    @Test
    fun cancelledOwnerReleasesRunSoNextRefreshCanStart() = runBlocking {
        val cancelledRepository = CancellationGatedRepository()
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val owner = ownerScope.async {
            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = cancelledRepository,
                forceRefresh = true,
            )
        }
        cancelledRepository.started.await()

        // Force releaseRun down Mutex's suspending path. Without non-cancellable
        // cleanup, the cancelled owner exits before removing its completed run.
        service.withRefreshRegistryLockedForTest {
            owner.cancel()
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(1_000) { owner.join() }
            }
        }
        owner.cancelAndJoin()

        val nextRepository = CountingFixedRepository(source)
        val files = service.getOrLoad(
            remoteApi = source,
            recursive = false,
            filter = null,
            repository = nextRepository,
            forceRefresh = true,
        ).getOrThrow()

        assertEquals(1, nextRepository.invocations.get())
        assertEquals(3, files.size)
        ownerScope.cancel()
    }

    @Test
    fun cancelledOwnerAfterBatchSettlesPartialGeneration() = runBlocking {
        val repository = CancellationAfterBatchRepository(source)
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val owner = ownerScope.async {
            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = repository,
                forceRefresh = true,
            )
        }
        repository.batchWritten.await()

        owner.cancelAndJoin()

        val sourceType = service.resolveSourceType(source)
        val sourceKey = service.resolveSourceKey(source, recursive = false)
        val metadata = assertNotNull(db.cacheMetadataDao().getBySource(sourceType, sourceKey))
        val committed = assertNotNull(metadata.committedSyncVersion.takeIf { it > 0 })
        assertEquals(CacheMetadata.STATUS_VALID, metadata.status)
        assertEquals(3, metadata.totalItems)
        assertEquals(3, db.cachedItemDao().getBySourceVersion(sourceType, sourceKey, committed).size)
        ownerScope.cancel()
    }

    private class GatedRepository(
        private val source: UnSplashSource,
        private val gate: CompletableDeferred<Unit>,
    ) : BatchSourceRepository<UnSplashSource, NetworkFile> {

        val invocations = AtomicInteger(0)
        val firstBatchWritten = CompletableDeferred<Unit>()

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> {
            invocations.incrementAndGet()
            val batch = (0 until 60).map { index ->
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
            firstBatchWritten.complete(Unit)

            gate.await()
            return Result.success(60L)
        }
    }

    private class BeforeFirstBatchGatedRepository(
        private val source: UnSplashSource,
        private val gate: CompletableDeferred<Unit>,
    ) : BatchSourceRepository<UnSplashSource, NetworkFile> {

        val invocations = AtomicInteger(0)
        val started = CompletableDeferred<Unit>()

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> {
            invocations.incrementAndGet()
            started.complete(Unit)
            gate.await()
            val batch = (0 until 3).map { index -> image(source, "joined-$index.jpg") }
            onBatch(batch)
            return Result.success(batch.size.toLong())
        }
    }

    private class AbaRepository(
        private val source: UnSplashSource,
    ) : BatchSourceRepository<UnSplashSource, NetworkFile> {

        private val invocations = AtomicInteger(0)
        val c1Started = CompletableDeferred<Unit>()
        val allowC1Failure = CompletableDeferred<Unit>()
        val c2BatchWritten = CompletableDeferred<Unit>()
        val allowC2Completion = CompletableDeferred<Unit>()

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> = when (val invocation = invocations.incrementAndGet()) {
            1 -> {
                c1Started.complete(Unit)
                allowC1Failure.await()
                Result.failure(Exception("C1 failed"))
            }
            2 -> {
                val batch = (0 until 60).map { index -> image(source, "c2-$index.jpg") }
                onBatch(batch)
                c2BatchWritten.complete(Unit)
                allowC2Completion.await()
                Result.success(batch.size.toLong())
            }
            else -> error("unexpected sync invocation $invocation")
        }
    }

    private class CancellationGatedRepository :
        BatchSourceRepository<UnSplashSource, NetworkFile> {

        val started = CompletableDeferred<Unit>()

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> {
            started.complete(Unit)
            awaitCancellation()
        }
    }

    private class CancellationAfterBatchRepository(
        private val source: UnSplashSource,
    ) : BatchSourceRepository<UnSplashSource, NetworkFile> {

        val batchWritten = CompletableDeferred<Unit>()

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> {
            val batch = (0 until 3).map { index -> image(source, "before-cancel-$index.jpg") }
            onBatch(batch)
            batchWritten.complete(Unit)
            awaitCancellation()
        }
    }

    private class CountingFixedRepository(
        private val source: UnSplashSource,
    ) : BatchSourceRepository<UnSplashSource, NetworkFile> {

        val invocations = AtomicInteger(0)

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> {
            invocations.incrementAndGet()
            val batch = (0 until 3).map { index -> image(source, "after-cancel-$index.jpg") }
            onBatch(batch)
            return Result.success(batch.size.toLong())
        }
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

    private companion object {
        fun image(source: UnSplashSource, name: String) = NetworkFile(
            remote = source,
            path = "https://images.example/$name",
            fileName = name,
            isDirectory = false,
            size = 1,
            mimeType = "image/jpeg",
            modTime = "0",
        )
    }
}
