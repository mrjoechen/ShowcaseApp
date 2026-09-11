package com.alpha.showcase.common.ui.play

import kotlin.test.*

class GalleryImageMetadataTest {
    @Test fun parsesOriginalCameraGpsExposureAndDimensions() {
        val result = galleryImageMetadata(mapOf(
            "PixelWidth" to 4032, "PixelHeight" to 3024, "Orientation" to 6,
            "{TIFF}" to mapOf("Make" to "Apple", "Model" to "iPhone", "Artist" to "Photographer"),
            "{Exif}" to mapOf("LensModel" to "Wide camera", "FNumber" to 1.8, "ExposureTime" to 0.01,
                "ISOSpeedRatings" to listOf(100), "FocalLength" to 4.2,
                "DateTimeOriginal" to "2026:09:11 12:30:00", "OffsetTimeOriginal" to "+08:00"),
            "{GPS}" to mapOf("Latitude" to 31.2, "LatitudeRef" to "S", "Longitude" to 121.5, "LongitudeRef" to "W"),
        ), "IMG_1234.HEIC", 123456)
        assertEquals("IMG_1234.HEIC", result.fileName)
        assertEquals(123456L, result.fileSize)
        assertEquals(PhotoCoordinates(-31.2, -121.5), result.coordinates)
        assertTrue(result.hasDimensions)
        assertContains(result.lines, "Apple iPhone")
        assertContains(result.lines, "Wide camera")
        assertContains(result.lines, "2026-09-11 12:30:00 +08:00")
        assertTrue(result.rows.any { it.kind == MediaMetadataKind.Exposure && "100" in it.text })
        assertContains(result.lines, formatMediaDimensions(3024, 4032))
    }

    @Test fun photoKitFallbackDoesNotInventCameraAndRejectsInvalidGps() {
        val result = galleryImageMetadata(mapOf("{GPS}" to mapOf(
            "Latitude" to 999, "LatitudeRef" to "N", "Longitude" to 999, "LongitudeRef" to "E",
        )), "IMG.PNG", 20, 100, 80, "2026-09-11", PhotoCoordinates(31.0, 121.0))
        assertEquals(PhotoCoordinates(31.0, 121.0), result.coordinates)
        assertContains(result.lines, "2026-09-11")
        assertFalse(result.rows.any { it.kind == MediaMetadataKind.Camera })
        assertNull(galleryImageMetadata(emptyMap<Any, Any>(), null, 0).coordinates)
    }

    @Test fun assetFilenameUsesDisplayNameAndNeverIdentifierSuffix() {
        val uri = "phasset://ABC/L0/001"
        val state = MediaItemState(DataWithType(uri, "image/heic", mapOf("displayName" to "IMG_1234.HEIC")))
        assertEquals("IMG_1234.HEIC", mediaMetadataRows(state).single { it.kind == MediaMetadataKind.FileName }.text)
        val bare = MediaItemState(uri)
        assertTrue(mediaMetadataRows(bare).none { it.kind == MediaMetadataKind.FileName })
        bare.metadata = MediaMetadata(emptyList(), fileName = "original.heic")
        assertEquals("original.heic", mediaMetadataRows(bare).single { it.kind == MediaMetadataKind.FileName }.text)
    }
}
