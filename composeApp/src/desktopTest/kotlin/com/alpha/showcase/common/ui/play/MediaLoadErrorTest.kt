package com.alpha.showcase.common.ui.play

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MediaLoadErrorTest {
    @Test fun errorIncludesFileNameWithoutUrlCredentialsOrQuery() {
        val error = mediaLoadError(MediaItemState("https://user:secret@example.com/photos/sunset.jpg?token=secret"), "HTTP 404")
        assertEquals("sunset.jpg\nHTTP 404", error)
        assertFalse(error.contains("secret"))
    }

    @Test fun errorUsesGalleryDisplayName() {
        val state = MediaItemState(DataWithType("phasset://opaque-id", "image/jpeg", mapOf("displayName" to "旅行照片.jpg")))
        assertEquals("旅行照片.jpg\nDecode failed", mediaLoadError(state, "Decode failed"))
    }
}
