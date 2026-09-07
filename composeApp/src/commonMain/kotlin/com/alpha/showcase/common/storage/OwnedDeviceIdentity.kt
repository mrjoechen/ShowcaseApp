package com.alpha.showcase.common.storage

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val DEVICE_UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

internal fun requireDeviceId(value: String): String {
    require(value.matches(DEVICE_UUID)) { "A UUIDv4 device ID is required" }
    return value
}

@Serializable
internal data class OwnedDeviceBinding(val deviceId: String, val ownerId: String)

internal class DeviceRegistrationConflictException : Exception("Device belongs to another user")

internal class DurableDeviceIdProvider(
    private val read: () -> String?,
    private val save: (String) -> Unit,
    private val generate: () -> String,
) {
    private val lock = SynchronizedObject()
    private var cached: String? = null

    fun getOrCreate(): String = synchronized(lock) {
        cached ?: runCatching { read()?.trim()?.lowercase()?.takeIf { it.matches(DEVICE_UUID) } }
            .getOrNull()?.also { cached = it } ?: createAndSave()
    }

    fun rotate(): String = synchronized(lock) { createAndSave() }

    private fun createAndSave(): String = requireDeviceId(generate()).also {
        cached = it
        // A restricted browser or unavailable native store still has a stable in-memory ID.
        runCatching { save(it) }
    }
}

internal class OwnedDeviceRegistrar(
    private val deviceIds: DurableDeviceIdProvider,
    private val readBinding: suspend () -> OwnedDeviceBinding?,
    private val saveBinding: suspend (OwnedDeviceBinding) -> Unit,
    private val register: suspend (String) -> Unit,
) {
    private val mutex = Mutex()
    private var registered: OwnedDeviceBinding? = null

    suspend fun ensureRegistered(ownerId: String): String = mutex.withLock {
        require(ownerId.isNotBlank()) { "Authenticated owner is required" }
        var deviceId = deviceIds.getOrCreate()
        if (registered == OwnedDeviceBinding(deviceId, ownerId)) return@withLock deviceId
        val previous = try {
            readBinding()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        if (previous?.deviceId == deviceId && previous.ownerId != ownerId) {
            deviceId = deviceIds.rotate()
        }
        try {
            register(deviceId)
        } catch (_: DeviceRegistrationConflictException) {
            deviceId = deviceIds.rotate()
            register(deviceId)
        }
        val binding = OwnedDeviceBinding(deviceId, ownerId)
        try {
            saveBinding(binding)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Server ownership was verified; storage availability must not broaden access.
        }
        registered = binding
        deviceId
    }
}

@OptIn(ExperimentalUuidApi::class)
internal val durableDeviceIds = DurableDeviceIdProvider(
    read = ::getDurableDeviceId,
    save = ::saveDurableDeviceId,
    generate = { Uuid.random().toString() },
)

fun getOrCreateDurableDeviceId(): String = durableDeviceIds.getOrCreate()
