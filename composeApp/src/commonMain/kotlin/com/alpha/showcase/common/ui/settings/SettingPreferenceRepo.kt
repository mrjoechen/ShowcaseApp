package com.alpha.showcase.common.ui.settings

import com.alpha.showcase.common.data.Settings
import com.alpha.showcase.common.storage.objectStoreOf
import kotlinx.serialization.json.Json

class SettingPreferenceRepo {

    private val store = objectStoreOf<String>("settings")

    suspend fun getSettings(): Settings {
        return store.get()?.let {
            Json.decodeFromString(Settings.serializer(), it)
        } ?: Settings.getDefaultInstance()
    }

    suspend fun updateSettings(update: (Settings) -> Settings): Settings {
        val value = update(getSettings())
        store.set(
            Json.encodeToString(Settings.serializer(), value)
        )
        return value
    }


}
