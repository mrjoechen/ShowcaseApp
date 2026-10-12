package com.alpha.showcase.common.sync

import com.alpha.showcase.common.networkfile.storage.remote.GallerySource
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
import com.alpha.showcase.common.repo.mergeSyncedSources
import com.alpha.showcase.common.ui.settings.GeneralPreference
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigSyncDomainsTest {

    @Test
    fun deviceOnlySourcesKeepTheirPositions() {
        val local = Local(name = "Camera")
        val gallery = GallerySource(name = "Photos")
        val current = listOf(local, webDav("old"), gallery)

        val merged = mergeSyncedSources(current, listOf(webDav("b"), webDav("a"), webDav("Photos")))

        assertEquals(listOf("Camera", "b", "Photos", "a"), merged.map { it.name })
        assertEquals(listOf<RemoteApi>(local, gallery), merged.filter { it is Local || it is GallerySource })
    }

    @Test
    fun syncedSourcesSurviveJsonRoundTrip() {
        val serializer = PolymorphicSerializer(RemoteApi::class)
        val source = webDav("NAS")

        val element = StorageSourceSerializer.sourceJson.encodeToJsonElement(serializer, source)

        assertEquals(source, StorageSourceSerializer.sourceJson.decodeFromJsonElement(serializer, element))
    }

    @Test
    fun syncedPreferenceFieldsExistAndExcludeDeviceDecisions() {
        val fields = Json { encodeDefaults = true }
            .encodeToJsonElement(GeneralPreference.serializer(), GeneralPreference(0, 0))
            .jsonObject.keys

        assertTrue(fields.containsAll(PreferenceSyncDomain.SYNCED_PREFERENCE_FIELDS), "renamed field: $fields")
        assertTrue(fields.size > PreferenceSyncDomain.SYNCED_PREFERENCE_FIELDS.size)
    }

    private fun webDav(name: String) = WebDav(id = "id-$name", url = "https://$name.example", user = "u", passwd = "p", name = name, addTime = 1)
}
