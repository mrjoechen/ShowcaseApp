package com.alpha.showcase.common.ui.settings

import com.alpha.showcase.common.storage.objectStoreOf

class SettingPreferenceRepo {

    private val settingsStore = objectStoreOf<Settings>("settings")
    private val preferenceStore = objectStoreOf<GeneralPreference>("preference")

    suspend fun getSettings(): Settings {
        return settingsStore.get()?: Settings.getDefaultInstance()
    }

    suspend fun updateSettings(settings: Settings) {
        settingsStore.set(settings)
    }


    suspend fun updatePreference(preference: GeneralPreference) {
        preferenceStore.set(preference)
    }

    suspend fun getPreference(): GeneralPreference {
        return preferenceStore.get()?: GeneralPreference(0, 0)
    }


}
