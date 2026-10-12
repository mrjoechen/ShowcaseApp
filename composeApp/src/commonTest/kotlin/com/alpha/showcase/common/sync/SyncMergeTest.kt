package com.alpha.showcase.common.sync

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncMergeTest {

    private fun snapshot(vararg items: Pair<String, Int>, order: List<String>? = null) =
        SyncSnapshot(items.associate { (key, value) -> key to (JsonPrimitive(value) as JsonElement) }, order)

    @Test
    fun dataPresentBeforeFirstMergeLosesToCloudCopy() {
        val observed = DomainSyncState().observe(snapshot("theme" to 1, "local" to 5), now = 100)
        assertEquals(0L, observed.clocks["theme"])

        val remote = SyncPayload(items = mapOf("theme" to SyncItem(50, JsonPrimitive(2))))
        val result = mergeDomain(observed, snapshot("theme" to 1, "local" to 5), remote, now = 100)

        assertEquals(JsonPrimitive(2), result.local.items["theme"])
        assertEquals(JsonPrimitive(5), result.local.items["local"], "items only on this device are kept")
        assertTrue(result.localChanged)
        assertTrue(result.state.merged)
    }

    @Test
    fun firstMergeKeepsDataDeletedOnAnotherDevice() {
        val observed = DomainSyncState().observe(snapshot("nas" to 1), now = 500)
        val remote = SyncPayload(tombstones = mapOf("nas" to 300))

        val result = mergeDomain(observed, snapshot("nas" to 1), remote, now = 500)

        assertFalse(result.localChanged)
        assertEquals(SyncItem(500, JsonPrimitive(1)), result.payload.items["nas"], "republished as a newer edit")
        assertTrue(result.payload.tombstones.isEmpty())
    }

    @Test
    fun laterLocalEditWinsAndIsPublished() {
        val base = mergeDomain(DomainSyncState(), snapshot("theme" to 1), null, now = 10).state
        val edited = base.observe(snapshot("theme" to 3), now = 200)
        val remote = SyncPayload(items = mapOf("theme" to SyncItem(150, JsonPrimitive(2))))

        val result = mergeDomain(edited, snapshot("theme" to 3), remote, now = 200)

        assertFalse(result.localChanged)
        assertEquals(SyncItem(200, JsonPrimitive(3)), result.payload.items["theme"])
    }

    @Test
    fun deletionBeatsOlderCopyAndNewerCopyBeatsDeletion() {
        val base = mergeDomain(DomainSyncState(), snapshot("a" to 1), null, now = 10).state
        val deleted = base.observe(snapshot(), now = 300)
        assertEquals(300L, deleted.tombstones["a"])

        val older = mergeDomain(deleted, snapshot(), SyncPayload(mapOf("a" to SyncItem(10, JsonPrimitive(1)))), now = 300)
        assertNull(older.local.items["a"])
        assertEquals(300L, older.payload.tombstones["a"])

        val newer = mergeDomain(deleted, snapshot(), SyncPayload(mapOf("a" to SyncItem(400, JsonPrimitive(9)))), now = 400)
        assertEquals(JsonPrimitive(9), newer.local.items["a"])
        assertTrue(newer.payload.tombstones.isEmpty())
    }

    @Test
    fun oldTombstonesAreCollected() {
        val remote = SyncPayload(tombstones = mapOf("gone" to 1))
        val result = mergeDomain(DomainSyncState(merged = true), snapshot(), remote, now = TOMBSTONE_RETENTION_MILLIS + 2)
        assertTrue(result.payload.tombstones.isEmpty())
    }

    @Test
    fun equalClocksConvergeOnEveryDevice() {
        val stateA = DomainSyncState(merged = true, clocks = mapOf("k" to 5), hashes = mapOf("k" to syncHash(JsonPrimitive(1))))
        val stateB = DomainSyncState(merged = true, clocks = mapOf("k" to 5), hashes = mapOf("k" to syncHash(JsonPrimitive(2))))
        val onA = mergeDomain(stateA, snapshot("k" to 1), SyncPayload(mapOf("k" to SyncItem(5, JsonPrimitive(2)))), now = 6)
        val onB = mergeDomain(stateB, snapshot("k" to 2), SyncPayload(mapOf("k" to SyncItem(5, JsonPrimitive(1)))), now = 6)
        assertEquals(onA.local.items["k"], onB.local.items["k"])
    }

    @Test
    fun orderFollowsNewestEditAndKeepsEveryItem() {
        val base = mergeDomain(DomainSyncState(), snapshot("a" to 1, "b" to 2, order = listOf("a", "b")), null, now = 10).state
        val local = snapshot("a" to 1, "b" to 2, "c" to 3, order = listOf("a", "b", "c"))
        val observed = base.observe(local, now = 20)
        val remote = SyncPayload(
            items = mapOf("a" to SyncItem(10, JsonPrimitive(1)), "b" to SyncItem(10, JsonPrimitive(2))),
            order = listOf("b", "a"),
            orderUpdatedAt = 30,
        )

        val result = mergeDomain(observed, local, remote, now = 30)

        assertEquals(listOf("b", "a", "c"), result.local.order)
        assertEquals(30L, result.payload.orderUpdatedAt)
    }

    @Test
    fun rebaseAcceptsWhatTheDomainStored() {
        val merged = mergeDomain(DomainSyncState(), snapshot(), SyncPayload(mapOf("new" to SyncItem(5, JsonPrimitive(1)))), now = 5)
        val rebased = merged.state.rebase(snapshot())
        val next = rebased.observe(snapshot(), now = 6)
        assertTrue(next.tombstones.isEmpty(), "an item this version cannot store must not be deleted for others")
    }
}
