package com.alpha.ai.imagegeneration

import com.alpha.ai.imagegeneration.internal.http.readImageSource
import com.alpha.ai.imagegeneration.internal.http.ImageGenerationTransportException
import kotlinx.coroutines.test.runTest
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PortableImageSourceTest {
    @Test
    fun byteSourceSnapshotsInputAndCanBeReadAgain() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val source = ByteArrayImageSource(bytes, "image/png", "photo.png")
        bytes[0] = 9
        repeat(2) {
            assertContentEquals(byteArrayOf(1, 2, 3), source.openSource().buffer().use { it.readByteArray() })
        }
        assertEquals(3L, source.contentLength)
    }

    @Test
    fun sourceCannotExceedProviderLimit() = runTest {
        val source = ByteArrayImageSource(byteArrayOf(1, 2, 3), "image/png")
        assertFailsWith<ImageGenerationTransportException> {
            readImageSource(source, setOf("image/png"), 2)
        }
    }
}
