package com.alpha.showcase.common.cache

import androidx.room3.Room
import androidx.room3.RoomDatabase
import com.alpha.showcase.worker.createSQLiteWasmWorker

actual fun getSourceCacheDatabaseBuilder(): RoomDatabase.Builder<SourceCacheDatabase> {
    return Room.databaseBuilder<SourceCacheDatabase>(
        name = "source_cache.db",
    ).setDriver(createSQLiteWasmWorker())
}
