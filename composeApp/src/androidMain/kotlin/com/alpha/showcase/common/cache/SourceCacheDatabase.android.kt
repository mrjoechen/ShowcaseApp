package com.alpha.showcase.common.cache

import AndroidApp
import androidx.room.Room
import androidx.room.RoomDatabase

actual fun getSourceCacheDatabaseBuilder(): RoomDatabase.Builder<SourceCacheDatabase> {
    val context = AndroidApp.applicationContext
    val dbFile = context.getDatabasePath("source_cache.db")
    dbFile.parentFile?.mkdirs()

    return Room.databaseBuilder<SourceCacheDatabase>(
        context = context,
        name = dbFile.absolutePath,
    )
}
