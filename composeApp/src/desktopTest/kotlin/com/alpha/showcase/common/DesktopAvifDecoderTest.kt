package com.alpha.showcase.common

import coil3.BitmapImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.maxBitmapSize
import coil3.size.Precision
import coil3.size.Size
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopAvifDecoderTest {
    @Test fun decodesAvifThroughTheDesktopImageLoader() = runTest {
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { addPlatformComponents() }.build()
        try {
            val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(fixture("two-colors.avif")).build())
            val image = assertIs<SuccessResult>(result, (result as? ErrorResult)?.throwable.toString()).image
            assertEquals(64, image.width)
            assertEquals(48, image.height)
            assertColor(image, 8, 24, RED)
            assertColor(image, 56, 24, BLUE)
        } finally { loader.shutdown() }
    }

    @Test fun decodesAvifEncodedByMacOsImageIo() = runTest {
        val loader = loader()
        try {
            val result = success(loader, "imageio.avif")
            assertEquals(64, result.image.width)
            assertEquals(48, result.image.height)
            assertColor(result.image, 8, 24, RED)
            assertColor(result.image, 56, 24, BLUE)
        } finally { loader.shutdown() }
    }

    @Test fun appliesContainerRotation() = runTest {
        val loader = loader()
        try {
            val result = success(loader, "rotated.avif")
            assertEquals(48, result.image.width)
            assertEquals(64, result.image.height)
            assertColor(result.image, 24, 8, RED)
            assertColor(result.image, 24, 56, BLUE)
        } finally { loader.shutdown() }
    }

    @Test fun handlesAllContainerOrientationsForNonSquareImages() = runTest {
        val loader = loader()
        // Reference corner pixels (TL, TR, BL, BR) decoded independently with libavif.
        val expected = listOf(
            listOf(RED, GREEN, BLUE, YELLOW), listOf(GREEN, RED, YELLOW, BLUE),
            listOf(YELLOW, BLUE, GREEN, RED), listOf(BLUE, YELLOW, RED, GREEN),
            listOf(RED, BLUE, GREEN, YELLOW), listOf(BLUE, RED, YELLOW, GREEN),
            listOf(YELLOW, GREEN, BLUE, RED), listOf(GREEN, YELLOW, RED, BLUE),
        )
        try {
            for (orientation in 1..8) {
                val result = success(loader, "orientation-$orientation.avif")
                val portrait = orientation >= 5
                assertEquals(if (portrait) 64 else 128, result.image.width, "orientation $orientation")
                assertEquals(if (portrait) 128 else 64, result.image.height, "orientation $orientation")
                val width = result.image.width
                val height = result.image.height
                val corners = listOf(8 to 8, width - 9 to 8, 8 to height - 9, width - 9 to height - 9)
                corners.forEachIndexed { index, (x, y) ->
                    assertColor(result.image, x, y, expected[orientation - 1][index], "orientation $orientation, corner $index")
                }
            }
        } finally { loader.shutdown() }
    }

    @Test fun decodesTenBitAlphaAndAnimatedImages() = runTest {
        val loader = loader()
        try {
            val tenBit = success(loader, "ten-bit.avif")
            assertEquals(64, tenBit.image.width)
            assertColor(tenBit.image, 32, 24, RED)

            val alpha = success(loader, "alpha.avif")
            assertColor(alpha.image, 8, 24, RED)
            val transparent = assertIs<BitmapImage>(alpha.image).bitmap.getColor(56, 24)
            assertTrue(transparent ushr 24 < 10, "Expected transparency: ${transparent.toUInt().toString(16)}")

            // An avis sequence shows its first frame.
            val animated = success(loader, "animated.avif")
            assertEquals(64, animated.image.width)
            assertEquals(48, animated.image.height)
            assertColor(animated.image, 56, 24, RED)
        } finally { loader.shutdown() }
    }

    @Test fun samplesAndReusesImmutablePixelsFromMemoryCache() = runTest {
        val loader = loader()
        try {
            val request = ImageRequest.Builder(PlatformContext.INSTANCE).data(fixture("two-colors.avif"))
                .size(32, 24).memoryCacheKey("avif").build()
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

    @Test fun respectsPrecisionAndMaxBitmapSize() = runTest {
        val loader = loader()
        try {
            suspend fun decode(block: ImageRequest.Builder.() -> Unit): SuccessResult {
                val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE)
                    .data(fixture("two-colors.avif")).apply(block).build())
                return assertIs<SuccessResult>(result, (result as? ErrorResult)?.throwable.toString())
            }
            val inexact = decode { size(128, 96); precision(Precision.INEXACT) }
            assertEquals(64, inexact.image.width)
            assertFalse(inexact.isSampled)
            val exact = decode { size(128, 96); precision(Precision.EXACT) }
            assertEquals(128, exact.image.width)
            assertEquals(96, exact.image.height)
            val limited = decode { maxBitmapSize(Size(16, 16)) }
            assertEquals(16, limited.image.width)
            assertEquals(12, limited.image.height)
            assertTrue(limited.isSampled)
        } finally { loader.shutdown() }
    }

    @Test fun sniffingUsesCompatibleBrandsAndDoesNotConsumeTheSource() {
        val source = Buffer().write(fixture("two-colors.avif"))
        val before = source.snapshot()
        assertTrue(DesktopAvifDecoder.isAvif(source))
        assertEquals(before, source.snapshot())
        assertFalse(DesktopHeicDecoder.isHeic(source))
        fun header(brand: String, compatible: String) = Buffer().writeInt(24).writeUtf8("ftyp")
            .writeUtf8(brand).writeInt(0).writeUtf8("mif1").writeUtf8(compatible)
        assertTrue(DesktopAvifDecoder.isAvif(header("mif1", "avif")))
        assertTrue(DesktopAvifDecoder.isAvif(header("avis", "msf1")))
        assertTrue(DesktopAvifDecoder.isAvif(Buffer().write(fixture("animated.avif"))))
        assertFalse(DesktopAvifDecoder.isAvif(header("heic", "heic")))
        assertFalse(DesktopAvifDecoder.isAvif(header("mif1", "mif1")))
        assertFalse(DesktopAvifDecoder.isAvif(Buffer().writeUtf8("GIF89a")))
        assertFalse(DesktopAvifDecoder.isAvif(Buffer().write(fixture("two-colors.avif"), 0, 20)))
        assertFalse(DesktopAvifDecoder.isAvif(Buffer().write(heicFixture())))
    }

    @Test fun malformedAvifReturnsAnErrorAndDoesNotBlockTheNextDecode() = runTest {
        val loader = loader()
        try {
            val valid = fixture("two-colors.avif")
            for (length in listOf(40, valid.size - 20)) { // missing meta, or incomplete mdat
                assertIs<ErrorResult>(loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE)
                    .data(valid.copyOf(length)).build()))
            }
            success(loader, "two-colors.avif")
        } finally { loader.shutdown() }
    }

    private fun loader() = ImageLoader.Builder(PlatformContext.INSTANCE)
        .components { addPlatformComponents() }
        .memoryCache { MemoryCache.Builder().maxSizeBytes(1024 * 1024).build() }.build()

    private fun fixture(name: String) = checkNotNull(javaClass.getResourceAsStream("/avif/$name")).use { it.readBytes() }

    private fun heicFixture() = checkNotNull(javaClass.getResourceAsStream("/heic/two-colors.heic")).use { it.readBytes() }

    private suspend fun success(loader: ImageLoader, name: String): SuccessResult {
        val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(fixture(name)).build())
        return assertIs<SuccessResult>(result, (result as? ErrorResult)?.throwable.toString())
    }

    private fun assertColor(image: coil3.Image, x: Int, y: Int, expected: Int, message: String = "($x, $y)") {
        val actual = assertIs<BitmapImage>(image).bitmap.getColor(x, y)
        assertTrue(actual ushr 24 > 245, "$message: ${actual.toUInt().toString(16)}")
        for (shift in listOf(0, 8, 16)) {
            assertTrue(kotlin.math.abs((actual ushr shift and 255) - (expected ushr shift and 255)) < 25,
                "$message: ${actual.toUInt().toString(16)}")
        }
    }

    private companion object {
        const val RED = 0xff0000
        const val GREEN = 0x00ff00
        const val BLUE = 0x0000ff
        const val YELLOW = 0xffff00
    }
}
