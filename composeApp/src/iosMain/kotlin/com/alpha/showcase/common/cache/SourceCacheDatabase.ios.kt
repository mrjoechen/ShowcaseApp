package com.alpha.showcase.common.cache

import androidx.room.Room
import androidx.room.RoomDatabase
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
    )
}
