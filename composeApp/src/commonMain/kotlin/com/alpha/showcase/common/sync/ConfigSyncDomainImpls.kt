package com.alpha.showcase.common.sync

import com.alpha.showcase.common.ai.AiProfile
import com.alpha.showcase.common.ai.AiServices
import com.alpha.showcase.common.ai.LocalAiStyleCatalog
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
import com.alpha.showcase.common.repo.SourceListRepo
import com.alpha.showcase.common.ui.settings.GeneralPreference
import com.alpha.showcase.common.ui.settings.SettingPreferenceRepo
import com.alpha.showcase.common.ui.settings.Settings
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val syncJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

/** Showcase modes and playback options; every top-level field is one item. */
internal class SettingsSyncDomain(
    private val repo: SettingPreferenceRepo = SettingPreferenceRepo(),
) : ConfigSyncDomain {
    override val id: String = ConfigSyncDomains.SETTINGS
    override val localEdits: Flow<Unit> get() = SettingPreferenceRepo.settingsEdits

    override suspend fun read(): SyncSnapshot =
        SyncSnapshot(syncJson.encodeToJsonElement(Settings.serializer(), repo.getSettings()).jsonObject)

    override suspend fun write(snapshot: SyncSnapshot) {
        val defaults = syncJson.encodeToJsonElement(Settings.serializer(), Settings.getDefaultInstance()).jsonObject
        val settings = syncJson.decodeFromJsonElement(Settings.serializer(), JsonObject(defaults + snapshot.items))
        repo.applySyncedSettings(settings)
    }
}

/** Appearance choices only; consent, cache size and update checks are decisions per device. */
internal class PreferenceSyncDomain(
    private val repo: SettingPreferenceRepo = SettingPreferenceRepo(),
) : ConfigSyncDomain {
    override val id: String = ConfigSyncDomains.PREFERENCE
    override val localEdits: Flow<Unit> get() = SettingPreferenceRepo.preferenceEdits

    override suspend fun read(): SyncSnapshot {
        val local = syncJson.encodeToJsonElement(GeneralPreference.serializer(), repo.getPreference()).jsonObject
        return SyncSnapshot(local.filterKeys { it in SYNCED_PREFERENCE_FIELDS })
    }

    override suspend fun write(snapshot: SyncSnapshot) {
        val local = syncJson.encodeToJsonElement(GeneralPreference.serializer(), repo.getPreference()).jsonObject
        val synced = snapshot.items.filterKeys { it in SYNCED_PREFERENCE_FIELDS }
        val preference = syncJson.decodeFromJsonElement(GeneralPreference.serializer(), JsonObject(local + synced))
        repo.applySyncedPreference(preference)
    }

    internal companion object {
        val SYNCED_PREFERENCE_FIELDS = setOf("language", "darkMode", "themeStyle")
    }
}

/** Remote sources keyed by their unique name; local folders and photo libraries stay on the device. */
internal class SourcesSyncDomain(
    private val repo: SourceListRepo = SourceListRepo(),
) : ConfigSyncDomain {
    override val id: String = ConfigSyncDomains.SOURCES
    override val localEdits: Flow<Unit> get() = SourceListRepo.localEdits
    private val serializer = PolymorphicSerializer(RemoteApi::class)

    override suspend fun read(): SyncSnapshot {
        val sources = repo.readSyncableSources()
        return SyncSnapshot(
            items = sources.associate { it.name to StorageSourceSerializer.sourceJson.encodeToJsonElement(serializer, it) },
            order = sources.map { it.name },
        )
    }

    override suspend fun write(snapshot: SyncSnapshot) {
        val names = (snapshot.order.orEmpty() + snapshot.items.keys).distinct().filter { it in snapshot.items }
        val sources = names.mapNotNull { name ->
            runCatching { StorageSourceSerializer.sourceJson.decodeFromJsonElement(serializer, snapshot.items.getValue(name)) }
                .onFailure { Napier.w("Skipping synced source this version cannot read") }
                .getOrNull()
                ?.takeIf { it.name == name }
        }
        repo.applySyncedSources(sources)
    }
}

/** AI provider profiles and selections; generation tasks and results stay on the device. */
internal class AiConfigSyncDomain : ConfigSyncDomain {
    override val id: String = ConfigSyncDomains.AI_CONFIG
    override val localEdits: Flow<Unit> get() = AiServices.engine.configEdits

