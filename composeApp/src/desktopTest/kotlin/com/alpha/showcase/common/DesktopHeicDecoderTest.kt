package com.alpha.showcase.common

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.BitmapImage
import coil3.decode.DataSource
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertSame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DesktopHeicDecoderTest {
    @Test fun decodesHeicThroughTheDesktopImageLoader() = runTest {
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { addPlatformComponents() }.build()
        try {
            val bytes = checkNotNull(javaClass.getResourceAsStream("/heic/two-colors.heic")).use { it.readBytes() }
            val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(bytes).build())
            val image = assertIs<SuccessResult>(result, (result as? ErrorResult)?.throwable.toString()).image
            assertEquals(64, image.width)
            assertEquals(48, image.height)
            assertColor(image, 8, 24, red = true)
            assertColor(image, 56, 24, red = false)
        } finally { loader.shutdown() }
    }

    @Test fun appliesContainerRotationAndCleanAperture() = runTest {
        val loader = loader()
        try {
            val result = success(loader, "rotated.heic")
            assertEquals(48, result.image.width)
            assertEquals(64, result.image.height)
            assertColor(result.image, 24, 8, red = true)
            assertColor(result.image, 24, 56, red = false)
        } finally { loader.shutdown() }
    }

    @Test fun decodesTenBitSdr() = runTest {
        val loader = loader()
        try {
            val result = success(loader, "ten-bit.heic")
            assertEquals(64, result.image.width)
            assertEquals(48, result.image.height)
            assertColor(result.image, 32, 24, red = true)
        } finally { loader.shutdown() }
    }

    @Test fun handlesAllContainerOrientationsForNonSquareImages() = runTest {
        val loader = loader()
        val red = 0xff0000
        val green = 0x00ff00
        val blue = 0x0000ff
        val yellow = 0xffff00
        // Reference corner pixels (TL, TR, BL, BR) decoded independently with libheif.
        val expected = listOf(
            listOf(red, green, blue, yellow), listOf(green, red, yellow, blue),
            listOf(yellow, blue, green, red), listOf(blue, yellow, red, green),
            listOf(red, blue, green, yellow), listOf(blue, red, yellow, green),
            listOf(yellow, green, blue, red), listOf(green, yellow, red, blue),
        )
        try {
            for (orientation in 1..8) {
                val result = success(loader, "orientation-$orientation.heic")
                val portrait = orientation >= 5
                assertEquals(if (portrait) 64 else 128, result.image.width, "orientation $orientation")
                assertEquals(if (portrait) 128 else 64, result.image.height, "orientation $orientation")
                val bitmap = assertIs<BitmapImage>(result.image).bitmap
                val corners = listOf(8 to 8, bitmap.width - 9 to 8,
                    8 to bitmap.height - 9, bitmap.width - 9 to bitmap.height - 9)
                corners.forEachIndexed { index, (x, y) ->
                    val actual = bitmap.getColor(x, y)
                    val reference = expected[orientation - 1][index]
                    for (shift in listOf(0, 8, 16)) {
                        assertTrue(kotlin.math.abs((actual ushr shift and 255) - (reference ushr shift and 255)) < 25,
                            "orientation $orientation, corner $index: ${actual.toUInt().toString(16)}")
                    }
                }
            }
        } finally { loader.shutdown() }
    }

    @Test fun samplesAndReusesImmutablePixelsFromMemoryCache() = runTest {
        val loader = loader()
        try {
            val request = ImageRequest.Builder(PlatformContext.INSTANCE).data(fixture("two-colors.heic"))
                .size(32, 24).memoryCacheKey("heic").build()
            val first = assertIs<SuccessResult>(loader.execute(request))
            assertEquals(32, first.image.width)
            assertEquals(24, first.image.height)
            assertTrue(first.isSampled)
            assertTrue(assertIs<BitmapImage>(first.image).bitmap.isImmutable)
            val cached = assertIs<SuccessResult>(loader.execute(request))
            assertEquals(DataSource.MEMORY_CACHE, cached.dataSource)
            assertSame(first.image, cached.image)
        } finally { loader.shutdown() }
    }

    @Test fun sniffingUsesCompatibleBrandsAndDoesNotConsumeTheSource() {
        val source = Buffer().write(fixture("two-colors.heic"))
        val before = source.snapshot()
        assertTrue(DesktopHeicDecoder.isHeic(source))
        assertEquals(before, source.snapshot())
        fun header(brand: String, compatible: String) = Buffer().writeInt(24).writeUtf8("ftyp")
            .writeUtf8(brand).writeInt(0).writeUtf8("mif1").writeUtf8(compatible)
        assertTrue(DesktopHeicDecoder.isHeic(header("mif1", "heic")))
        assertFalse(DesktopHeicDecoder.isHeic(header("avif", "avif")))
        assertFalse(DesktopHeicDecoder.isHeic(header("mif1", "mif1")))
        assertFalse(DesktopHeicDecoder.isHeic(Buffer().writeUtf8("GIF89a")))
        assertFalse(DesktopHeicDecoder.isHeic(Buffer().write(fixture("two-colors.heic"), 0, 20)))
    }

    @Test fun malformedHeicReturnsAnErrorAndDoesNotBlockTheNextDecode() = runTest {
        val loader = loader()
        try {
            val valid = fixture("two-colors.heic")
            for (length in listOf(28, valid.size - 20)) { // missing meta, or incomplete mdat
                assertIs<ErrorResult>(loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE)
                    .data(valid.copyOf(length)).build()))
            }
            success(loader, "two-colors.heic")
        } finally { loader.shutdown() }
    }

    private fun loader() = ImageLoader.Builder(PlatformContext.INSTANCE)
        .components { addPlatformComponents() }
        .memoryCache { MemoryCache.Builder().maxSizeBytes(1024 * 1024).build() }.build()

    private fun fixture(name: String) = checkNotNull(javaClass.getResourceAsStream("/heic/$name")).use { it.readBytes() }

    private suspend fun success(loader: ImageLoader, name: String): SuccessResult {
        val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(fixture(name)).build())
        return assertIs<SuccessResult>(result, (result as? ErrorResult)?.throwable.toString())
    }

    private fun assertColor(image: coil3.Image, x: Int, y: Int, red: Boolean) {
        val color = assertIs<BitmapImage>(image).bitmap.getColor(x, y)
        val r = color ushr 16 and 255
        val g = color ushr 8 and 255
        val b = color and 255
        assertTrue(g < 40 && if (red) r > 210 && b < 40 else b > 210 && r < 40,
            "Unexpected pixel ${color.toUInt().toString(16)} at ($x, $y)")
    }
}
