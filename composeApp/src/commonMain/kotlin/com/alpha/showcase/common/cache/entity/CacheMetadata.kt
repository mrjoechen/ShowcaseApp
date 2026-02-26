package com.alpha.showcase.common.cache.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 缓存元数据实体 - 记录每个数据源的缓存状态和更新策略
 */
@Entity(
    tableName = "cache_metadata",
    indices = [
        Index(value = ["source_type", "source_key"], unique = true),
        Index(value = ["last_updated"], unique = false),
        Index(value = ["next_update_time"], unique = false)
    ]
)
@Serializable
data class CacheMetadata(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 数据源类型 */
    @ColumnInfo(name = "source_type")
    val sourceType: String,

    /** 数据源唯一标识键 */
    @ColumnInfo(name = "source_key")
    val sourceKey: String,

    /** 最后更新时间 */
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long,

    /** 下次更新时间 */
    @ColumnInfo(name = "next_update_time")
    val nextUpdateTime: Long,

    /** 缓存的项目总数 */
    @ColumnInfo(name = "total_items")
    val totalItems: Int,

    /** 更新策略类型 */
    @ColumnInfo(name = "update_strategy")
    val updateStrategy: String,

    /** 是否为递归缓存 */
    @ColumnInfo(name = "is_recursive")
    val isRecursive: Boolean,

    /** 数据源版本/ETag (用于检测变更) */
    @ColumnInfo(name = "version")
    val version: String? = null,

    /** 缓存状态: VALID, INVALID, UPDATING */
    @ColumnInfo(name = "status")
    val status: String = STATUS_VALID,

    /** 数据源配置 hash，用于检测配置变更 */
    @ColumnInfo(name = "source_config_hash")
    val sourceConfigHash: String? = null
) {
    fun isValid() = status != STATUS_INVALID

    fun isSourceConfigChanged(currentConfigHash: String): Boolean {
        return sourceConfigHash != null && sourceConfigHash != currentConfigHash
    }

    companion object {
        const val STATUS_VALID = "VALID"
        const val STATUS_INVALID = "INVALID"
        const val STATUS_UPDATING = "UPDATING"

        const val STRATEGY_TTL = "TTL"
        const val STRATEGY_STALE_WHILE_REVALIDATE = "STALE_WHILE_REVALIDATE"
    }
}
