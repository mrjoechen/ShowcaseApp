package com.alpha.showcase.common.ui.source

import com.alpha.showcase.common.cache.GalleryMediaInput
import com.alpha.showcase.common.cache.GalleryMediaRecord
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GalleryAssetReferenceTest {
    @Test
    fun identifierRoundTripsWithoutTreatingItAsAFilePath() {
        val id = "ABC-123/L0/001"
        val uri = galleryAssetUri(id)
        assertEquals(id, galleryAssetIdentifier(uri))
        assertEquals(uri, toGalleryDisplayUri(uri))
        assertNull(galleryAssetIdentifier("gallery://old/photo.jpg"))
        assertNull(galleryAssetIdentifier("phasset://"))
    }

    @Test
    fun filtersDeletedAndRevokedAssetsPreservingOrderAndLegacyFiles() = runTest {
        val records = listOf(record(galleryAssetUri("allowed")), record("gallery://old/a.jpg"),
            record(galleryAssetUri("deleted")), record(galleryAssetUri("revoked")))
        var requested = emptyList<String>()
        val result = filterAccessibleGalleryMedia(records) { ids ->
            requested = ids
            setOf("allowed")
        }
        assertEquals(listOf("allowed", "deleted", "revoked"), requested)
        assertEquals(records.take(2), result)
        // Filtering is read-only: a subsequent permission restoration makes the saved record usable.
        assertEquals(records, filterAccessibleGalleryMedia(records) { it.toSet() })
    }

    @Test
    fun deniedAccessFiltersAllAssetReferencesButRetainsLegacyFiles() = runTest {
        val legacy = record("gallery://old/a.jpg")
        assertEquals(listOf(legacy), filterAccessibleGalleryMedia(
            listOf(record(galleryAssetUri("photo")), legacy),
        ) { emptySet() })
    }

    @Test
    fun nativeSelectionUsesMetadataDirectlyWithoutOpeningAFile() = runTest {
        val metadata = GalleryMediaInput(galleryAssetUri("photo/L0/001"), "photo.heic", "image/heic")
        assertEquals(metadata, GalleryPickedMedia.Asset(metadata).toGalleryMediaInput("source"))
    }

    private fun record(uri: String) = GalleryMediaRecord("source", uri, "photo", "image/jpeg", 0)
}
