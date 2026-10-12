package com.alpha.showcase.common.sync

import com.alpha.showcase.common.ai.aiFeaturesAvailable
import com.alpha.showcase.common.storage.objectStoreOf
import dev.whyoleg.cryptography.random.CryptographyRandom
import getPlatform
import isWeb
import kotlinx.coroutines.CoroutineScope
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
    const val SETTINGS = "settings"
    const val PREFERENCE = "preference"
    const val SOURCES = "sources"
    const val AI_CONFIG = "ai_config"
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
        val domains = buildList {
            add(SettingsSyncDomain())
            add(PreferenceSyncDomain())
            add(SourcesSyncDomain())
            if (aiFeaturesAvailable(isWeb())) add(AiConfigSyncDomain())
        }
        val created = ConfigSyncCoordinator(
            cloud = cloud,
            stateStore = objectStoreOf<ConfigSyncLocalState>("config_sync_state"),
            domains = domains,
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

    suspend fun setEnabled(enabled: Boolean) {
        coordinator?.setEnabled(enabled)
    }

    suspend fun syncNow() {
        coordinator?.syncNow()
    }

    suspend fun clearCloudDataAndDisable() {
        coordinator?.clearCloudData()
    }
}
