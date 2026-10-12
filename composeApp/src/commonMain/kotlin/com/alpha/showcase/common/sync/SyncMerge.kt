package com.alpha.showcase.common.sync

import kotlinx.serialization.json.JsonElement

internal const val TOMBSTONE_RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1000

/**
 * Records local edits since the last observation. Items seen for the first time in a fresh state get
 * clock 0, so data that existed before sync was enabled never overrides cloud data on first merge.
 */
internal fun DomainSyncState.observe(local: SyncSnapshot, now: Long): DomainSyncState {
    val fresh = !merged && hashes.isEmpty() && tombstones.isEmpty()
    val clocks = clocks.toMutableMap()
    val hashes = hashes.toMutableMap()
    val tombstones = tombstones.toMutableMap()
    fun stamp(previous: Long?): Long = if (fresh) 0 else maxOf(now, (previous ?: 0) + 1)

    local.items.forEach { (key, value) ->
        val hash = syncHash(value)
        if (hashes[key] != hash) {
            clocks[key] = stamp(maxOf(clocks[key] ?: 0, tombstones[key] ?: 0))
            hashes[key] = hash
            tombstones.remove(key)
        }
    }
    (hashes.keys - local.items.keys).forEach { key ->
        tombstones[key] = stamp(clocks[key])
        clocks.remove(key)
        hashes.remove(key)
    }

    var order = order
    var orderHash = orderHash
    var orderUpdatedAt = orderUpdatedAt
    local.order?.let { localOrder ->
        val hash = syncHash(localOrder)
        if (hash != orderHash) {
            order = localOrder
            orderHash = hash
            orderUpdatedAt = stamp(orderUpdatedAt)
        }
    }
    return copy(
        clocks = clocks,
        hashes = hashes,
        tombstones = tombstones,
        order = order,
        orderHash = orderHash,
        orderUpdatedAt = orderUpdatedAt,
    )
}

internal data class DomainMergeResult(
    val local: SyncSnapshot,
    val localChanged: Boolean,
    val payload: SyncPayload,
    val state: DomainSyncState,
)

/**
 * Merges an observed local [state] with the cloud [remote] payload item by item. The newest change
 * wins; deletions are kept as tombstones so they win over older copies on other devices, except
 * that the first merge never deletes data the device already had.
 */
internal fun mergeDomain(
    state: DomainSyncState,
    local: SyncSnapshot,
    remote: SyncPayload?,
    now: Long,
): DomainMergeResult {
    val preferRemoteOnTie = !state.merged
    val remoteItems = remote?.items.orEmpty()
    val remoteTombstones = remote?.tombstones.orEmpty()
    val keys = local.items.keys + state.tombstones.keys + remoteItems.keys + remoteTombstones.keys

    val items = linkedMapOf<String, SyncItem>()
    val tombstones = linkedMapOf<String, Long>()
    keys.forEach { key ->
        val remoteEntry = remoteItems[key]?.let { Entry.Value(it.updatedAt, it.value) }
            ?: remoteTombstones[key]?.let { Entry.Deleted(it) }
        val localEntry = local.items[key]?.let { value ->
            // Data that predates sync on this device was never deleted here. A deletion recorded by
            // another device must not remove it on the first merge, so it counts as a newer edit.
            val revived = (remoteEntry as? Entry.Deleted)?.takeIf { preferRemoteOnTie }?.let { maxOf(now, it.time + 1) }
            Entry.Value(revived ?: state.clocks[key] ?: 0, value)
        } ?: state.tombstones[key]?.let { Entry.Deleted(it) }
        when (val winner = pickEntry(localEntry, remoteEntry, preferRemoteOnTie)) {
            is Entry.Value -> items[key] = SyncItem(winner.time, winner.value)
            is Entry.Deleted -> if (now - winner.time < TOMBSTONE_RETENTION_MILLIS) tombstones[key] = winner.time
            null -> Unit
        }
    }

    val usesOrder = local.order != null || remote?.order != null
    val remoteOrderWins = remote?.order != null && (
        remote.orderUpdatedAt > state.orderUpdatedAt ||
            (remote.orderUpdatedAt == state.orderUpdatedAt && (preferRemoteOnTie || state.order == null))
        )
    val winningOrder = if (remoteOrderWins) remote.order.orEmpty() else state.order ?: local.order.orEmpty()
    val orderUpdatedAt = if (remoteOrderWins) remote.orderUpdatedAt else state.orderUpdatedAt
    val order = if (usesOrder) {
        val fallback = (local.order.orEmpty() + remote?.order.orEmpty() + items.keys.sorted()).distinct()
        (winningOrder + fallback).distinct().filter { it in items }
    } else {
        null
    }

    val mergedLocal = SyncSnapshot(items.mapValues { it.value.value }, order)
    val localChanged = mergedLocal.items != local.items || (usesOrder && order != local.order)
    val payload = SyncPayload(items, tombstones, order, if (usesOrder) orderUpdatedAt else 0)
    val nextState = DomainSyncState(
        clocks = items.mapValues { it.value.updatedAt },
        hashes = items.mapValues { syncHash(it.value.value) },
        tombstones = tombstones,
        order = order,
        orderHash = order?.let(::syncHash),
        orderUpdatedAt = if (usesOrder) orderUpdatedAt else 0,
        merged = true,
    )
    return DomainMergeResult(mergedLocal, localChanged, payload, nextState)
}

/** Re-bases hashes on what the domain actually stored, so lossy local decoding is not seen as an edit. */
internal fun DomainSyncState.rebase(stored: SyncSnapshot): DomainSyncState = copy(
    hashes = stored.items.mapValues { syncHash(it.value) },
    clocks = clocks.filterKeys { it in stored.items } +
        (stored.items.keys - clocks.keys).associateWith { 0L },
    order = stored.order ?: order,
    orderHash = stored.order?.let(::syncHash) ?: orderHash,
)

private sealed interface Entry {
    val time: Long

    data class Value(override val time: Long, val value: JsonElement) : Entry
    data class Deleted(override val time: Long) : Entry
}

private fun pickEntry(local: Entry?, remote: Entry?, preferRemoteOnTie: Boolean): Entry? {
    if (local == null) return remote
    if (remote == null) return local
    if (local.time != remote.time) return if (local.time > remote.time) local else remote
    if (local == remote || preferRemoteOnTie) return remote
    // Deterministic on every device, so concurrent edits with equal clocks still converge.
    return when {
        local is Entry.Deleted -> local
        remote is Entry.Deleted -> remote
        else -> if (syncHash((local as Entry.Value).value) >= syncHash((remote as Entry.Value).value)) local else remote
    }
}

internal fun syncHash(value: JsonElement): String = fnv1a64(value.toString())

internal fun syncHash(order: List<String>): String = fnv1a64(order.joinToString("\u0000"))

/** Change detection only; not used for anything security relevant. */
private fun fnv1a64(text: String): String {
    var hash = -0x340d631b7bdddcdbL
    text.encodeToByteArray().forEach { byte ->
        hash = hash xor (byte.toLong() and 0xff)
        hash *= 0x100000001b3L
    }
    return hash.toULong().toString(16).padStart(16, '0')
}
