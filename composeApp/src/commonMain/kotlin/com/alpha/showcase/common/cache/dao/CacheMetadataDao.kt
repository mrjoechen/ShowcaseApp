package com.alpha.showcase.common.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.alpha.showcase.common.cache.entity.CacheMetadata

@Dao
interface CacheMetadataDao {

    @Query(
        """
        SELECT * FROM cache_metadata
        WHERE source_type = :sourceType AND source_key = :sourceKey
        LIMIT 1
        """
    )
    suspend fun getBySource(sourceType: String, sourceKey: String): CacheMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(metadata: CacheMetadata)
}
