@file:OptIn(ExperimentalTime::class)

package com.alpha.showcase.common.cache.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Entity(
    tableName = "gallery_source_media",
    indices = [
        Index(value = ["source_name"], unique = false),
        Index(value = ["added_at"], unique = false),
        Index(value = ["source_name", "media_uri"], unique = true),
    ]
)
@Serializable
data class GallerySourceMedia(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "source_name")
    val sourceName: String,

    @ColumnInfo(name = "media_uri")
    val mediaUri: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = Clock.System.now().toEpochMilliseconds(),
)

