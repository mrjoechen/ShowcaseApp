package com.alpha.showcase.common.cache

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.alpha.showcase.common.cache.entity.CACHED_ITEM_MEDIA_KIND_IMAGE
import com.alpha.showcase.common.cache.entity.CachedItem
import com.alpha.showcase.common.cache.dao.CachedItemDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * DB-level coverage for the multi-version coexistence introduced when the
 * cached_items unique index started including sync_version. Verifies that a
 * background re-sync's new-version rows do NOT clobber the old committed version,
 * and that success/failure cleanup behave as an atomic switch.
 */
class CachedItemVersionCoexistenceTest {

    private lateinit var db: SourceCacheDatabase
    private lateinit var dao: CachedItemDao

    private val sourceType = "webdav"
    private val sourceKey = "key1"

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<SourceCacheDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        dao = db.cachedItemDao()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun image(path: String, version: Long, name: String = path): CachedItem = CachedItem(
        sourceType = sourceType,
        sourceKey = sourceKey,
        parentPath = "/",
        name = name,
        path = path,
        isDirectory = false,
        size = 1,
        mimeType = "image/jpeg",
        mediaKind = CACHED_ITEM_MEDIA_KIND_IMAGE,
        modifiedTime = 0,
        syncVersion = version,
    )

    @Test
    fun newVersionRowsCoexistWithOldAndDoNotClobberIt() = runTest {
        // Commit version=1 with 3 images.
        dao.insertOrIgnore(listOf(image("a.jpg", 1), image("b.jpg", 1), image("c.jpg", 1)))
        assertEquals(3, dao.countImagesBySourceVersion(sourceType, sourceKey, 1))

        // A re-sync writes a PARTIAL version=2 that overlaps paths a.jpg and b.jpg.
        // With sync_version in the unique index these must NOT replace the v1 rows.
        dao.insertOrIgnore(listOf(image("a.jpg", 2), image("b.jpg", 2)))

        // v1 is untouched (still 3); v2 has its 2 partial rows. No clobbering.
        assertEquals(3, dao.countImagesBySourceVersion(sourceType, sourceKey, 1))
        assertEquals(2, dao.countImagesBySourceVersion(sourceType, sourceKey, 2))

        // A version-pinned page of v1 still returns all 3 in stable order.
        val v1Page = dao.getImagesPagedByNameAscVersion(sourceType, sourceKey, 1, limit = 10, offset = 0)
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), v1Page.map { it.path })
    }

    @Test
    fun duplicatePathInLaterBatchKeepsExistingRowAndItsId() = runTest {
        // IGNORE semantics guard the first-sync snapshot's ORDER BY id paging: a
        // path re-returned by a later batch must NOT be re-inserted with a new id
        // (REPLACE did exactly that), or already-served id-ordered OFFSET windows
        // would shift.
        dao.insertOrIgnore(listOf(image("a.jpg", 1), image("b.jpg", 1)))
        val before = dao.getImagesPagedByIdAscVersion(sourceType, sourceKey, 1, limit = 10, offset = 0)

        dao.insertOrIgnore(listOf(image("b.jpg", 1), image("c.jpg", 1)))
        val after = dao.getImagesPagedByIdAscVersion(sourceType, sourceKey, 1, limit = 10, offset = 0)

        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), after.map { it.path })
        assertEquals(before.map { it.id }, after.take(2).map { it.id })
    }

    @Test
    fun failedSyncRollbackLeavesOldVersionIntact() = runTest {
        dao.insertOrIgnore(listOf(image("a.jpg", 1), image("b.jpg", 1), image("c.jpg", 1)))
        // Partial v2 written, then the sync fails -> delete only v2's rows.
        dao.insertOrIgnore(listOf(image("a.jpg", 2)))
        dao.deleteBySourceAndSyncVersion(sourceType, sourceKey, 2)

        // Old cache fully survives; the in-flight version is gone.
        assertEquals(3, dao.countImagesBySourceVersion(sourceType, sourceKey, 1))
        assertEquals(0, dao.countImagesBySourceVersion(sourceType, sourceKey, 2))
    }

    @Test
    fun successfulSyncSwitchToNewVersionAndDropsOld() = runTest {
        dao.insertOrIgnore(listOf(image("a.jpg", 1), image("b.jpg", 1), image("c.jpg", 1)))
        // v2 fully written (a.jpg replaced content, c.jpg removed, d.jpg added).
        dao.insertOrIgnore(listOf(image("a.jpg", 2), image("b.jpg", 2), image("d.jpg", 2)))
        // Atomic switch: commit v2, then delete everything that isn't v2.
        dao.deleteBySourceAndOldSyncVersion(sourceType, sourceKey, 2)

        assertEquals(0, dao.countImagesBySourceVersion(sourceType, sourceKey, 1))
        assertEquals(3, dao.countImagesBySourceVersion(sourceType, sourceKey, 2))
        val page = dao.getImagesPagedByNameAscVersion(sourceType, sourceKey, 2, limit = 10, offset = 0)
        assertEquals(listOf("a.jpg", "b.jpg", "d.jpg"), page.map { it.path })
    }
}
