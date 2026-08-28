package com.alpha.showcase.common.cache.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.alpha.showcase.common.cache.entity.CachedItem

// Every paged query below orders by (sort column, path): name/modified_time are
// not unique (duplicate file names across folders, equal timestamps), so without
// the unique `path` tiebreaker SQLite gives no stable total order and LIMIT/OFFSET
// windows could repeat or skip rows between calls.

@Dao
interface CachedItemDao {

    /**
     * IGNORE — not REPLACE — on the (source, key, path, sync_version) unique
     * index. REPLACE deletes and re-inserts, assigning the row a NEW autoincrement
     * id; first-sync snapshot sessions page `ORDER BY id` on the assumption that
     * ids only ever APPEND, so a path re-returned by a later batch would move to
     * the end and shift already-served OFFSET windows. With IGNORE the first
     * occurrence wins and existing ids never change.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(items: List<CachedItem>): List<Long>

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        ORDER BY is_directory DESC, name COLLATE NOCASE ASC, path ASC
        """
    )
    suspend fun getBySource(sourceType: String, sourceKey: String): List<CachedItem>

    /**
     * Version-pinned variant of [getBySource]. Required now that multiple
     * sync_versions of the same path can coexist during a re-sync — an unversioned
     * read would return duplicate paths.
     */
    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        ORDER BY is_directory DESC, name COLLATE NOCASE ASC, path ASC
        """
    )
    suspend fun getBySourceVersion(sourceType: String, sourceKey: String, syncVersion: Long): List<CachedItem>

    @Query(
        """
        DELETE FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey AND sync_version != :syncVersion
        """
    )
    suspend fun deleteBySourceAndOldSyncVersion(sourceType: String, sourceKey: String, syncVersion: Long)

    /**
     * Deletes all of a source's rows EXCEPT the two given versions. Used by the
     * commit switch to keep the previous committed version alive as a grace copy
     * for read sessions still pinned to it, while purging anything older.
     */
    @Query(
        """
        DELETE FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version NOT IN (:keepVersion, :graceVersion)
        """
    )
    suspend fun deleteBySourceExceptVersions(
        sourceType: String,
        sourceKey: String,
        keepVersion: Long,
        graceVersion: Long,
    )

    @Query(
        """
        DELETE FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey AND sync_version = :syncVersion
        """
    )
    suspend fun deleteBySourceAndSyncVersion(sourceType: String, sourceKey: String, syncVersion: Long)

    @Query(
        """
        UPDATE cached_items
        SET last_accessed = :accessedAt
        WHERE source_type = :sourceType AND source_key = :sourceKey
        """
    )
    suspend fun updateLastAccessed(sourceType: String, sourceKey: String, accessedAt: Long)

    @Query(
        """
        SELECT COUNT(*) FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        """
    )
    suspend fun countBySource(sourceType: String, sourceKey: String): Int

    /**
     * Highest generation ever persisted for one source, including committed,
     * grace, abandoned, and partially-written rows. A restarted process must
     * allocate above this floor so (source, path, sync_version) can never collide
     * with an older process's row.
     */
    @Query(
        """
        SELECT MAX(sync_version) FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        """
    )
    suspend fun maxSyncVersionBySource(sourceType: String, sourceKey: String): Long?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM cached_items
            WHERE source_type = :sourceType AND source_key = :sourceKey
            AND sync_version = :syncVersion
            LIMIT 1
        )
        """
    )
    suspend fun hasAnyBySourceVersion(sourceType: String, sourceKey: String, syncVersion: Long): Boolean

    @Query(
        """
        SELECT COUNT(*) FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        AND is_directory = 0
        AND media_kind = 1
        """
    )
    suspend fun countImagesBySourceVersion(sourceType: String, sourceKey: String, syncVersion: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        AND is_directory = 0
        AND media_kind IN (1, 2)
        """
    )
    suspend fun countMediaBySourceVersion(sourceType: String, sourceKey: String, syncVersion: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND is_directory = 0
        AND media_kind = 1
        """
    )
    suspend fun countImagesBySource(sourceType: String, sourceKey: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND is_directory = 0
        AND media_kind IN (1, 2)
        """
    )
    suspend fun countMediaBySource(sourceType: String, sourceKey: String): Int

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND is_directory = 0
        AND media_kind = 1
        ORDER BY name COLLATE NOCASE ASC, path ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getImagesPagedByNameAsc(sourceType: String, sourceKey: String, limit: Int, offset: Int): List<CachedItem>

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND is_directory = 0
        AND media_kind = 1
        ORDER BY name COLLATE NOCASE DESC, path DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getImagesPagedByNameDesc(sourceType: String, sourceKey: String, limit: Int, offset: Int): List<CachedItem>

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND is_directory = 0
        AND media_kind = 1
        ORDER BY modified_time ASC, path ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getImagesPagedByDateAsc(sourceType: String, sourceKey: String, limit: Int, offset: Int): List<CachedItem>

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND is_directory = 0
        AND media_kind = 1
        ORDER BY modified_time DESC, path DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getImagesPagedByDateDesc(sourceType: String, sourceKey: String, limit: Int, offset: Int): List<CachedItem>

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND is_directory = 0
        AND media_kind IN (1, 2)
        ORDER BY name COLLATE NOCASE ASC, path ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getMediaPagedByNameAsc(sourceType: String, sourceKey: String, limit: Int, offset: Int): List<CachedItem>

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND is_directory = 0
        AND media_kind IN (1, 2)
        ORDER BY name COLLATE NOCASE DESC, path DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getMediaPagedByNameDesc(sourceType: String, sourceKey: String, limit: Int, offset: Int): List<CachedItem>

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND is_directory = 0
        AND media_kind IN (1, 2)
        ORDER BY modified_time ASC, path ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getMediaPagedByDateAsc(sourceType: String, sourceKey: String, limit: Int, offset: Int): List<CachedItem>

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND is_directory = 0
        AND media_kind IN (1, 2)
        ORDER BY modified_time DESC, path DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getMediaPagedByDateDesc(sourceType: String, sourceKey: String, limit: Int, offset: Int): List<CachedItem>

    // --- Version-pinned paged queries (read a single committed sync_version) ---

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        AND is_directory = 0 AND media_kind = 1
        ORDER BY name COLLATE NOCASE ASC, path ASC LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getImagesPagedByNameAscVersion(sourceType: String, sourceKey: String, syncVersion: Long, limit: Int, offset: Int): List<CachedItem>

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        AND is_directory = 0 AND media_kind = 1
        ORDER BY name COLLATE NOCASE DESC, path DESC LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getImagesPagedByNameDescVersion(sourceType: String, sourceKey: String, syncVersion: Long, limit: Int, offset: Int): List<CachedItem>

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        AND is_directory = 0 AND media_kind = 1
        ORDER BY modified_time ASC, path ASC LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getImagesPagedByDateAscVersion(sourceType: String, sourceKey: String, syncVersion: Long, limit: Int, offset: Int): List<CachedItem>

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        AND is_directory = 0 AND media_kind = 1
        ORDER BY modified_time DESC, path DESC LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getImagesPagedByDateDescVersion(sourceType: String, sourceKey: String, syncVersion: Long, limit: Int, offset: Int): List<CachedItem>

    /**
     * Insertion-order (rowid) paged read of ONE version. Used while that version is
     * still being written by a first sync: rows only ever APPEND in id order, so
     * already-served OFFSET windows never shift — unlike name/date order, where a
     * later batch can insert rows before an already-read position.
     */
    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        AND is_directory = 0 AND media_kind = 1
        ORDER BY id ASC LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getImagesPagedByIdAscVersion(sourceType: String, sourceKey: String, syncVersion: Long, limit: Int, offset: Int): List<CachedItem>

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        AND is_directory = 0 AND media_kind IN (1, 2)
        ORDER BY id ASC LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getMediaPagedByIdAscVersion(sourceType: String, sourceKey: String, syncVersion: Long, limit: Int, offset: Int): List<CachedItem>

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        AND is_directory = 0 AND media_kind IN (1, 2)
        ORDER BY name COLLATE NOCASE ASC, path ASC LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getMediaPagedByNameAscVersion(sourceType: String, sourceKey: String, syncVersion: Long, limit: Int, offset: Int): List<CachedItem>

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        AND is_directory = 0 AND media_kind IN (1, 2)
        ORDER BY name COLLATE NOCASE DESC, path DESC LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getMediaPagedByNameDescVersion(sourceType: String, sourceKey: String, syncVersion: Long, limit: Int, offset: Int): List<CachedItem>

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        AND is_directory = 0 AND media_kind IN (1, 2)
        ORDER BY modified_time ASC, path ASC LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getMediaPagedByDateAscVersion(sourceType: String, sourceKey: String, syncVersion: Long, limit: Int, offset: Int): List<CachedItem>

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        AND is_directory = 0 AND media_kind IN (1, 2)
        ORDER BY modified_time DESC, path DESC LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getMediaPagedByDateDescVersion(sourceType: String, sourceKey: String, syncVersion: Long, limit: Int, offset: Int): List<CachedItem>

    // --- Stable-key ordinal lookup (re-anchor after a refresh) ---
    // A pager that must keep showing the SAME media across a refresh resolves the
    // item's NEW index by counting the rows that sort before it under the
    // session's ordering. The media filter is parameterized as
    // `media_kind BETWEEN 1 AND :maxMediaKind` (1 = images only, 2 = images+videos)
    // so each ordering needs only one query.

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion AND path = :path
        LIMIT 1
        """
    )
    suspend fun getByPathVersion(sourceType: String, sourceKey: String, syncVersion: Long, path: String): CachedItem?

    @Query(
        """
        SELECT COUNT(*) FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        AND is_directory = 0 AND media_kind BETWEEN 1 AND :maxMediaKind
        AND id < :id
        """
    )
    suspend fun countMediaBeforeIdVersion(sourceType: String, sourceKey: String, syncVersion: Long, maxMediaKind: Int, id: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        AND is_directory = 0 AND media_kind BETWEEN 1 AND :maxMediaKind
        AND (name COLLATE NOCASE < :name OR (name COLLATE NOCASE = :name AND path < :path))
        """
    )
    suspend fun countMediaBeforeNameAscVersion(sourceType: String, sourceKey: String, syncVersion: Long, maxMediaKind: Int, name: String, path: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        AND is_directory = 0 AND media_kind BETWEEN 1 AND :maxMediaKind
        AND (name COLLATE NOCASE > :name OR (name COLLATE NOCASE = :name AND path > :path))
        """
    )
    suspend fun countMediaBeforeNameDescVersion(sourceType: String, sourceKey: String, syncVersion: Long, maxMediaKind: Int, name: String, path: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        AND is_directory = 0 AND media_kind BETWEEN 1 AND :maxMediaKind
        AND (modified_time < :modifiedTime OR (modified_time = :modifiedTime AND path < :path))
        """
    )
    suspend fun countMediaBeforeDateAscVersion(sourceType: String, sourceKey: String, syncVersion: Long, maxMediaKind: Int, modifiedTime: Long, path: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND sync_version = :syncVersion
        AND is_directory = 0 AND media_kind BETWEEN 1 AND :maxMediaKind
        AND (modified_time > :modifiedTime OR (modified_time = :modifiedTime AND path > :path))
        """
    )
    suspend fun countMediaBeforeDateDescVersion(sourceType: String, sourceKey: String, syncVersion: Long, maxMediaKind: Int, modifiedTime: Long, path: String): Int
}
