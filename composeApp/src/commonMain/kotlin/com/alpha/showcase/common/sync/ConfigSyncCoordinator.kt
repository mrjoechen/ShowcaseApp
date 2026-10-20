package com.alpha.showcase.common.sync

import com.alpha.showcase.common.storage.ObjectStore
import com.alpha.showcase.common.utils.decodePassAsync
import com.alpha.showcase.common.utils.encodePassAsync
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal const val SYNC_KEY_PREFIX = "showcase.sync.v1."
internal const val SYNC_META_KEY = "${SYNC_KEY_PREFIX}meta"
internal const val SYNC_SCHEMA = 1
internal const val SYNC_MIN_READER = 1
internal const val SYNC_MAX_VALUE_BYTES = 256 * 1024

internal fun syncCloudKey(domainId: String): String = SYNC_KEY_PREFIX + domainId

/**
 * Replicates configuration domains through a [CloudKeyValueStore]. Local storage stays the source of
 * truth for the UI; the cloud store is only a transport, merged item by item on every exchange.
 * Cloud copies of [retiredDomainIds], domains earlier versions synced, are removed while syncing.
 */
class ConfigSyncCoordinator internal constructor(
    private val cloud: CloudKeyValueStore,
    private val stateStore: ObjectStore<ConfigSyncLocalState>,
    private val domains: List<ConfigSyncDomain>,
    private val retiredDomainIds: Set<String> = emptySet(),
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val newId: () -> String,
    private val newKey: () -> ByteArray,
    private val debounceMillis: Long = 2_000,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val localChanges = Channel<String>(Channel.UNLIMITED)
    private val allIds = domains.map { it.id }
    private var state = ConfigSyncLocalState()
    private var loaded = false
    private var started = false

    private val mutableStatus = MutableStateFlow<ConfigSyncStatus>(ConfigSyncStatus.Disabled)
    val status: StateFlow<ConfigSyncStatus> = mutableStatus.asStateFlow()

    private val mutableChanges = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** Ids of domains whose local data was replaced by cloud data. */
    val changes: SharedFlow<String> = mutableChanges.asSharedFlow()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            val enabled = mutex.withLock {
                ensureLoaded()
                publishIdleStatus()
                state.enabled
            }
            if (enabled) {
                cloud.synchronize()
                sync(allIds)
            }
        }
        scope.launch { cloud.changes.collect(::handleCloudChange) }
        domains.forEach { domain ->
            // Subscribe before returning, so an edit made right after start is not missed.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                domain.localEdits.collect { notifyLocalChange(domain.id) }
            }
        }
        scope.launch {
            cloud.isAvailable.drop(1).collect { available ->
                if (available) sync(allIds) else mutex.withLock { publishIdleStatus() }
            }
        }
        scope.launch {
            while (true) {
                val pending = mutableSetOf(localChanges.receive())
                while (true) {
                    pending += withTimeoutOrNull(debounceMillis) { localChanges.receive() } ?: break
                }
                sync(pending)
            }
        }
    }

    /** Queues a domain whose local data changed; changes are batched before publishing. */
    internal fun notifyLocalChange(domainId: String) {
        localChanges.trySend(domainId)
    }

    suspend fun setEnabled(enabled: Boolean) {
        mutex.withLock {
            ensureLoaded()
            // Re-enabling starts a fresh merge: the cloud copy wins ties with pre-existing local data.
            state = state.copy(
                enabled = enabled,
                pausedByAccountChange = false,
                domains = emptyMap(),
                lastSyncedAt = 0,
            )
            persist()
            publishIdleStatus()
        }
        if (enabled) syncNow()
    }

    suspend fun syncNow() {
        cloud.synchronize()
        sync(allIds)
    }

    /** Removes this app's data from the cloud store and turns sync off on this device. */
    suspend fun clearCloudData() = mutex.withLock {
        ensureLoaded()
        (allIds + retiredDomainIds).forEach { cloud.remove(syncCloudKey(it)) }
        cloud.remove(SYNC_META_KEY)
        state = state.copy(enabled = false, knownKeys = emptyMap(), domains = emptyMap(), lastSyncedAt = 0)
        persist()
        publishIdleStatus()
    }

    internal suspend fun sync(ids: Collection<String>) = mutex.withLock {
        ensureLoaded()
        if (!state.enabled || !cloud.isAvailable.value) {
            publishIdleStatus()
            return@withLock
        }
        mutableStatus.value = ConfigSyncStatus.Syncing
        val outcomes = try {
            val key = resolveKey()
            removeRetiredDomains()
            domains.filter { it.id in ids }.map { syncDomain(it, key) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Napier.w("Config sync failed", error)
            listOf(DomainOutcome.Failed(error.message ?: error::class.simpleName.orEmpty()))
        }
        val failure = outcomes.filterIsInstance<DomainOutcome.Failed>().firstOrNull()
        mutableStatus.value = when {
            failure != null -> ConfigSyncStatus.Failed(failure.message)
            DomainOutcome.NeedsUpgrade in outcomes -> ConfigSyncStatus.NeedsUpgrade
            DomainOutcome.TooLarge in outcomes -> ConfigSyncStatus.QuotaExceeded
            DomainOutcome.Waiting in outcomes -> ConfigSyncStatus.Waiting
            else -> {
                state = state.copy(lastSyncedAt = now())
                ConfigSyncStatus.Synced(state.lastSyncedAt)
            }
        }
        persist()
    }

    private suspend fun handleCloudChange(change: CloudChange) {
        when (change) {
            is CloudChange.Updated -> {
                val ids = if (change.initialSync || SYNC_META_KEY in change.keys) {
                    allIds
                } else {
                    allIds.filter { syncCloudKey(it) in change.keys }
                }
                if (ids.isNotEmpty()) sync(ids)
            }

            CloudChange.AccountChanged -> mutex.withLock {
                ensureLoaded()
                if (state.enabled) {
                    // Never merge one account's credentials into another account without consent.
                    state = state.copy(enabled = false, pausedByAccountChange = true, domains = emptyMap(), lastSyncedAt = 0)
                    persist()
                }
                publishIdleStatus()
            }

            CloudChange.QuotaExceeded -> mutex.withLock {
                ensureLoaded()
                if (state.enabled) mutableStatus.value = ConfigSyncStatus.QuotaExceeded
            }
        }
    }

    private fun removeRetiredDomains() {
        retiredDomainIds.map(::syncCloudKey).filter { cloud.get(it) != null }.forEach(cloud::remove)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun resolveKey(): Pair<String, ByteArray> {
        val meta = cloud.get(SYNC_META_KEY)?.let { runCatching { json.decodeFromString<SyncMeta>(it) }.getOrNull() }
        val resolved = meta?.takeIf { it.currentKeyId in it.keys } ?: run {
            val keyId = newId()
            SyncMeta(keyId, meta?.keys.orEmpty() + (keyId to Base64.encode(newKey())))
                .also { cloud.set(SYNC_META_KEY, json.encodeToString(SyncMeta.serializer(), it)) }
        }
        // Keys that lost a concurrent first write stay known locally, so their payloads can be re-encrypted.
        state = state.copy(knownKeys = state.knownKeys + resolved.keys)
        return resolved.currentKeyId to Base64.decode(resolved.keys.getValue(resolved.currentKeyId))
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun syncDomain(domain: ConfigSyncDomain, key: Pair<String, ByteArray>): DomainOutcome = try {
        val cloudKey = syncCloudKey(domain.id)
        val envelope = cloud.get(cloudKey)?.let { raw ->
            runCatching { json.decodeFromString<SyncEnvelope>(raw) }
                .onFailure { Napier.w("Ignoring unreadable sync envelope for ${domain.id}") }
                .getOrNull()
        }
        if (envelope != null && envelope.minReader > SYNC_SCHEMA) return DomainOutcome.NeedsUpgrade
        val remote = envelope?.let {
            val remoteKey = state.knownKeys[it.keyId] ?: return DomainOutcome.Waiting
            json.decodeFromString<SyncPayload>(it.payload.decodePassAsync(Base64.decode(remoteKey)))
        }

        val local = domain.read()
        val observed = (state.domains[domain.id] ?: DomainSyncState()).observe(local, now())
        val merge = mergeDomain(observed, local, remote, now())
        var nextState = merge.state
        if (merge.localChanged) {
            domain.write(merge.local)
            val stored = domain.read()
            nextState = nextState.rebase(stored)
            // Data this version cannot store stays in the payload for newer devices; only real changes refresh UI.
            if (stored != local) mutableChanges.tryEmit(domain.id)
        }

        val needsPush = if (remote == null) !merge.payload.isEmpty else merge.payload != remote || envelope.keyId != key.first
        var outcome: DomainOutcome = DomainOutcome.Synced
        if (needsPush) {
            val encoded = json.encodeToString(
                SyncEnvelope.serializer(),
                SyncEnvelope(
                    schema = SYNC_SCHEMA,
                    minReader = SYNC_MIN_READER,
                    updatedAt = now(),
                    deviceId = state.deviceId,
                    keyId = key.first,
                    payload = json.encodeToString(SyncPayload.serializer(), merge.payload).encodePassAsync(key.second),
                ),
            )
            if (encoded.encodeToByteArray().size > SYNC_MAX_VALUE_BYTES) {
                Napier.w("Config sync domain ${domain.id} exceeds $SYNC_MAX_VALUE_BYTES bytes; kept local only")
                outcome = DomainOutcome.TooLarge
            } else {
                cloud.set(cloudKey, encoded)
            }
        }
        state = state.copy(domains = state.domains + (domain.id to nextState))
        outcome
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Napier.w("Config sync of ${domain.id} failed", error)
        DomainOutcome.Failed(error.message ?: error::class.simpleName.orEmpty())
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        state = runCatching { stateStore.get() }.getOrNull() ?: ConfigSyncLocalState()
        if (state.deviceId.isBlank()) state = state.copy(deviceId = newId())
        loaded = true
    }

    private suspend fun persist() {
        runCatching { stateStore.set(state) }.onFailure { Napier.w("Unable to save config sync state", it) }
    }

    private fun publishIdleStatus() {
        mutableStatus.value = when {
            state.pausedByAccountChange && !state.enabled -> ConfigSyncStatus.AccountChanged
            !state.enabled -> ConfigSyncStatus.Disabled
            !cloud.isAvailable.value -> ConfigSyncStatus.Unavailable
            state.lastSyncedAt > 0 -> ConfigSyncStatus.Synced(state.lastSyncedAt)
            else -> ConfigSyncStatus.Syncing
        }
    }

    private sealed interface DomainOutcome {
        data object Synced : DomainOutcome
        data object Waiting : DomainOutcome
        data object NeedsUpgrade : DomainOutcome
        data object TooLarge : DomainOutcome
        data class Failed(val message: String) : DomainOutcome
    }
}
