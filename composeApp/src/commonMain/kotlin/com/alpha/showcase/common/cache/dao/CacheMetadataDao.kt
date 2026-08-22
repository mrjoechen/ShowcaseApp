package com.alpha.showcase.common.cache.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
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
