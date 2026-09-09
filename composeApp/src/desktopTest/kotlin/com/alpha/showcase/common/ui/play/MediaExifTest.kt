package com.alpha.showcase.common.ui.play

import coil3.PlatformContext
import coil3.request.SuccessResult
import com.alpha.showcase.common.ui.ext.buildMediaImageRequest
import de.stefan_oltmann.kim.common.RationalNumber
import de.stefan_oltmann.kim.common.RationalNumbers
import de.stefan_oltmann.kim.format.jpeg.JpegRewriter
import de.stefan_oltmann.kim.format.tiff.constant.ExifTag
import de.stefan_oltmann.kim.format.tiff.constant.TiffTag
import de.stefan_oltmann.kim.format.tiff.write.TiffOutputSet
import de.stefan_oltmann.kim.input.ByteArrayByteReader
import de.stefan_oltmann.kim.model.GpsCoordinates
import de.stefan_oltmann.kim.output.ByteArrayByteWriter
import kotlinx.coroutines.test.runTest
import okio.buffer
import okio.source
import okio.Path.Companion.toPath
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.*

class MediaExifTest {
    @Test fun exifIsReadBeforeDecoderFactoryRepositionsSharedFileDescriptor() = runTest {
        val bytes = exifFixture()
        val file = java.nio.file.Files.createTempFile("album-exif", ".jpg").toFile()
        file.writeBytes(bytes)
        val loader = metadataImageLoader()
        try {
            java.io.FileInputStream(file).use { input ->
                val source = input.source().buffer()
                // Format sniffing buffers bytes before Android's StaticImageDecoder
                // factory seeks the content URI's shared descriptor back to its start.
                source.require(32)
                val fetcher = coil3.fetch.Fetcher.Factory<coil3.Uri> { _, options, _ ->
                    coil3.fetch.Fetcher {
                        coil3.fetch.SourceFetchResult(coil3.decode.ImageSource(source, options.fileSystem),
                            "image/jpeg", coil3.decode.DataSource.DISK)
                    }
                }
                val factory = coil3.decode.Decoder.Factory { result, options, imageLoader ->
                    input.channel.position(0)
                    // Like ImageDecoder, decode the file independently of Okio's buffer.
                    val pixelSource = coil3.decode.ImageSource(file.absolutePath.toPath(), options.fileSystem)
                    val decoder = imageLoader.components.newDecoder(
                        coil3.fetch.SourceFetchResult(pixelSource, result.mimeType, result.dataSource), options, imageLoader
                    )!!.first
                    object : coil3.decode.Decoder {
                        override suspend fun decode() = try { decoder.decode() } finally { pixelSource.close() }
                    }
                }
                val request = buildMediaImageRequest(PlatformContext.INSTANCE, "content://album/photo")
                    .newBuilder().fetcherFactory(fetcher).decoderFactory(factory).build()
                val result = assertIs<SuccessResult>(loader.execute(request))
                assertContains(assertNotNull(result.mediaMetadata).lines.joinToString("\n"), "RF 50mm")
            }
        } finally {
            loader.shutdown()
            file.delete()
        }
    }

    @Test fun jpegExifIsAvailableFromAnUnknownLengthAlbumStream() {
        val source = okio.Buffer().write(exifFixture())
        val metadata = assertNotNull(readMediaMetadata { MetadataByteReader(source.peek()) })
        assertContains(metadata.lines.joinToString("\n"), "EOS R5")
    }

    @Test fun actualJpegExifSurvivesDecodingAndMemoryCache() = runTest {
        val bytes = exifFixture()
        val loader = metadataImageLoader()
        try {
            val request = buildMediaImageRequest(PlatformContext.INSTANCE, DataWithType(bytes, "jpg"))
                .newBuilder().memoryCacheKey("exif-fixture").build()
            repeat(2) { index ->
                val result = assertIs<SuccessResult>(loader.execute(request))
                if (index == 1) assertEquals(coil3.decode.DataSource.MEMORY_CACHE, result.dataSource)
                val metadata = assertNotNull(result.mediaMetadata)
                val text = metadata.lines.joinToString("\n")
                listOf("2026-09-08 10:20:30 +08:00", "Canon", "EOS R5", "RF 50mm", "2.8", "1/250", "400", "50", "GPS", "35mm: 50 mm").forEach {
                    assertContains(text, it)
                }
                val state = MediaItemState(DataWithType(bytes, "jpg")).apply { loaded(result.image, metadata) }
                assertContains(mediaMetadataLines(state), formatMediaFileSize(bytes.size.toLong()))
            }
        } finally { loader.shutdown() }
    }

    @Test fun sourceNamesNeverExposeAuthenticationAndPixelCountUsesOriginalSize() {
        val state = MediaItemState(UrlWithAuth("https://user:password@example.com/photos/photo.jpg?secret=token", "Authorization", "secret"))
        assertEquals(listOf("photo.jpg"), mediaMetadataLines(state))
        assertEquals("4032 × 3024 · 12.2 MP", formatMediaDimensions(4032, 3024))
        assertEquals("3.5 MB", formatMediaFileSize(3_670_016))
    }
}

internal fun exifFixture(): ByteArray {
    val jpeg = Bitmap().use { bitmap ->
        bitmap.allocN32Pixels(800, 400)
        bitmap.erase(0xffcccccc.toInt())
        org.jetbrains.skia.Image.makeFromBitmap(bitmap).use { image ->
            image.encodeToData(EncodedImageFormat.JPEG)!!.use { it.bytes }
        }
    }
    val exif = TiffOutputSet().apply {
        getOrCreateRootDirectory().apply {
            add(TiffTag.TIFF_TAG_MAKE, "Canon")
            add(TiffTag.TIFF_TAG_MODEL, "EOS R5")
            add(TiffTag.TIFF_TAG_COPYRIGHT, "Fixture copyright ".repeat(1000))
        }
        getOrCreateExifDirectory().apply {
            add(ExifTag.EXIF_TAG_DATE_TIME_ORIGINAL, "2026:09:08 10:20:30")
            add(ExifTag.EXIF_TAG_OFFSET_TIME_ORIGINAL, "+08:00")
            add(ExifTag.EXIF_TAG_LENS_MODEL, "RF 50mm F1.8 STM")
            add(ExifTag.EXIF_TAG_FNUMBER, RationalNumbers(arrayOf(RationalNumber(28, 10))))
            add(ExifTag.EXIF_TAG_EXPOSURE_TIME, RationalNumbers(arrayOf(RationalNumber(1, 250))))
            add(ExifTag.EXIF_TAG_ISO, shortArrayOf(400))
            add(ExifTag.EXIF_TAG_FOCAL_LENGTH, RationalNumbers(arrayOf(RationalNumber(50, 1))))
            add(ExifTag.EXIF_TAG_FOCAL_LENGTH_IN_35MM_FORMAT, 50.toShort())
        }
        setGpsCoordinates(GpsCoordinates(31.2304, 121.4737))
    }
    val writer = ByteArrayByteWriter()
    JpegRewriter.updateExifMetadata(ByteArrayByteReader(jpeg), writer, exif)
    return writer.toByteArray()
}
