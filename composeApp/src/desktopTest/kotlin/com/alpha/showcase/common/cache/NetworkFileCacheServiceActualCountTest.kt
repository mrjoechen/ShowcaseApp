package com.alpha.showcase.common.cache

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.repo.BatchSourceRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class NetworkFileCacheServiceActualCountTest {

    private lateinit var db: SourceCacheDatabase
    private lateinit var service: NetworkFileCacheService

    private val source = UnSplashSource(
        name = "actual-count",
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
    fun successfulSyncReportsOnlyRowsActuallyInserted() = runBlocking {
        val ready = service.ensureCacheReady(
            remoteApi = source,
            recursive = false,
            repository = DuplicatePathRepository(source, failAfterBatches = false),
            forceRefresh = true,
            supportVideo = false,
        ).getOrThrow()

        val completed = assertIs<CacheSyncResult.Completed>(ready.completion.await())
        assertEquals(3, completed.totalItems)
        assertPersistedTotalIsThree(ready.sourceType, ready.sourceKey)
    }

    @Test
    fun getOrLoadReturnsOnlyActuallyInsertedRowsInFirstInsertionOrder() = runBlocking {
        val files = service.getOrLoad(
            remoteApi = source,
            recursive = false,
            filter = null,
            repository = DuplicatePathRepository(source, failAfterBatches = false),
            forceRefresh = true,
        ).getOrThrow()

        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), files.map { it.fileName })
    }

    @Test
    fun partialFailureStoresOnlyRowsActuallyInsertedAsSurvivorCount() = runBlocking {
        val ready = service.ensureCacheReady(
            remoteApi = source,
            recursive = false,
            repository = DuplicatePathRepository(source, failAfterBatches = true),
            forceRefresh = true,
            supportVideo = false,
        ).getOrThrow()

        assertIs<CacheSyncResult.Failed>(ready.completion.await())
        assertPersistedTotalIsThree(ready.sourceType, ready.sourceKey)
    }

    private suspend fun assertPersistedTotalIsThree(sourceType: String, sourceKey: String) {
        val metadata = db.cacheMetadataDao().getBySource(sourceType, sourceKey)
        assertNotNull(metadata)
        assertEquals(3, metadata.totalItems)
        val committed = assertNotNull(metadata.committedSyncVersion.takeIf { it > 0 })
        assertEquals(3, db.cachedItemDao().getBySourceVersion(sourceType, sourceKey, committed).size)
    }

    private class DuplicatePathRepository(
        private val source: UnSplashSource,
        private val failAfterBatches: Boolean,
    ) : BatchSourceRepository<UnSplashSource, NetworkFile> {

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> {
            onBatch(listOf(image("a.jpg"), image("b.jpg")))
            onBatch(listOf(image("a.jpg"), image("c.jpg")))
            return if (failAfterBatches) {
                Result.failure(Exception("failed after duplicate batches"))
            } else {
                Result.success(4L)
            }
        }

        private fun image(name: String) = NetworkFile(
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
