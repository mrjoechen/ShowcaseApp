package com.alpha.showcase.common.repo

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.alpha.showcase.api.s3.S3ListPage
import com.alpha.showcase.api.s3.S3ObjectItem
import com.alpha.showcase.common.cache.NetworkFileCacheService
import com.alpha.showcase.common.cache.SourceCacheDatabase
import com.alpha.showcase.common.networkfile.storage.remote.RssSource
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class S3RssCacheIntegrationTest {

    private lateinit var database: SourceCacheDatabase
    private lateinit var cacheService: NetworkFileCacheService

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder<SourceCacheDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        cacheService = NetworkFileCacheService(database)
    }

    @AfterTest
    fun tearDown() = runBlocking {
        cacheService.shutdownBackgroundRefreshes()
        database.close()
    }

    @Test
    fun s3UsesCacheBackedPagingInsteadOfLegacyFullListLoading() = runBlocking {
        val source = S3Source(
            name = "Archive",
            endpoint = "s3.amazonaws.com",
            accessKey = "access",
            secretKey = "encrypted-secret",
            bucket = "photos",
        )
        val s3Repo = S3SourceRepo { _, _, _ ->
            S3ListPage(
                objects = listOf(
                    S3ObjectItem(
                        key = "album/cat.jpg",
                        size = 10,
                        lastModified = "2026-08-20T01:02:03Z",
                        etag = "cat-v1",
                    ),
                ),
                commonPrefixes = emptyList(),
                isTruncated = false,
                nextContinuationToken = null,
            )
        }
        val manager = RepoManager(
            s3SourceRepo = s3Repo,
            cacheService = cacheService,
        )

        val info = assertNotNull(manager.ensureCacheReady(source, recursive = true).getOrThrow())
        info.syncCompletion.await()

        assertEquals(1, manager.countMedia(info, supportVideo = false))
        val file = manager.loadMediaPage(info, supportVideo = false, sortRule = 1, offset = 0, limit = 10).single()
        assertEquals("album/cat.jpg", file.path)
        assertIs<S3Source>(file.remote)
        assertEquals("album/cat.jpg", file.extra?.get(S3_OBJECT_KEY))
        assertEquals(
            "cat-v1",
            file.extra?.get("etag"),
            "the DB round-trip must retain the object version used by Coil's cache key",
        )
    }

    @Test
    fun rssUsesConfiguredRefreshIntervalForItsCacheTtl() = runBlocking {
        val refreshInterval = 123_456L
        val source = RssSource(
            name = "News",
            url = "https://example.com/feed.xml",
            refreshInterval = refreshInterval,
        )
        val manager = RepoManager(
            rssSourceRepo = RssSourceRepo {
                listOf("https://cdn.example.com/hero.jpg")
            },
            cacheService = cacheService,
        )

        val info = assertNotNull(manager.ensureCacheReady(source, recursive = false).getOrThrow())
        info.syncCompletion.await()
        val metadata = assertNotNull(
            database.cacheMetadataDao().getBySource(info.sourceType, info.sourceKey),
        )

        assertEquals(refreshInterval, metadata.nextUpdateTime - metadata.lastUpdated)
        assertEquals(1, manager.countMedia(info, supportVideo = false))
    }
}
