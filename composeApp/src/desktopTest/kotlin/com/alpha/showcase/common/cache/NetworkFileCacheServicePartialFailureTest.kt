package com.alpha.showcase.common.cache

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.alpha.showcase.api.pexels.Pagination
import com.alpha.showcase.api.pexels.Photo as PexelsPhoto
import com.alpha.showcase.api.pexels.Src
import com.alpha.showcase.api.unsplash.Photo as UnsplashPhoto
import com.alpha.showcase.api.unsplash.PhotoUrls
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.repo.BatchSourceRepository
import com.alpha.showcase.common.repo.PexelsSourceRepo
import com.alpha.showcase.common.repo.PexelsSourceType
import com.alpha.showcase.common.repo.UnSplashSourceType
import com.alpha.showcase.common.repo.UnsplashRepo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class NetworkFileCacheServicePartialFailureTest {

    private lateinit var db: SourceCacheDatabase
    private lateinit var service: NetworkFileCacheService

    private val source = UnSplashSource(
        name = "partial-failure",
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
    fun firstSyncPartialFailureKeepsAlreadyDisplayedRows() = runBlocking {
        val failGate = CompletableDeferred<Unit>()
        val repository = PartialThenFailRepository(source, failGate)

        val readyDeferred = async(Dispatchers.Default) {
            service.ensureCacheReady(
                remoteApi = source,
                recursive = false,
                repository = repository,
                supportVideo = false,
            ).getOrThrow()
        }

        repository.firstBatchWritten.await()
        val ready = readyDeferred.await()
        var terminal: CacheSyncResult? = null
        try {
            val activePin = assertNotNull(ready.committedSyncVersion)
            assertEquals(
                60,
                service.countMedia(
                    ready.sourceType,
                    ready.sourceKey,
                    supportVideo = false,
                    syncVersion = activePin,
                )
            )
        } finally {
            failGate.complete(Unit)
            terminal = ready.completion.await()
        }
        assertIs<CacheSyncResult.Failed>(terminal)

        val committedVersion = service.resolveCommittedSyncVersion(ready.sourceType, ready.sourceKey)
        assertNotNull(committedVersion)
        assertEquals(
            60,
            service.countMedia(
                ready.sourceType,
                ready.sourceKey,
                supportVideo = false,
                syncVersion = committedVersion,
            )
        )
    }

    @Test
    fun unsplashLaterPageFailureKeepsExistingCommittedGeneration() = runBlocking {
        val source = UnSplashSource(
            name = "unsplash-partial-refresh",
            photoType = UnSplashSourceType.FeedPhotos.type,
        )
        val initialRepository = UnsplashRepo(
            pageLoader = { _, page, _ ->
                if (page == 1) {
                    listOf(unsplashPhoto("old-one"), unsplashPhoto("old-two"))
                } else {
                    emptyList()
                }
            },
            maxPages = 10,
        )

        val initial = service.getOrLoad(
            remoteApi = source,
            recursive = false,
            filter = null,
            repository = initialRepository,
            forceRefresh = true,
        ).getOrThrow()
        assertEquals(
            listOf("https://images.example/old-one.jpg", "https://images.example/old-two.jpg"),
            initial.map { it.path },
        )

        val sourceType = service.resolveSourceType(source)
        val sourceKey = service.resolveSourceKey(source, recursive = false)
        val initialCommitted = assertNotNull(service.resolveCommittedSyncVersion(sourceType, sourceKey))
        val failingRefresh = UnsplashRepo(
            pageLoader = { _, page, _ ->
                if (page == 1) {
                    listOf(unsplashPhoto("new-one"), unsplashPhoto("new-two"))
                } else {
                    error("rate limited on page $page")
                }
            },
            maxPages = 10,
        )

        val refreshed = service.getOrLoad(
            remoteApi = source,
            recursive = false,
            filter = null,
            repository = failingRefresh,
            forceRefresh = true,
        ).getOrThrow()

        assertEquals(initial.map { it.path }, refreshed.map { it.path })
        assertEquals(initialCommitted, service.resolveCommittedSyncVersion(sourceType, sourceKey))
        assertEquals(
            initial.map { it.path },
            db.cachedItemDao().getBySource(sourceType, sourceKey).map { it.path },
        )
    }

    @Test
    fun failedRefreshNeverReplacesAnyCompleteCommittedGenerationWithPartialRows() = runBlocking {
        val cases = listOf(
            "video-only" to ("old.mp4" to "video/mp4"),
            "non-media" to ("old.txt" to "text/plain"),
        )

        for ((caseName, oldFile) in cases) {
            val (oldFileName, oldMimeType) = oldFile
            val source = UnSplashSource(
                name = "failed-refresh-retains-$caseName",
                photoType = "test",
                user = caseName,
            )
            val oldRow = networkFile(source, oldFileName, oldMimeType)

            service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = OneBatchRepository(source, listOf(oldRow)),
                forceRefresh = true,
            ).getOrThrow()

            val sourceType = service.resolveSourceType(source)
            val sourceKey = service.resolveSourceKey(source, recursive = false)
            val committedVersion = assertNotNull(
                service.resolveCommittedSyncVersion(sourceType, sourceKey)
            )

            val refreshed = service.getOrLoad(
                remoteApi = source,
                recursive = false,
                filter = null,
                repository = OneBatchRepository(
                    source = source,
                    batch = listOf(networkFile(source, "new.jpg", "image/jpeg")),
                    terminalFailure = Exception("page 2 failed"),
                ),
                forceRefresh = true,
            ).getOrThrow()

            assertEquals(
                listOf(oldFileName),
                refreshed.map { it.fileName },
                "$caseName committed generation must remain visible",
            )
            assertEquals(
                committedVersion,
                service.resolveCommittedSyncVersion(sourceType, sourceKey),
                "$caseName committed version must not change",
            )
        }
    }

    @Test
    fun pexelsFirstSyncLaterPageFailureCommitsPartialRowsAndSchedulesRetry() = runBlocking {
        val source = PexelsSource(
            name = "pexels-partial-first-sync",
            photoType = PexelsSourceType.FeedPhotos.type,
        )
        val repository = PexelsSourceRepo(
            pageLoader = { _, page, perPage ->
                if (page == 1) {
                    pexelsPage(
                        page = page,
                        perPage = perPage,
                        nextPage = "https://api.pexels.com/v1/curated?page=2",
                        photos = listOf(pexelsPhoto("one"), pexelsPhoto("two")),
                    )
                } else {
                    error("rate limited on page $page")
                }
            },
            maxPages = 10,
        )

        val files = service.getOrLoad(
            remoteApi = source,
            recursive = false,
            filter = null,
            repository = repository,
            forceRefresh = true,
        ).getOrThrow()

        assertEquals(
            listOf("https://images.example/one.jpg", "https://images.example/two.jpg"),
            files.map { it.path },
        )
        val sourceType = service.resolveSourceType(source)
        val sourceKey = service.resolveSourceKey(source, recursive = false)
        val metadata = assertNotNull(db.cacheMetadataDao().getBySource(sourceType, sourceKey))
        val committed = assertNotNull(metadata.committedSyncVersion.takeIf { it > 0 })
        assertEquals(2, service.countMedia(sourceType, sourceKey, false, committed))
        assertEquals(300_000L, metadata.nextUpdateTime - metadata.lastUpdated)
    }

    private class PartialThenFailRepository(
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
            val firstBatch = (0 until 60).map { index ->
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
            onBatch(firstBatch)
            firstBatchWritten.complete(Unit)

            failGate.await()
            return Result.failure(Exception("rate limited"))
        }
    }

    private class OneBatchRepository(
        private val source: UnSplashSource,
        private val batch: List<NetworkFile>,
        private val terminalFailure: Exception? = null,
    ) : BatchSourceRepository<UnSplashSource, NetworkFile> {

        override suspend fun streamItems(
            remoteApi: UnSplashSource,
            recursive: Boolean,
            filter: ((NetworkFile) -> Boolean)?,
            batchSize: Int,
            onBatch: suspend (List<NetworkFile>) -> Unit,
        ): Result<Long> {
            check(remoteApi == source)
            onBatch(batch)
            return terminalFailure?.let { Result.failure(it) }
                ?: Result.success(batch.size.toLong())
        }
    }

    private fun networkFile(
        source: UnSplashSource,
        fileName: String,
        mimeType: String,
    ): NetworkFile {
        return NetworkFile(
            remote = source,
            path = "https://files.example/$fileName",
            fileName = fileName,
            isDirectory = false,
            size = 1,
            mimeType = mimeType,
            modTime = "0",
        )
    }

    private fun unsplashPhoto(id: String): UnsplashPhoto {
        return UnsplashPhoto(
            id = id,
            width = 1600,
            height = 900,
            description = null,
            createdAt = null,
            updatedAt = null,
            promotedAt = null,
            altDescription = null,
            color = null,
            urls = PhotoUrls(
                raw = "https://images.example/$id.raw",
                full = "https://images.example/$id.full",
                regular = "https://images.example/$id.jpg",
                small = "https://images.example/$id-small.jpg",
                thumb = "https://images.example/$id-thumb.jpg",
            ),
        )
    }

    private fun pexelsPage(
        page: Int,
        perPage: Int,
        nextPage: String?,
        photos: List<PexelsPhoto>,
    ): Pagination {
        return Pagination(
            nextPage = nextPage,
            page = page,
            perPage = perPage,
            photos = photos,
        )
    }

    private fun pexelsPhoto(id: String): PexelsPhoto {
        return PexelsPhoto(
            alt = id,
            avgColor = "#000000",
            height = 900,
            id = id.hashCode(),
            liked = false,
            photographer = "tester",
            photographerId = 1L,
            photographerUrl = "https://example.com/tester",
            src = Src(
                landscape = "https://images.example/$id-landscape.jpg",
                large = "https://images.example/$id-large.jpg",
                large2x = "https://images.example/$id-large2x.jpg",
                medium = "https://images.example/$id-medium.jpg",
                original = "https://images.example/$id.jpg",
                portrait = "https://images.example/$id-portrait.jpg",
                small = "https://images.example/$id-small.jpg",
                tiny = "https://images.example/$id-tiny.jpg",
            ),
            url = "https://pexels.example/$id",
            width = 1600,
        )
    }
}
