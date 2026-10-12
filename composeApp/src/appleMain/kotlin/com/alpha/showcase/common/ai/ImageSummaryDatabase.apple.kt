package com.alpha.showcase.common.ai

import androidx.room3.Room
import androidx.room3.RoomDatabase
import getPlatform
import okio.FileSystem
import okio.Path.Companion.toPath
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

internal actual fun imageSummaryDatabaseBuilder(): RoomDatabase.Builder<ImageSummaryDatabase> {
    val path = getPlatform().getConfigDirectory().toPath() / "image_summaries.db"
    path.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
    return Room.databaseBuilder<ImageSummaryDatabase>(name = path.toString()).setDriver(BundledSQLiteDriver())
}

