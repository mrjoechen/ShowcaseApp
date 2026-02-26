package com.alpha.showcase.common.cache

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.alpha.showcase.common.cache.dao.CacheMetadataDao
import com.alpha.showcase.common.cache.dao.CachedItemDao
import com.alpha.showcase.common.cache.entity.CacheMetadata
import com.alpha.showcase.common.cache.entity.CachedItem
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        CachedItem::class,
        CacheMetadata::class,
    ],
    version = 1,
    exportSchema = true
)
@ConstructedBy(SourceCacheDatabaseConstructor::class)
abstract class SourceCacheDatabase : RoomDatabase() {

    abstract fun cachedItemDao(): CachedItemDao

    abstract fun cacheMetadataDao(): CacheMetadataDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object SourceCacheDatabaseConstructor : RoomDatabaseConstructor<SourceCacheDatabase> {
    override fun initialize(): SourceCacheDatabase
}

expect fun getSourceCacheDatabaseBuilder(): RoomDatabase.Builder<SourceCacheDatabase>

internal object SourceCacheDatabaseProvider {
    val database: SourceCacheDatabase by lazy {
        getSourceCacheDatabaseBuilder()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }
}
