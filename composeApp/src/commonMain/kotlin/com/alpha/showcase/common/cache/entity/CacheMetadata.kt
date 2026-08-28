package com.alpha.showcase.common.cache.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
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
    val sourceConfigHash: String? = null,

    /**
     * The sync_version of the last SUCCESSFULLY COMPLETED sync. Paged reads pin to
     * this so they never see an in-flight (partially-written) version. 0 = none yet.
     * Set only when a sync finishes; an in-progress sync's new rows do NOT change it.
     * defaultValue matches the 3->4 migration's ALTER (NOT NULL DEFAULT 0) so fresh
     * and migrated databases have the identical column definition.
     */
    @ColumnInfo(name = "committed_sync_version", defaultValue = "0")
    val committedSyncVersion: Long = 0
) {
    fun isValid() = status != STATUS_INVALID

    /**
     * True only when a DISPLAYABLE generation of rows exists (a sync reached a
     * terminal state and committed a version). [isValid] alone is NOT enough:
     * STATUS_UPDATING is "valid" but during a FIRST sync nothing is committed yet —
     * treating that as usable cache made concurrent callers (or a process restart
     * mid-first-sync) return an unpinned empty/mixed read instead of joining the
     * first-sync wait path.
     */
    fun hasDisplayableCache() = isValid() && committedSyncVersion > 0

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
