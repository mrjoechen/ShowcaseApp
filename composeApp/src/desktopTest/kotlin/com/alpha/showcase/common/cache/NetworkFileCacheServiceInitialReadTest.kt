package com.alpha.showcase.common.cache

import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.alpha.showcase.common.cache.entity.CACHED_ITEM_MEDIA_KIND_IMAGE
import com.alpha.showcase.common.cache.entity.CacheMetadata
import com.alpha.showcase.common.cache.entity.CachedItem
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.repo.BatchSourceRepository
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkFileCacheServiceInitialReadTest {

    private lateinit var db: SourceCacheDatabase
    private lateinit var service: NetworkFileCacheService
    private lateinit var recordingDriver: RecordingSQLiteDriver

    private val source = UnSplashSource(
        name = "initial-read",
        photoType = "test",
        user = "user",
    )

    @BeforeTest
    fun setUp() {
        recordingDriver = RecordingSQLiteDriver(BundledSQLiteDriver())
        db = Room.inMemoryDatabaseBuilder<SourceCacheDatabase>()
            .setDriver(recordingDriver)
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        service = NetworkFileCacheService(db)
    }

    @AfterTest
    fun tearDown() = runBlocking {
        service.shutdownBackgroundRefreshes()
        db.close()
    }

    @Test
    fun invalidMetadataSkipsHistoricalRowsBeforeRepositoryStarts() = runBlocking {
        seedCache(CacheMetadata.STATUS_INVALID, committedVersion = 101L)

        assertNoCachedItemSelectBeforeRepositoryEntry(forceRefresh = false)
    }

    @Test
    fun zeroCommittedVersionSkipsAbandonedRowsBeforeRepositoryStarts() = runBlocking {
        seedCache(CacheMetadata.STATUS_UPDATING, committedVersion = 0L, rowVersion = 202L)

        assertNoCachedItemSelectBeforeRepositoryEntry(forceRefresh = false)
    }

    @Test
    fun forcedRefreshSkipsCommittedRowsBeforeRepositoryStarts() = runBlocking {
        seedCache(CacheMetadata.STATUS_VALID, committedVersion = 303L)

        assertNoCachedItemSelectBeforeRepositoryEntry(forceRefresh = true)
    }

    @Test
    fun freshValidCacheProducesRecordedVersionPinnedSelect() = runBlocking {
        seedCache(CacheMetadata.STATUS_VALID, committedVersion = 404L)
        val repository = EntryGatedRepository()

        val files = service.getOrLoad(
            remoteApi = source,
            recursive = false,
            filter = null,
            repository = repository,
        ).getOrThrow()

        assertEquals(listOf("https://images.example/cached.jpg"), files.map { it.path })
        assertTrue(!repository.entered.isCompleted, "fresh cache must not invoke the repository")
        val selects = recordingDriver.cachedItemSelects()
        assertEquals(1, selects.size, "positive control must observe the initial cached_items read")
        assertTrue(
            normalizeSql(selects.single()).contains("and sync_version = ?"),
            "the initial cache read must remain pinned to one committed generation",
        )
    }

    @Test
    fun swrVersionSwitchMaterializesOnlyFinalCommittedGeneration() = runBlocking {
        seedCache(CacheMetadata.STATUS_VALID, committedVersion = 505L)

        val replacementRepository = ReplacementGatedRepository(source)
        val replacement = async(Dispatchers.Default) {
            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = replacementRepository,
                forceRefresh = true,
            ).getOrThrow()
        }
        replacementRepository.batchWritten.await()
        recordingDriver.clear()

        // This stale reader reaches the refresh registry while the replacement
        // generation is still active. Commit that generation before it acquires
        // the registry, so the reader must choose between its old snapshot and the
        // newly committed one. It should materialize only the final choice.
        val readerAtAcquire = CompletableDeferred<Unit>()
        val allowReaderAcquire = CompletableDeferred<Unit>()
        service.beforeRefreshAcquireForTest = {
            service.beforeRefreshAcquireForTest = null
            readerAtAcquire.complete(Unit)
            allowReaderAcquire.await()
        }
        val unusedRepository = CountingEmptyRepository()
        val reader = async(Dispatchers.Default) {
            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = unusedRepository,
            ).getOrThrow()
        }
        readerAtAcquire.await()

        replacementRepository.allowCompletion.complete(Unit)
        val replacementFiles = replacement.await()
        allowReaderAcquire.complete(Unit)
        val readerFiles = reader.await()

        assertEquals(replacementFiles.map { it.path }, readerFiles.map { it.path })
        assertEquals(0, unusedRepository.invocations.get())
        assertEquals(
            1,
            recordingDriver.cachedItemSelects().size,
            "one getOrLoad call must not materialize both the old and new generations",
        )
        val existenceChecks = recordingDriver.cachedItemExistenceChecks()
        assertEquals(1, existenceChecks.size, "stale presence check must stop at the first row")
        assertTrue(normalizeSql(existenceChecks.single()).contains("sync_version = ?"))
    }

    @Test
    fun shutdownBackgroundRefreshesCancelsAndJoinsAlreadyFreshSettlement() = runBlocking {
        seedCache(CacheMetadata.STATUS_VALID, committedVersion = 606L)

        val replacementRepository = ReplacementGatedRepository(source)
        val readerAtAcquire = CompletableDeferred<Unit>()
        val allowReaderAcquire = CompletableDeferred<Unit>()
        val settlementEntered = CompletableDeferred<Unit>()
        val settlementCancellationObserved = CompletableDeferred<Unit>()
        val abortSettlement = CompletableDeferred<Unit>()
        val allowCancelledSettlementToFinish = CompletableDeferred<Unit>()

        try {
            val replacement = async(Dispatchers.Default) {
                service.getOrLoad(
                    remoteApi = source,
                    recursive = false,
                    filter = null,
                    repository = replacementRepository,
                    forceRefresh = true,
                ).getOrThrow()
            }
            withTimeout(TEST_STAGE_TIMEOUT_MILLIS) {
                replacementRepository.batchWritten.await()
            }

            service.beforeRefreshAcquireForTest = {
                service.beforeRefreshAcquireForTest = null
                readerAtAcquire.complete(Unit)
                withTimeout(TEST_STAGE_TIMEOUT_MILLIS) {
                    allowReaderAcquire.await()
                }
            }

            service.beforeAlreadyFreshSettlementForTest = {
                service.beforeAlreadyFreshSettlementForTest = null
                settlementEntered.complete(Unit)
                try {
                    // Only cancellation from shutdown may reach the normal-path
                    // finally below. [abortSettlement] is completed exclusively
                    // by the outer failure cleanup, so a broken "join only"
                    // shutdown times out instead of being rescued into a pass.
                    abortSettlement.await()
                } finally {
                    withContext(NonCancellable) {
                        settlementCancellationObserved.complete(Unit)
                        withTimeout(TEST_STAGE_TIMEOUT_MILLIS) {
                            allowCancelledSettlementToFinish.await()
                        }
                    }
                }
            }

            val reader = async(Dispatchers.Default) {
                service.getOrLoad(
                    remoteApi = source,
                    recursive = false,
                    filter = null,
                    repository = CountingEmptyRepository(),
                ).getOrThrow()
            }
            withTimeout(TEST_STAGE_TIMEOUT_MILLIS) {
                readerAtAcquire.await()
            }

            replacementRepository.allowCompletion.complete(Unit)
            withTimeout(TEST_STAGE_TIMEOUT_MILLIS) {
                replacement.await()
            }
            allowReaderAcquire.complete(Unit)
            withTimeout(TEST_STAGE_TIMEOUT_MILLIS) {
                reader.await()
            }
            withTimeout(TEST_STAGE_TIMEOUT_MILLIS) {
                settlementEntered.await()
            }

            val shutdown = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                service.shutdownBackgroundRefreshes()
            }
            withTimeout(TEST_STAGE_TIMEOUT_MILLIS) {
                settlementCancellationObserved.await()
            }
            assertFalse(
                shutdown.isCompleted,
                "shutdown must join cancelled refresh settlement before the database can close",
            )
            allowCancelledSettlementToFinish.complete(Unit)
            withTimeout(TEST_STAGE_TIMEOUT_MILLIS) {
                shutdown.await()
            }
        } finally {
            service.beforeRefreshAcquireForTest = null
            service.beforeAlreadyFreshSettlementForTest = null
            replacementRepository.allowCompletion.complete(Unit)
            allowReaderAcquire.complete(Unit)
            abortSettlement.complete(Unit)
            allowCancelledSettlementToFinish.complete(Unit)
        }
    }

    private suspend fun seedCache(
        status: String,
        committedVersion: Long,
        rowVersion: Long = committedVersion,
    ) {
        val sourceType = service.resolveSourceType(source)
        val sourceKey = service.resolveSourceKey(source, recursive = false)
        db.cachedItemDao().insertOrIgnore(
            listOf(
                CachedItem(
                    sourceType = sourceType,
                    sourceKey = sourceKey,
                    parentPath = "/",
                    name = "cached.jpg",
                    path = "https://images.example/cached.jpg",
                    isDirectory = false,
                    size = 1,
                    mimeType = "image/jpeg",
                    mediaKind = CACHED_ITEM_MEDIA_KIND_IMAGE,
                    modifiedTime = 1,
                    syncVersion = rowVersion,
                )
            )
        )
        db.cacheMetadataDao().insertOrReplace(
            CacheMetadata(
                sourceType = sourceType,
                sourceKey = sourceKey,
                lastUpdated = 1,
                nextUpdateTime = Long.MAX_VALUE,
                totalItems = 1,
                updateStrategy = CacheMetadata.STRATEGY_STALE_WHILE_REVALIDATE,
                isRecursive = false,
                status = status,
                committedSyncVersion = committedVersion,
            )
        )
        recordingDriver.clear()
    }

    private suspend fun assertNoCachedItemSelectBeforeRepositoryEntry(forceRefresh: Boolean) =
        coroutineScope {
            val repository = EntryGatedRepository()
            val request = async(Dispatchers.Default) {
                service.getOrLoad(
                    remoteApi = source,
                    recursive = false,
                    filter = null,
                    repository = repository,
                    forceRefresh = forceRefresh,
                )
            }

            repository.entered.await()
            try {
                assertTrue(
                    recordingDriver.cachedItemSelects().isEmpty(),
                    "cached_items rows were materialized before the repository started",
                )
            } finally {
                repository.release.complete(Unit)
            }
            request.await().getOrThrow()
            Unit
        }

    private class EntryGatedRepository : BatchSourceRepository<UnSplashSource, NetworkFile> {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> {
            entered.complete(Unit)
            release.await()
            return Result.success(0L)
        }
    }

    private class ReplacementGatedRepository(
        private val source: UnSplashSource,
    ) : BatchSourceRepository<UnSplashSource, NetworkFile> {
        val batchWritten = CompletableDeferred<Unit>()
        val allowCompletion = CompletableDeferred<Unit>()

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> {
            val item = NetworkFile(
                remote = source,
                path = "https://images.example/replacement.jpg",
                fileName = "replacement.jpg",
                isDirectory = false,
                size = 1,
                mimeType = "image/jpeg",
                modTime = "2",
            )
            onBatch(listOf(item))
            batchWritten.complete(Unit)
            allowCompletion.await()
            return Result.success(1L)
        }
    }

    private class CountingEmptyRepository : BatchSourceRepository<UnSplashSource, NetworkFile> {
        val invocations = AtomicInteger(0)

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> {
            invocations.incrementAndGet()
            return Result.success(0L)
        }
    }

    private class RecordingSQLiteDriver(
        private val delegate: SQLiteDriver,
    ) : SQLiteDriver {
        private val statements = CopyOnWriteArrayList<String>()

        override val hasConnectionPool: Boolean
            get() = delegate.hasConnectionPool

        override fun open(fileName: String): SQLiteConnection =
            RecordingSQLiteConnection(delegate.open(fileName), statements::add)

        fun clear() {
            statements.clear()
        }

        fun cachedItemSelects(): List<String> = statements.filter {
            normalizeSql(it).startsWith("select * from cached_items ")
        }

        fun cachedItemExistenceChecks(): List<String> = statements.filter {
            val normalized = normalizeSql(it)
            normalized.startsWith("select exists(") && normalized.contains(" from cached_items ")
        }
    }

    private class RecordingSQLiteConnection(
        private val delegate: SQLiteConnection,
        private val record: (String) -> Unit,
    ) : SQLiteConnection by delegate {
        override fun prepare(sql: String): SQLiteStatement =
            RecordingSQLiteStatement(delegate.prepare(sql), sql, record)
    }

    private class RecordingSQLiteStatement(
        private val delegate: SQLiteStatement,
        private val sql: String,
        private val record: (String) -> Unit,
    ) : SQLiteStatement by delegate {
        private var executionRecorded = false

        override fun step(): Boolean {
            if (!executionRecorded) {
                record(sql)
                executionRecorded = true
            }
            return delegate.step()
        }

        override fun reset() {
            delegate.reset()
            executionRecorded = false
        }
    }

    companion object {
        private const val TEST_STAGE_TIMEOUT_MILLIS = 5_000L
        private val whitespace = Regex("\\s+")

        private fun normalizeSql(sql: String): String =
            sql.trim().replace(whitespace, " ").lowercase()
    }
}
