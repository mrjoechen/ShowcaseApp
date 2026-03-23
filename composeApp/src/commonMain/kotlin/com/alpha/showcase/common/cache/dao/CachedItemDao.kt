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

    // --- Paging queries for media files (images) ---

    @Query(
        """
        SELECT COUNT(*) FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND is_directory = 0
        AND (
            mime_type LIKE 'image/%'
            OR LOWER(name) LIKE '%.jpg' OR LOWER(name) LIKE '%.jpeg'
            OR LOWER(name) LIKE '%.png' OR LOWER(name) LIKE '%.webp'
            OR LOWER(name) LIKE '%.gif' OR LOWER(name) LIKE '%.bmp'
            OR LOWER(name) LIKE '%.heic' OR LOWER(name) LIKE '%.heif'
            OR LOWER(name) LIKE '%.svg' OR LOWER(name) LIKE '%.dng'
            OR LOWER(name) LIKE '%.ico'
        )
        """
    )
    suspend fun countImagesBySource(sourceType: String, sourceKey: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND is_directory = 0
        AND (
            mime_type LIKE 'image/%' OR mime_type LIKE 'video/%'
            OR LOWER(name) LIKE '%.jpg' OR LOWER(name) LIKE '%.jpeg'
            OR LOWER(name) LIKE '%.png' OR LOWER(name) LIKE '%.webp'
            OR LOWER(name) LIKE '%.gif' OR LOWER(name) LIKE '%.bmp'
            OR LOWER(name) LIKE '%.heic' OR LOWER(name) LIKE '%.heif'
            OR LOWER(name) LIKE '%.svg' OR LOWER(name) LIKE '%.dng'
            OR LOWER(name) LIKE '%.ico'
            OR LOWER(name) LIKE '%.mp4' OR LOWER(name) LIKE '%.mkv'
            OR LOWER(name) LIKE '%.webm' OR LOWER(name) LIKE '%.mov'
            OR LOWER(name) LIKE '%.avi' OR LOWER(name) LIKE '%.wmv'
            OR LOWER(name) LIKE '%.flv' OR LOWER(name) LIKE '%.m4v'
            OR LOWER(name) LIKE '%.3gp'
        )
        """
    )
    suspend fun countMediaBySource(sourceType: String, sourceKey: String): Int

    // --- Paged image queries by sort order ---

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND is_directory = 0
        AND (
            mime_type LIKE 'image/%'
            OR LOWER(name) LIKE '%.jpg' OR LOWER(name) LIKE '%.jpeg'
            OR LOWER(name) LIKE '%.png' OR LOWER(name) LIKE '%.webp'
            OR LOWER(name) LIKE '%.gif' OR LOWER(name) LIKE '%.bmp'
            OR LOWER(name) LIKE '%.heic' OR LOWER(name) LIKE '%.heif'
            OR LOWER(name) LIKE '%.svg' OR LOWER(name) LIKE '%.dng'
            OR LOWER(name) LIKE '%.ico'
        )
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
        AND (
            mime_type LIKE 'image/%'
            OR LOWER(name) LIKE '%.jpg' OR LOWER(name) LIKE '%.jpeg'
            OR LOWER(name) LIKE '%.png' OR LOWER(name) LIKE '%.webp'
            OR LOWER(name) LIKE '%.gif' OR LOWER(name) LIKE '%.bmp'
            OR LOWER(name) LIKE '%.heic' OR LOWER(name) LIKE '%.heif'
            OR LOWER(name) LIKE '%.svg' OR LOWER(name) LIKE '%.dng'
            OR LOWER(name) LIKE '%.ico'
        )
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
        AND (
            mime_type LIKE 'image/%'
            OR LOWER(name) LIKE '%.jpg' OR LOWER(name) LIKE '%.jpeg'
            OR LOWER(name) LIKE '%.png' OR LOWER(name) LIKE '%.webp'
            OR LOWER(name) LIKE '%.gif' OR LOWER(name) LIKE '%.bmp'
            OR LOWER(name) LIKE '%.heic' OR LOWER(name) LIKE '%.heif'
            OR LOWER(name) LIKE '%.svg' OR LOWER(name) LIKE '%.dng'
            OR LOWER(name) LIKE '%.ico'
        )
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
        AND (
            mime_type LIKE 'image/%'
            OR LOWER(name) LIKE '%.jpg' OR LOWER(name) LIKE '%.jpeg'
            OR LOWER(name) LIKE '%.png' OR LOWER(name) LIKE '%.webp'
            OR LOWER(name) LIKE '%.gif' OR LOWER(name) LIKE '%.bmp'
            OR LOWER(name) LIKE '%.heic' OR LOWER(name) LIKE '%.heif'
            OR LOWER(name) LIKE '%.svg' OR LOWER(name) LIKE '%.dng'
            OR LOWER(name) LIKE '%.ico'
        )
        ORDER BY modified_time DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getImagesPagedByDateDesc(sourceType: String, sourceKey: String, limit: Int, offset: Int): List<CachedItem>

    // --- Paged media (images + videos) queries by sort order ---

    @Query(
        """
        SELECT * FROM cached_items
        WHERE source_type = :sourceType AND source_key = :sourceKey
        AND is_directory = 0
        AND (
            mime_type LIKE 'image/%' OR mime_type LIKE 'video/%'
            OR LOWER(name) LIKE '%.jpg' OR LOWER(name) LIKE '%.jpeg'
            OR LOWER(name) LIKE '%.png' OR LOWER(name) LIKE '%.webp'
            OR LOWER(name) LIKE '%.gif' OR LOWER(name) LIKE '%.bmp'
            OR LOWER(name) LIKE '%.heic' OR LOWER(name) LIKE '%.heif'
            OR LOWER(name) LIKE '%.svg' OR LOWER(name) LIKE '%.dng'
            OR LOWER(name) LIKE '%.ico'
            OR LOWER(name) LIKE '%.mp4' OR LOWER(name) LIKE '%.mkv'
            OR LOWER(name) LIKE '%.webm' OR LOWER(name) LIKE '%.mov'
            OR LOWER(name) LIKE '%.avi' OR LOWER(name) LIKE '%.wmv'
            OR LOWER(name) LIKE '%.flv' OR LOWER(name) LIKE '%.m4v'
            OR LOWER(name) LIKE '%.3gp'
        )
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
        AND (
            mime_type LIKE 'image/%' OR mime_type LIKE 'video/%'
            OR LOWER(name) LIKE '%.jpg' OR LOWER(name) LIKE '%.jpeg'
            OR LOWER(name) LIKE '%.png' OR LOWER(name) LIKE '%.webp'
            OR LOWER(name) LIKE '%.gif' OR LOWER(name) LIKE '%.bmp'
            OR LOWER(name) LIKE '%.heic' OR LOWER(name) LIKE '%.heif'
            OR LOWER(name) LIKE '%.svg' OR LOWER(name) LIKE '%.dng'
            OR LOWER(name) LIKE '%.ico'
            OR LOWER(name) LIKE '%.mp4' OR LOWER(name) LIKE '%.mkv'
            OR LOWER(name) LIKE '%.webm' OR LOWER(name) LIKE '%.mov'
            OR LOWER(name) LIKE '%.avi' OR LOWER(name) LIKE '%.wmv'
            OR LOWER(name) LIKE '%.flv' OR LOWER(name) LIKE '%.m4v'
            OR LOWER(name) LIKE '%.3gp'
        )
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
        AND (
            mime_type LIKE 'image/%' OR mime_type LIKE 'video/%'
            OR LOWER(name) LIKE '%.jpg' OR LOWER(name) LIKE '%.jpeg'
            OR LOWER(name) LIKE '%.png' OR LOWER(name) LIKE '%.webp'
            OR LOWER(name) LIKE '%.gif' OR LOWER(name) LIKE '%.bmp'
            OR LOWER(name) LIKE '%.heic' OR LOWER(name) LIKE '%.heif'
            OR LOWER(name) LIKE '%.svg' OR LOWER(name) LIKE '%.dng'
            OR LOWER(name) LIKE '%.ico'
            OR LOWER(name) LIKE '%.mp4' OR LOWER(name) LIKE '%.mkv'
            OR LOWER(name) LIKE '%.webm' OR LOWER(name) LIKE '%.mov'
            OR LOWER(name) LIKE '%.avi' OR LOWER(name) LIKE '%.wmv'
            OR LOWER(name) LIKE '%.flv' OR LOWER(name) LIKE '%.m4v'
            OR LOWER(name) LIKE '%.3gp'
        )
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
        AND (
            mime_type LIKE 'image/%' OR mime_type LIKE 'video/%'
            OR LOWER(name) LIKE '%.jpg' OR LOWER(name) LIKE '%.jpeg'
            OR LOWER(name) LIKE '%.png' OR LOWER(name) LIKE '%.webp'
            OR LOWER(name) LIKE '%.gif' OR LOWER(name) LIKE '%.bmp'
            OR LOWER(name) LIKE '%.heic' OR LOWER(name) LIKE '%.heif'
            OR LOWER(name) LIKE '%.svg' OR LOWER(name) LIKE '%.dng'
            OR LOWER(name) LIKE '%.ico'
            OR LOWER(name) LIKE '%.mp4' OR LOWER(name) LIKE '%.mkv'
            OR LOWER(name) LIKE '%.webm' OR LOWER(name) LIKE '%.mov'
            OR LOWER(name) LIKE '%.avi' OR LOWER(name) LIKE '%.wmv'
            OR LOWER(name) LIKE '%.flv' OR LOWER(name) LIKE '%.m4v'
            OR LOWER(name) LIKE '%.3gp'
        )
        ORDER BY modified_time DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getMediaPagedByDateDesc(sourceType: String, sourceKey: String, limit: Int, offset: Int): List<CachedItem>
}
