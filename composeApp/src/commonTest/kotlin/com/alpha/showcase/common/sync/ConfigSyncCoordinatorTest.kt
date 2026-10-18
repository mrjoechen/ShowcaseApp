package com.alpha.showcase.common.sync

import com.alpha.showcase.common.storage.ObjectStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigSyncCoordinatorTest {

    @Test
    fun secondDeviceAdoptsCloudDataAndKeepsItsOwnItems() = runTest {
        val cloud = FakeCloud()
        val clock = Clock(1_000)
        val a = Device(cloud, clock, "a", mapOf("theme" to 1, "only-a" to 1), backgroundScope)
        val b = Device(cloud, clock, "b", mapOf("theme" to 2, "only-b" to 2), backgroundScope)

        a.coordinator.setEnabled(true)
        clock.time += 10
        b.coordinator.setEnabled(true)
        clock.time += 10
        a.coordinator.syncNow()

        val expected = mapOf("theme" to 1, "only-a" to 1, "only-b" to 2)
        assertEquals(expected, a.domain.values)
        assertEquals(expected, b.domain.values)
        assertIs<ConfigSyncStatus.Synced>(b.coordinator.status.value)
    }

    @Test
    fun editsAndDeletionsPropagateBetweenDevices() = runTest {
        val cloud = FakeCloud()
        val clock = Clock(1_000)
        val a = Device(cloud, clock, "a", mapOf("x" to 1, "y" to 1), backgroundScope)
        val b = Device(cloud, clock, "b", emptyMap(), backgroundScope)
        a.coordinator.setEnabled(true)
        b.coordinator.setEnabled(true)
        assertEquals(mapOf("x" to 1, "y" to 1), b.domain.values)

        clock.time += 100
        b.domain.values = mapOf("x" to 5)
        b.coordinator.syncNow()
        clock.time += 100
        a.coordinator.syncNow()

        assertEquals(mapOf("x" to 5), a.domain.values)
    }

    @Test
    fun cloudPayloadDoesNotContainPlaintextSecrets() = runTest {
        val cloud = FakeCloud()
        val device = Device(cloud, Clock(1), "a", emptyMap(), backgroundScope, secret = "super-secret-token")

        device.coordinator.setEnabled(true)

        assertFalse("super-secret-token" in cloud.values.getValue(syncCloudKey(DOMAIN)))
        assertTrue(SYNC_META_KEY in cloud.values)
    }

    @Test
    fun payloadFromNewerSchemaIsLeftUntouched() = runTest {
        val cloud = FakeCloud()
        val future = SyncEnvelope(schema = 9, minReader = 9, updatedAt = 1, deviceId = "future", keyId = "k", payload = "?")
        val raw = Json.encodeToString(SyncEnvelope.serializer(), future)
        cloud.values[syncCloudKey(DOMAIN)] = raw
        val device = Device(cloud, Clock(1), "a", mapOf("x" to 1), backgroundScope)

        device.coordinator.setEnabled(true)

        assertEquals(ConfigSyncStatus.NeedsUpgrade, device.coordinator.status.value)
        assertEquals(raw, cloud.values[syncCloudKey(DOMAIN)])
    }

    @Test
    fun accountChangeTurnsSyncOff() = runTest {
        val cloud = FakeCloud()
        val device = Device(cloud, Clock(1), "a", mapOf("x" to 1), backgroundScope)
        device.coordinator.start()
        runCurrent()
        device.coordinator.setEnabled(true)
        val writes = cloud.writes

        cloud.events.emit(CloudChange.AccountChanged)
        runCurrent()
        device.domain.values = mapOf("x" to 2)
        device.coordinator.syncNow()

        assertEquals(ConfigSyncStatus.AccountChanged, device.coordinator.status.value)
        assertEquals(writes, cloud.writes)
    }

    @Test
    fun localChangesAreBatchedIntoOneUpload() = runTest {
        val cloud = FakeCloud()
        val device = Device(cloud, Clock(1), "a", mapOf("x" to 1), backgroundScope)
        device.coordinator.start()
        runCurrent()
        device.coordinator.setEnabled(true)
        val writes = cloud.writes

        device.domain.values = mapOf("x" to 2)
        repeat(3) { device.domain.localEdits.tryEmit(Unit) }
        runCurrent()
        advanceTimeBy(500)
        assertEquals(writes, cloud.writes)
        advanceTimeBy(3_000)
        cloud.writeCount.awaitUntil { it > writes }
        runCurrent()

        assertEquals(writes + 1, cloud.writes)
    }

    @Test
    fun remoteUpdateRefreshesListeners() = runTest {
        val cloud = FakeCloud()
        val clock = Clock(1)
        val a = Device(cloud, clock, "a", mapOf("x" to 1), backgroundScope)
        val b = Device(cloud, clock, "b", emptyMap(), backgroundScope)
        val received = MutableStateFlow(emptyList<String>())
        b.coordinator.start()
        backgroundScope.launch { b.coordinator.changes.collect { id -> received.update { it + id } } }
        runCurrent()
        b.coordinator.setEnabled(true)

        a.coordinator.setEnabled(true)
        cloud.events.emit(CloudChange.Updated(setOf(syncCloudKey(DOMAIN)), initialSync = false))
        received.awaitUntil { it.isNotEmpty() }
        runCurrent()

        assertEquals(listOf(DOMAIN), received.value)
        assertEquals(mapOf("x" to 1), b.domain.values)
    }

    /**
     * Waits on the wall clock until [predicate] holds. On web the payload cipher awaits WebCrypto promises, which
     * runCurrent() and advanceTimeBy() cannot drive, and a virtual-time timeout would fire before they settle.
     */
    private suspend fun <T> StateFlow<T>.awaitUntil(predicate: (T) -> Boolean): T =
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { first(predicate) } }

    private class Clock(var time: Long)

    private class Device(
        cloud: FakeCloud,
        clock: Clock,
        name: String,
        initial: Map<String, Int>,
        scope: CoroutineScope,
        secret: String? = null,
    ) {
        val domain = MapDomain(initial, secret)
        private var ids = 0
        val coordinator = ConfigSyncCoordinator(
            cloud = cloud,
            stateStore = MemoryStore(),
            domains = listOf(domain),
            scope = scope,
            now = { clock.time },
            newId = { "$name-${ids++}" },
            newKey = { Random(name.hashCode()).nextBytes(32) },
        )
    }

    /** Integer settings plus an optional secret that is synced but never written back. */
    private class MapDomain(initial: Map<String, Int>, private val secret: String?) : ConfigSyncDomain {
        override val id = DOMAIN
        override val localEdits = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
        var values = initial

        override suspend fun read(): SyncSnapshot {
            val items: Map<String, JsonElement> = values.mapValues { JsonPrimitive(it.value) }
            return SyncSnapshot(if (secret == null) items else items + ("secret" to JsonPrimitive(secret)))
        }

        override suspend fun write(snapshot: SyncSnapshot) {
            values = snapshot.items
                .filterKeys { it != "secret" }
                .mapValues { (it.value as JsonPrimitive).content.toInt() }
        }
    }

    private class FakeCloud : CloudKeyValueStore {
        val values = mutableMapOf<String, String>()
        val events = MutableSharedFlow<CloudChange>()
        val writeCount = MutableStateFlow(0)
        val writes get() = writeCount.value
        override val isAvailable: StateFlow<Boolean> = MutableStateFlow(true)
        override val changes: Flow<CloudChange> = events
        override fun get(key: String) = values[key]
        override fun set(key: String, value: String) {
            values[key] = value
            writeCount.update { it + 1 }
        }
        override fun remove(key: String) {
            values.remove(key)
        }
        override fun synchronize() = Unit
    }

    private class MemoryStore : ObjectStore<ConfigSyncLocalState> {
        private var value: ConfigSyncLocalState? = null
        override suspend fun set(value: ConfigSyncLocalState) {
            this.value = value
        }
        override suspend fun delete() {
            value = null
        }
        override suspend fun get() = value
    }

    private companion object {
        const val DOMAIN = "test"
    }
}
