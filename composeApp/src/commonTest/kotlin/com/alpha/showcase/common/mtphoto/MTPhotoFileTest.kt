package com.alpha.showcase.common.mtphoto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MTPhotoFileTest {

    @Test
    fun cacheKeyUsesStableFileIdentityAndNoAuthMaterial() {
        val file = MTPhotoFile(
            sourceKey = "source-a1b2",
            albumId = 17,
            fileId = 23,
            md5 = "abc123",
            mimeType = "image/jpeg",
            width = 1600,
            height = 900,
        )

        assertEquals("mtphoto://source-a1b2/17/23/abc123", file.cacheKey)
        assertFalse(file.cacheKey.contains("auth"))
        assertTrue(file.isImage)
        assertFalse(file.isVideo)
    }

    @Test
    fun videoMimeTypeIsPreserved() {
        val file = MTPhotoFile(
            sourceKey = "source-a1b2",
            albumId = 17,
            fileId = 24,
            md5 = "def456",
            mimeType = "video/mp4",
            width = 1920,
            height = 1080,
            duration = 12.5f,
        )

        assertTrue(file.isVideo)
        assertFalse(file.isImage)
    }
}
