package com.alpha.showcase.common.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** One synced value with the time it was last changed by any device. */
@Serializable
data class SyncItem(val updatedAt: Long, val value: JsonElement)

/** Plaintext content of one sync domain as published to the cloud. */
@Serializable
data class SyncPayload(
    val items: Map<String, SyncItem> = emptyMap(),
    val tombstones: Map<String, Long> = emptyMap(),
    val order: List<String>? = null,
    val orderUpdatedAt: Long = 0,
) {
    val isEmpty: Boolean get() = items.isEmpty() && tombstones.isEmpty() && order.isNullOrEmpty()
}

/** Cloud value wrapper. [minReader] lets a newer schema stop older apps from overwriting it. */
@Serializable
data class SyncEnvelope(
    val schema: Int,
    val minReader: Int,
    val updatedAt: Long,
    val deviceId: String,
    val keyId: String,
    val payload: String,
)

/**
 * Obfuscation keys shared through the cloud store itself. They keep values out of plain sight but
 * are not a security boundary: anyone with the cloud data also has these keys.
 */
@Serializable
data class SyncMeta(
    val currentKeyId: String,
    val keys: Map<String, String>,
)

/** What a domain currently holds locally. Values equal to defaults are omitted. */
data class SyncSnapshot(
    val items: Map<String, JsonElement>,
    val order: List<String>? = null,
) {
    companion object {
        val Empty = SyncSnapshot(emptyMap())
    }
}

/** Per-device bookkeeping of what has been observed and published for one domain. */
@Serializable
data class DomainSyncState(
    val clocks: Map<String, Long> = emptyMap(),
    val hashes: Map<String, String> = emptyMap(),
    val tombstones: Map<String, Long> = emptyMap(),
    val order: List<String>? = null,
    val orderHash: String? = null,
    val orderUpdatedAt: Long = 0,
    /** False until the first merge with the cloud; ties then favour the cloud copy. */
    val merged: Boolean = false,
)

@Serializable
data class ConfigSyncLocalState(
    val enabled: Boolean = false,
    val deviceId: String = "",
    val knownKeys: Map<String, String> = emptyMap(),
    val domains: Map<String, DomainSyncState> = emptyMap(),
    val lastSyncedAt: Long = 0,
    val pausedByAccountChange: Boolean = false,
)

/** One unit of configuration that syncs as a whole set of keyed items. */
interface ConfigSyncDomain {
    val id: String

    /** Emits after the user changes this domain's data on this device; never for [write]. */
    val localEdits: Flow<Unit>

    /** Syncable local content, with secrets in plaintext; device-specific data must be excluded. */
    suspend fun read(): SyncSnapshot

    /** Replaces the syncable part of local data; keys absent from [snapshot] mean default/removed. */
    suspend fun write(snapshot: SyncSnapshot)
}
