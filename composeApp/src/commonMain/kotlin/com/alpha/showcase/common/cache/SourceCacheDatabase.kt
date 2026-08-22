package com.alpha.showcase.common.cache

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.AutoMigration
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL
import com.alpha.showcase.common.cache.dao.CacheMetadataDao
import com.alpha.showcase.common.cache.dao.CachedItemDao
import com.alpha.showcase.common.cache.dao.GallerySourceMediaDao
import com.alpha.showcase.common.cache.entity.CacheMetadata
import com.alpha.showcase.common.cache.entity.CachedItem
import com.alpha.showcase.common.cache.entity.CACHED_ITEM_MEDIA_KIND_IMAGE
import com.alpha.showcase.common.cache.entity.CACHED_ITEM_MEDIA_KIND_VIDEO
import com.alpha.showcase.common.cache.entity.GallerySourceMedia
import com.alpha.showcase.common.utils.IMAGE_EXT_SUPPORT
import com.alpha.showcase.common.utils.VIDEO_EXT_SUPPORT
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        CachedItem::class,
        CacheMetadata::class,
        GallerySourceMedia::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ]
)
@ConstructedBy(SourceCacheDatabaseConstructor::class)
abstract class SourceCacheDatabase : RoomDatabase() {

    abstract fun cachedItemDao(): CachedItemDao

    abstract fun cacheMetadataDao(): CacheMetadataDao

    abstract fun gallerySourceMediaDao(): GallerySourceMediaDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object SourceCacheDatabaseConstructor : RoomDatabaseConstructor<SourceCacheDatabase> {
    override fun initialize(): SourceCacheDatabase
}

expect fun getSourceCacheDatabaseBuilder(): RoomDatabase.Builder<SourceCacheDatabase>

private val sourceCacheMigration2To3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.executeSQL(
            """
            ALTER TABLE cached_items
            ADD COLUMN media_kind INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
        )
        connection.executeSQL(
            """
            CREATE INDEX IF NOT EXISTS index_cached_items_source_type_source_key_media_kind
            ON cached_items(source_type, source_key, media_kind)
            """.trimIndent()
        )
        connection.executeSQL(buildUpdateMediaKindSql(CACHED_ITEM_MEDIA_KIND_IMAGE, "image/", IMAGE_EXT_SUPPORT))
        connection.executeSQL(buildUpdateMediaKindSql(CACHED_ITEM_MEDIA_KIND_VIDEO, "video/", VIDEO_EXT_SUPPORT))
    }
}

private fun buildUpdateMediaKindSql(
    mediaKind: Int,
    mimePrefix: String,
    extensions: List<String>,
): String {
    val extensionMatches = extensions.joinToString(" OR ") { extension ->
        "LOWER(name) LIKE '%.${extension.lowercase()}'"
    }
    return """
        UPDATE cached_items
        SET media_kind = $mediaKind
        WHERE is_directory = 0
        AND (
            mime_type LIKE '$mimePrefix%'
            OR $extensionMatches
        )
    """.trimIndent()
}

internal object SourceCacheDatabaseProvider {
    val database: SourceCacheDatabase by lazy {
        getSourceCacheDatabaseBuilder()
            .addMigrations(sourceCacheMigration2To3)
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }
}
