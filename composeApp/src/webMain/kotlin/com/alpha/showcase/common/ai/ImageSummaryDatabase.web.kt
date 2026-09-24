package com.alpha.showcase.common.ai

import androidx.room3.Room
import androidx.room3.RoomDatabase
import com.alpha.showcase.worker.createSQLiteWasmWorker

internal actual fun imageSummaryDatabaseBuilder(): RoomDatabase.Builder<ImageSummaryDatabase> {
    return Room.databaseBuilder<ImageSummaryDatabase>(name = "image_summaries.db").setDriver(createSQLiteWasmWorker())
}

