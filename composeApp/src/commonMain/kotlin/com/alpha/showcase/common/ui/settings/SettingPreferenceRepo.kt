package com.alpha.showcase.common.ui.settings

import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.storage.objectStoreOf
import com.alpha.showcase.common.utils.isCurrentConfigCiphertext
import kotlinx.serialization.json.Json

class SettingPreferenceRepo {

    private val settingsStore = objectStoreOf<String>("settings")
    private val preferenceStore = objectStoreOf<String>("preference")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun getSettings(): Settings {
        val storedValue = settingsStore.get() ?: return Settings.getDefaultInstance()
        val rawJson = RConfig.decryptAsync(storedValue)
        val settings = json.decodeFromString<Settings>(rawJson)
        if (!storedValue.isCurrentConfigCiphertext()) {
            updateSettings(settings)
        }
        return settings
    }

    suspend fun updateSettings(settings: Settings) {
        val rawJson = json.encodeToString(Settings.serializer(), settings)
        settingsStore.set(RConfig.encryptAsync(rawJson))
    }


    suspend fun updatePreference(preference: GeneralPreference) {
        val rawJson = json.encodeToString(GeneralPreference.serializer(), preference)
        preferenceStore.set(RConfig.encryptAsync(rawJson))
    }

    suspend fun getPreference(): GeneralPreference {
        val storedValue = preferenceStore.get() ?: return GeneralPreference(0, 0)
        val rawJson = RConfig.decryptAsync(storedValue)
        val preference = json.decodeFromString<GeneralPreference>(rawJson)
        if (!storedValue.isCurrentConfigCiphertext()) {
            updatePreference(preference)
        }
        return preference
    }


}