    override suspend fun read(): SyncSnapshot {
        val engine = AiServices.engine
        val library = engine.loadedLibrary()
        val items = linkedMapOf<String, JsonElement>()
        library.activeProfiles.forEach { profile ->
            items[PROFILE_PREFIX + profile.id] = syncJson.encodeToJsonElement(
                SyncedAiProfile.serializer(),
                SyncedAiProfile.from(profile, engine.decryptCredential(profile.encryptedToken)),
            )
        }
        items[GENERATION_PROFILE] = library.generationProfileId?.let(::JsonPrimitive) ?: JsonNull
        items[UNDERSTANDING_PROFILE] = library.understandingProfileId?.let(::JsonPrimitive) ?: JsonNull
        items[STYLE] = JsonPrimitive(library.styleKey)
        items[FACE_PRIVACY] = JsonPrimitive(library.facePrivacyEnabled)
        return SyncSnapshot(items)
    }

    override suspend fun write(snapshot: SyncSnapshot) {
        val engine = AiServices.engine
        val synced = snapshot.items
            .filterKeys { it.startsWith(PROFILE_PREFIX) }
            .values
            .mapNotNull { runCatching { syncJson.decodeFromJsonElement(SyncedAiProfile.serializer(), it) }.getOrNull() }
            .associateBy { it.id }
        engine.applySyncedConfig { library ->
            val activeById = library.activeProfiles.associateBy { it.id }
            val unchanged = synced.values.filter { remote ->
                val local = activeById[remote.id] ?: return@filter false
                SyncedAiProfile.from(local, engine.decryptCredential(local.encryptedToken)) == remote
            }.mapTo(mutableSetOf()) { it.id }
            val replaced = synced.keys - unchanged
            val profiles = library.profiles.map { profile ->
                val retire = !profile.archived && (profile.id !in synced || profile.id in replaced)
                if (retire) profile.copy(archived = true) else profile
            } + replaced.map { id ->
                val remote = synced.getValue(id)
                remote.toProfile(
                    revision = (library.profiles.filter { it.id == id }.maxOfOrNull { it.revision } ?: 0) + 1,
                    encryptedToken = engine.encryptCredential(remote.token),
                )
            }
            val activeIds = profiles.filterNot { it.archived }.mapTo(mutableSetOf()) { it.id }
            fun selection(key: String, local: String?): String? {
                val element = snapshot.items[key] ?: return local?.takeIf { it in activeIds }
                return (element as? JsonPrimitive)?.contentOrNull?.takeIf { it in activeIds }
            }
            val style = snapshot.items[STYLE]?.jsonPrimitive?.contentOrNull
                ?.takeIf { key -> LocalAiStyleCatalog.styles().any { it.key == key } }
            library.copy(
                profiles = profiles,
                generationProfileId = selection(GENERATION_PROFILE, library.generationProfileId),
                understandingProfileId = selection(UNDERSTANDING_PROFILE, library.understandingProfileId),
                styleKey = style ?: library.styleKey,
                facePrivacyEnabled = snapshot.items[FACE_PRIVACY]?.jsonPrimitive?.booleanOrNull ?: library.facePrivacyEnabled,
            )
        }
    }

    private companion object {
        const val PROFILE_PREFIX = "profile:"
        const val GENERATION_PROFILE = "generationProfileId"
        const val UNDERSTANDING_PROFILE = "understandingProfileId"
        const val STYLE = "styleKey"
        const val FACE_PRIVACY = "facePrivacyEnabled"
    }
}

/** Profile as exchanged between devices: plaintext token, no local revision history. */
@Serializable
internal data class SyncedAiProfile(
    val id: String,
    val name: String,
    val providerId: String,
    val model: String,
    val baseUrl: String,
    val token: String,
    val allowInsecureHttp: Boolean = false,
) {
    override fun toString(): String = "SyncedAiProfile(id=$id, credentials=<redacted>)"

    fun toProfile(revision: Int, encryptedToken: String) = AiProfile(
        id = id,
        revision = revision,
        name = name,
        providerId = providerId,
        model = model,
        baseUrl = baseUrl,
        encryptedToken = encryptedToken,
        allowInsecureHttp = allowInsecureHttp,
    )

    companion object {
        fun from(profile: AiProfile, token: String) = SyncedAiProfile(
            id = profile.id,
            name = profile.name,
            providerId = profile.providerId,
            model = profile.model,
            baseUrl = profile.baseUrl,
            token = token,
            allowInsecureHttp = profile.allowInsecureHttp,
        )
    }
}
