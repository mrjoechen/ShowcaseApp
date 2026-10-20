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
            storeSettings(settings)
        }
        return settings
    }

    suspend fun updateSettings(settings: Settings) {
        storeSettings(settings)
    }

    private suspend fun storeSettings(settings: Settings) {
        val rawJson = json.encodeToString(Settings.serializer(), settings)
        settingsStore.set(RConfig.encryptAsync(rawJson))
    }


    suspend fun updatePreference(preference: GeneralPreference) {
        storePreference(preference)
    }

    private suspend fun storePreference(preference: GeneralPreference) {
        val rawJson = json.encodeToString(GeneralPreference.serializer(), preference)
        preferenceStore.set(RConfig.encryptAsync(rawJson))
    }

    suspend fun getPreference(): GeneralPreference {
        val storedValue = preferenceStore.get() ?: return GeneralPreference(0, 0)
        val rawJson = RConfig.decryptAsync(storedValue)
        val preference = json.decodeFromString<GeneralPreference>(rawJson)
        val migratedPreference = migrateAnonymousUsageConsent(preference)
        if (!storedValue.isCurrentConfigCiphertext() || migratedPreference != preference) {
            storePreference(migratedPreference)
        }
        return migratedPreference
    }
}

/** An answer for an older consent version no longer counts; collection stays off until the user answers again. */
internal fun migrateAnonymousUsageConsent(preference: GeneralPreference): GeneralPreference =
    if (preference.needsAnonymousUsageDecision && preference.anonymousUsage) {
        preference.copy(anonymousUsage = false)
    } else {
        preference
    }
