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
}
