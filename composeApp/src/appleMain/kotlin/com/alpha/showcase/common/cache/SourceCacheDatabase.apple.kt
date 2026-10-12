package com.alpha.showcase.common.cache

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import getPlatform
import okio.FileSystem
import okio.Path.Companion.toPath

actual fun getSourceCacheDatabaseBuilder(): RoomDatabase.Builder<SourceCacheDatabase> {
    val dbPath = getPlatform().getConfigDirectory().toPath().resolve("source_cache.db")
    dbPath.parent?.let { parent ->
        FileSystem.SYSTEM.createDirectories(parent)
    }

    return Room.databaseBuilder<SourceCacheDatabase>(
        name = dbPath.toString(),
    ).setDriver(BundledSQLiteDriver())
}
