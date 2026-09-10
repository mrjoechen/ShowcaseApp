package com.alpha.showcase.common

import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class WebHeicDecoderTest {
    @Test fun decodesAndSamplesThroughCoilAndReusesMemoryCache() = runTest {
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .memoryCache { MemoryCache.Builder().maxSizeBytes(1024 * 1024).build() }
            .components { addPlatformComponents() }.build()
        try {
            val request = ImageRequest.Builder(PlatformContext.INSTANCE)
                .data(requireNotNull(FIXTURE.decodeBase64()).toByteArray())
                .size(32, 24).memoryCacheKey("web-heic-test").build()
            val result = loader.execute(request)
            val first = assertIs<SuccessResult>(result, (result as? ErrorResult)?.throwable.toString())
            assertEquals(32, first.image.width)
            assertEquals(24, first.image.height)
            assertTrue(first.isSampled)
            assertSame(first.image, assertIs<SuccessResult>(loader.execute(request)).image)
        } finally { loader.shutdown() }
    }

    @Test fun recognizesCompatibleBrandWithoutConsumingInput() {
        val input = Buffer().writeInt(24).writeUtf8("ftypmif1").writeInt(0).writeUtf8("mif1heic")
        val original = input.snapshot()
        assertTrue(WebHeicDecoder.isHeic(input))
        assertEquals(original, input.snapshot())
    }

    @Test fun leavesOtherFormatsAndMalformedHeadersToOtherDecoders() {
        for (brand in listOf("avif", "mif1", "jpeg")) {
            assertFalse(WebHeicDecoder.isHeic(Buffer().writeInt(16).writeUtf8("ftyp$brand").writeInt(0)))
        }
        assertFalse(WebHeicDecoder.isHeic(Buffer().writeUtf8("GIF89a")))
        assertFalse(WebHeicDecoder.isHeic(Buffer().writeInt(64).writeUtf8("ftypheic").writeInt(0)))
    }

    private companion object {
        // Same generated 559-byte fixture as desktopTest/resources/heic/two-colors.heic.
        const val FIXTURE = "AAAAHGZ0eXBoZWljAAAAAG1pZjFoZWljbWlhZgAAAX1tZXRhAAAAAAAAACFoZGxyAAAAAAAAAABwaWN0AAAAAAAAAAAAAAAAAAAAACJpbG9jAAAAAERAAAEAAQAAAAABoQABAAAAAAAAAI4AAAAjaWluZgAAAAAAAQAAABVpbmZlAgAAAAABAABodmMxAAAAAA5waXRtAAAAAAABAAAA/WlwcnAAAADdaXBjbwAAAHZodmNDAQNwAAAAAAAAAAAAHvAA/P34+AAADwNgAAEAGEABDAH//wNwAAADAJAAAAMAAAMAHroCQGEAAQAqQgEBA3AAAAMAkAAAAwAAAwAeoCCBBZbq5Ka5uAhoMCAAAAMDIAAAAwAhYgABAAZEAcFzwIkAAAATY29scm5jbHgAAQANAAaAAAAAFGlzcGUAAAAAAAAAQAAAAEAAAAAoY2xhcAAAAEAAAAABAAAAMAAAAAEAAAAAAAAAAv////AAAAACAAAAEHBpeGkAAAAAAwgICAAAABhpcG1hAAAAAAAAAAEAAQWBAgMFhAAAAJZtZGF0AAAAiigBrwY4iS58L/ajX///stn9l/kw5KaZf4+hXI/5ViP//4zGpv/ATs0T9bJQ1ZyXuwR0xhlteo5eLFSIzQB/A9kJdqtANlHYrs2PVlrKD+BExrceEWBQc6h2fsAGDTLg5qUV1HDk5xqStZ1z8JLDjk270qmXo9qTvp6BzVf396AplRBiSls9IaERwA=="
    }
}
