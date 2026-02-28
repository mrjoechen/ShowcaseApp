package com.alpha.showcase.common.ui.settings

import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.storage.objectStoreOf
import kotlinx.serialization.json.Json

class SettingPreferenceRepo {

    private val settingsStore = objectStoreOf<String>("settings")
    private val preferenceStore = objectStoreOf<String>("preference")
    private val legacySettingsStore = objectStoreOf<Settings>("settings")
    private val legacyPreferenceStore = objectStoreOf<GeneralPreference>("preference")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun getSettings(): Settings {
        val encryptedValue = runCatching { settingsStore.get() }.getOrNull()
        encryptedValue?.let {
            runCatching {
                val rawJson = RConfig.decrypt(it)
                json.decodeFromString<Settings>(rawJson)
            }.getOrNull()
        }?.let {
            return it
        }

        val legacyValue = runCatching { legacySettingsStore.get() }.getOrNull()
        if (legacyValue != null) {
            updateSettings(legacyValue)
            return legacyValue
        }
        return Settings.getDefaultInstance()
    }

    suspend fun updateSettings(settings: Settings) {
        val rawJson = json.encodeToString(Settings.serializer(), settings)
        settingsStore.set(RConfig.encrypt(rawJson))
    }


    suspend fun updatePreference(preference: GeneralPreference) {
        val rawJson = json.encodeToString(GeneralPreference.serializer(), preference)
        preferenceStore.set(RConfig.encrypt(rawJson))
    }

    suspend fun getPreference(): GeneralPreference {
        val encryptedValue = runCatching { preferenceStore.get() }.getOrNull()
        encryptedValue?.let {
            runCatching {
                val rawJson = RConfig.decrypt(it)
                json.decodeFromString<GeneralPreference>(rawJson)
            }.getOrNull()
        }?.let {
            return it
        }

        val legacyValue = runCatching { legacyPreferenceStore.get() }.getOrNull()
        if (legacyValue != null) {
            updatePreference(legacyValue)
            return legacyValue
        }
        return GeneralPreference(0, 0)
    }


}
