package com.alpha.showcase.common.cache

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.alpha.showcase.common.cache.entity.CACHED_ITEM_MEDIA_KIND_IMAGE
import com.alpha.showcase.common.cache.entity.CACHED_ITEM_MEDIA_KIND_OTHER
import com.alpha.showcase.common.cache.entity.CACHED_ITEM_MEDIA_KIND_VIDEO
import com.alpha.showcase.common.cache.entity.CachedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression coverage for the stable-key ordinal locator: for EVERY row and
 * EVERY supported ordering, [NetworkFileCacheService.locateMediaIndex] must
 * agree with the position the paged queries would serve that row at — including
 * duplicate names (case-insensitive collation) and duplicate timestamps, where
 * only the path tiebreaker defines the order.
 */
class NetworkFileCacheServiceLocateIndexTest {

    private lateinit var db: SourceCacheDatabase
    private lateinit var service: NetworkFileCacheService

    private val sourceType = "unsplash"
    private val sourceKey = "locate-key"
    private val version = 100L

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<SourceCacheDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        service = NetworkFileCacheService(db)
    }

    @AfterTest
    fun tearDown() = runBlocking {
        service.shutdownBackgroundRefreshes()
        db.close()
    }

    private fun row(
        name: String,
        path: String,
        modifiedTime: Long,
        mediaKind: Int = CACHED_ITEM_MEDIA_KIND_IMAGE,
        isDirectory: Boolean = false,
    ) = CachedItem(
        sourceType = sourceType,
        sourceKey = sourceKey,
        parentPath = "/",
        name = name,
        path = path,
        isDirectory = isDirectory,
        size = 1,
        mimeType = if (mediaKind == CACHED_ITEM_MEDIA_KIND_VIDEO) "video/mp4" else "image/jpeg",
        mediaKind = mediaKind,
        modifiedTime = modifiedTime,
        syncVersion = version,
    )

    private suspend fun seed() {
        db.cachedItemDao().insertOrIgnore(
            listOf(
                // Duplicate names (case difference exercises COLLATE NOCASE) and
                // duplicate timestamps: only `path` gives them a stable order.
                row(name = "b.jpg", path = "p/1-b.jpg", modifiedTime = 200),
                row(name = "a.jpg", path = "p/2-a.jpg", modifiedTime = 300),
                row(name = "B.jpg", path = "p/3-B.jpg", modifiedTime = 200),
                row(name = "a.jpg", path = "p/4-a.jpg", modifiedTime = 100),
                row(name = "v.mp4", path = "p/5-v.mp4", modifiedTime = 200, mediaKind = CACHED_ITEM_MEDIA_KIND_VIDEO),
                row(name = "dir", path = "p/dir", modifiedTime = 0, mediaKind = CACHED_ITEM_MEDIA_KIND_OTHER, isDirectory = true),
                row(name = "notes.txt", path = "p/notes.txt", modifiedTime = 0, mediaKind = CACHED_ITEM_MEDIA_KIND_OTHER),
            )
        )
    }

    private suspend fun pagedRows(sortRule: Int, supportVideo: Boolean): List<CachedItem> {
        val dao = db.cachedItemDao()
        return if (supportVideo) {
            when (sortRule) {
                2 -> dao.getMediaPagedByNameDescVersion(sourceType, sourceKey, version, 100, 0)
                3 -> dao.getMediaPagedByDateAscVersion(sourceType, sourceKey, version, 100, 0)
                4 -> dao.getMediaPagedByDateDescVersion(sourceType, sourceKey, version, 100, 0)
                else -> dao.getMediaPagedByNameAscVersion(sourceType, sourceKey, version, 100, 0)
            }
        } else {
            when (sortRule) {
                2 -> dao.getImagesPagedByNameDescVersion(sourceType, sourceKey, version, 100, 0)
                3 -> dao.getImagesPagedByDateAscVersion(sourceType, sourceKey, version, 100, 0)
                4 -> dao.getImagesPagedByDateDescVersion(sourceType, sourceKey, version, 100, 0)
                else -> dao.getImagesPagedByNameAscVersion(sourceType, sourceKey, version, 100, 0)
            }
        }
    }

    @Test
    fun ordinalAgreesWithPagedOrderingForEverySortAndMediaFilter() = runTest {
        seed()
        for (supportVideo in listOf(false, true)) {
            for (sortRule in listOf(1, 2, 3, 4)) {
                val ordered = pagedRows(sortRule, supportVideo)
                ordered.forEachIndexed { expected, item ->
                    assertEquals(
                        expected,
                        service.locateMediaIndex(
                            sourceType, sourceKey, supportVideo, sortRule,
                            syncVersion = version, insertionOrder = false, path = item.path,
                        ),
                        "sortRule=$sortRule supportVideo=$supportVideo path=${item.path}",
                    )
                }
            }
        }
    }

    @Test
    fun ordinalAgreesWithInsertionOrderPaging() = runTest {
        seed()
        val byId = db.cachedItemDao().getMediaPagedByIdAscVersion(sourceType, sourceKey, version, 100, 0)
        byId.forEachIndexed { expected, item ->
            assertEquals(
                expected,
                service.locateMediaIndex(
                    sourceType, sourceKey, supportVideo = true, sortRule = -1,
                    syncVersion = version, insertionOrder = true, path = item.path,
                ),
                "insertion order path=${item.path}",
            )
        }
    }

    @Test
    fun locateReturnsNullForMissingFilteredOrNonMediaPaths() = runTest {
        seed()
        // Unknown path.
        assertNull(service.locateMediaIndex(sourceType, sourceKey, false, 1, version, false, "p/none.jpg"))
        // Wrong version.
        assertNull(service.locateMediaIndex(sourceType, sourceKey, false, 1, version + 1, false, "p/1-b.jpg"))
        // Directory and non-media rows are never locatable.
        assertNull(service.locateMediaIndex(sourceType, sourceKey, true, 1, version, false, "p/dir"))
        assertNull(service.locateMediaIndex(sourceType, sourceKey, true, 1, version, false, "p/notes.txt"))
        // A video is filtered out of an images-only session.
        assertNull(service.locateMediaIndex(sourceType, sourceKey, false, 1, version, false, "p/5-v.mp4"))
    }
}
