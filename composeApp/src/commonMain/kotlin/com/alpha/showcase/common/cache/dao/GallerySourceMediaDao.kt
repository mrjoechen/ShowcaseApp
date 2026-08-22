package com.alpha.showcase.common.cache.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.alpha.showcase.common.cache.entity.GallerySourceMedia

@Dao
interface GallerySourceMediaDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(items: List<GallerySourceMedia>): List<Long>

    @Query(
        """
        SELECT * FROM gallery_source_media
        WHERE source_name = :sourceName
        ORDER BY added_at ASC, id ASC
        """
    )
    suspend fun getBySource(sourceName: String): List<GallerySourceMedia>

    @Query(
        """
        DELETE FROM gallery_source_media
        WHERE source_name = :sourceName AND media_uri IN (:mediaUris)
        """
    )
    suspend fun deleteBySourceAndUris(sourceName: String, mediaUris: List<String>)

    @Query(
        """
        DELETE FROM gallery_source_media
        WHERE source_name = :sourceName
        """
    )
    suspend fun deleteBySource(sourceName: String)
}

