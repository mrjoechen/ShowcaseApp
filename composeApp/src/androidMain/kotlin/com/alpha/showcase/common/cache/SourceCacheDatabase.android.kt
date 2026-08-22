package com.alpha.showcase.common.cache

import AndroidApp
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

actual fun getSourceCacheDatabaseBuilder(): RoomDatabase.Builder<SourceCacheDatabase> {
    val context = AndroidApp.applicationContext
    val dbFile = context.getDatabasePath("source_cache.db")
    dbFile.parentFile?.mkdirs()

    return Room.databaseBuilder<SourceCacheDatabase>(
        context = context,
        name = dbFile.absolutePath,
    ).setDriver(BundledSQLiteDriver())
}
