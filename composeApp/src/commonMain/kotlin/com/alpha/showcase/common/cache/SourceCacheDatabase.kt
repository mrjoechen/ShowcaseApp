package com.alpha.showcase.common.cache

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.AutoMigration
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
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
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            ALTER TABLE cached_items
            ADD COLUMN media_kind INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_cached_items_source_type_source_key_media_kind
            ON cached_items(source_type, source_key, media_kind)
            """.trimIndent()
        )
        connection.execSQL(buildUpdateMediaKindSql(CACHED_ITEM_MEDIA_KIND_IMAGE, "image/", IMAGE_EXT_SUPPORT))
        connection.execSQL(buildUpdateMediaKindSql(CACHED_ITEM_MEDIA_KIND_VIDEO, "video/", VIDEO_EXT_SUPPORT))
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
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }
}
