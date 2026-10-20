package com.alpha.showcase.common.sync

import com.alpha.showcase.common.storage.objectStoreOf
import dev.whyoleg.cryptography.random.CryptographyRandom
import getPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.uuid.Uuid

object ConfigSyncDomains {
    const val SOURCES = "sources"

    /** Settings, appearance and AI providers synced in earlier versions; they now stay on each device. */
    val RETIRED = setOf("settings", "preference", "ai_config")
}

/**
 * App-wide entry point for configuration sync. Platforms without a cloud key-value store keep the
 * [ConfigSyncStatus.Unsupported] status and every call here is a no-op. Local edits reach sync
 * through each domain's [ConfigSyncDomain.localEdits]; the stores themselves do not know about sync.
 */
object ConfigSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val coordinatorFlow = MutableStateFlow<ConfigSyncCoordinator?>(null)

    @Volatile
    private var coordinator: ConfigSyncCoordinator? = null

    val isSupported: Boolean get() = coordinator != null

    private val mutableStatus = MutableStateFlow<ConfigSyncStatus>(ConfigSyncStatus.Unsupported)
    val status: StateFlow<ConfigSyncStatus> = mutableStatus.asStateFlow()

    /** Ids from [ConfigSyncDomains] whose local data was just replaced by data from another device. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val changes: Flow<String> = coordinatorFlow.flatMapLatest { it?.changes ?: emptyFlow() }

    fun start() {
        if (coordinator != null) return
        val cloud = getPlatform().configSyncStore ?: return
        val created = ConfigSyncCoordinator(
            cloud = cloud,
            stateStore = objectStoreOf<ConfigSyncLocalState>("config_sync_state"),
            domains = listOf(SourcesSyncDomain()),
            retiredDomainIds = ConfigSyncDomains.RETIRED,
            scope = scope,
            now = { Clock.System.now().toEpochMilliseconds() },
            newId = { Uuid.random().toString() },
            newKey = { CryptographyRandom.Default.nextBytes(32) },
        )
        coordinator = created
        coordinatorFlow.value = created
        scope.launch { created.status.collect { mutableStatus.value = it } }
        created.start()
    }

    // The calls below run in the app-wide scope so that closing the UI that started them does not cancel them.
    // Starting undispatched queues each call on the coordinator's lock before returning, keeping the user's order.

    fun setEnabled(enabled: Boolean) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { coordinator?.setEnabled(enabled) }
    }

    fun syncNow() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { coordinator?.syncNow() }
    }

    fun clearCloudDataAndDisable() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { coordinator?.clearCloudData() }
    }
}
