package com.alpha.showcase.common.sync

import com.alpha.showcase.common.networkfile.storage.remote.GallerySource
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
import com.alpha.showcase.common.repo.mergeSyncedSources
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun webDav(name: String) = WebDav(id = "id-$name", url = "https://$name.example", user = "u", passwd = "p", name = name, addTime = 1)
}
