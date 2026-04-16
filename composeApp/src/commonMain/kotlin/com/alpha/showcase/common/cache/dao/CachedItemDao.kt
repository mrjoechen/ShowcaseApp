package com.alpha.showcase.common.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.alpha.showcase.common.cache.entity.CachedItem

@Dao
interface CachedItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(items: List<CachedItem>)

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        ORDER BY is_directory DESC, name COLLATE NOCASE ASC
        """
    )
    suspend fun getBySource(sourceType: String, sourceKey: String): List<CachedItem>

    @Query(
        """
        DELETE FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey AND sync_version != :syncVersion
        """
    )
    suspend fun deleteBySourceAndOldSyncVersion(sourceType: String, sourceKey: String, syncVersion: Long)

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
        ORDER BY name COLLATE NOCASE ASC
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
        ORDER BY name COLLATE NOCASE DESC
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
        ORDER BY modified_time ASC
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
        ORDER BY modified_time DESC
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
        ORDER BY name COLLATE NOCASE ASC
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
        ORDER BY name COLLATE NOCASE DESC
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
        ORDER BY modified_time ASC
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
        ORDER BY modified_time DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getMediaPagedByDateDesc(sourceType: String, sourceKey: String, limit: Int, offset: Int): List<CachedItem>
}
