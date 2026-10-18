package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.utils.getMimeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayItemDataTest {
    @Test fun avifIsRecognizedAsAnImage() {
        assertTrue("https://example.com/photos/sunset.AVIF".isImage())
        assertTrue(DataWithType("sunset", "avif").isImage())
        assertTrue(DataWithType("sunset", "image/avif").isImage())
        assertFalse(DataWithType("sunset", "avif").isVideo())
        assertEquals("image/avif", getMimeType("sunset.avif"))
    }
}
