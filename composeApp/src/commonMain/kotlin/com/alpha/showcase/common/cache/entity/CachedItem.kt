@file:OptIn(ExperimentalTime::class)

package com.alpha.showcase.common.cache.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 缓存项实体 - 存储单个文件/项目的缓存数据
 */
@Entity(
    tableName = "cached_items",
    indices = [
        Index(value = ["source_type", "source_key", "parent_path"], unique = false),
        Index(value = ["source_type", "source_key"], unique = false),
        Index(value = ["created_at"], unique = false),
        Index(value = ["last_accessed"], unique = false),
        Index(value = ["source_type", "source_key", "path"], unique = true)
    ]
)
@Serializable
data class CachedItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 数据源类型 (ftp, smb, sftp, webdav, github, s3 等) */
    @ColumnInfo(name = "source_type")
    val sourceType: String,

    /** 数据源唯一标识键 */
    @ColumnInfo(name = "source_key")
    val sourceKey: String,

    /** 父路径 (用于构建层次结构) */
    @ColumnInfo(name = "parent_path")
    val parentPath: String,

    /** 项目名称 */
    @ColumnInfo(name = "name")
    val name: String,

    /** 完整路径/URL */
    @ColumnInfo(name = "path")
    val path: String,

    /** 是否为目录 */
    @ColumnInfo(name = "is_directory")
    val isDirectory: Boolean,

    /** 文件大小 */
    @ColumnInfo(name = "size")
    val size: Long,

    /** MIME类型/文件扩展名 */
    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    /** 修改时间戳 (毫秒) */
    @ColumnInfo(name = "modified_time")
    val modifiedTime: Long,

    /** 缓存创建时间 */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),

    /** 最后访问时间 */
    @ColumnInfo(name = "last_accessed")
    val lastAccessed: Long = Clock.System.now().toEpochMilliseconds(),

    /** 是否为递归查询的结果 */
    @ColumnInfo(name = "is_recursive")
    val isRecursive: Boolean = false,

    /** 额外的元数据 (JSON格式存储) */
    @ColumnInfo(name = "metadata")
    val metadata: String? = null,

    /** 同步版本号，用于增量同步时标记当前批次 */
    @ColumnInfo(name = "sync_version", defaultValue = "0")
    val syncVersion: Long = 0
)
