package com.alpha.showcase.common.ai

import androidx.room3.Room
import androidx.room3.RoomDatabase
import AndroidApp
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

internal actual fun imageSummaryDatabaseBuilder(): RoomDatabase.Builder<ImageSummaryDatabase> {
    val context = AndroidApp.applicationContext
    val file = context.getDatabasePath("image_summaries.db")
    file.parentFile?.mkdirs()
    return Room.databaseBuilder<ImageSummaryDatabase>(context = context, name = file.absolutePath).setDriver(BundledSQLiteDriver())
}

