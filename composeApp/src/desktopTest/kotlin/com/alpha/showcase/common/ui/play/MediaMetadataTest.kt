package com.alpha.showcase.common.ui.play

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.SuccessResult
import com.alpha.showcase.common.ui.ext.buildMediaImageRequest
import de.stefan_oltmann.kim.Kim
import de.stefan_oltmann.kim.model.MetadataUpdate
import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.*

class MediaMetadataTest {
    @Test fun metadataReaderIsClosedForValidEmptyAndFailingInputs() {
        fun read(bytes: ByteArray, fail: Boolean = false): MediaMetadata? {
            var closed = false
            val delegate = MetadataByteReader(Buffer().write(bytes))
            val reader = object : de.stefan_oltmann.kim.input.ByteReader by delegate {
                override fun readByte(): Byte? {
                    check(!fail) { "Read failure" }
                    return delegate.readByte()
                }
                override fun readBytes(count: Int): ByteArray {
                    check(!fail) { "Read failure" }
                    return delegate.readBytes(count)
                }
                override fun close() {
                    closed = true
                    delegate.close()
                }
            }
            val result = readMediaMetadata { reader }
            assertTrue(closed)
            return result
        }
        assertNotNull(read(metadataFixture("Cleanup")))
        assertNull(read(byteArrayOf()))
        assertNull(read(metadataFixture("Read error"), fail = true))
    }

    @Test fun applicationComponentsAndMetadataHandlingAreInstalledTogether() = runTest {
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .mediaMetadataCache(coil3.memory.MemoryCache.Builder().maxSizeBytes(1024 * 1024).build()) {
                add(coil3.key.Keyer<ByteArray> { _, _ -> "application-keyer" })
            }.build()
        try {
            val request = buildMediaImageRequest(PlatformContext.INSTANCE,
                DataWithType(metadataFixture("Application metadata"), "png"))
            val first = assertIs<SuccessResult>(loader.execute(request))
            assertEquals("application-keyer", first.memoryCacheKey?.key)
            assertContains(assertNotNull(first.mediaMetadata).lines, "Application metadata")
            val cached = assertIs<SuccessResult>(loader.execute(request))
            assertEquals(coil3.decode.DataSource.MEMORY_CACHE, cached.dataSource)
            assertContains(assertNotNull(cached.mediaMetadata).lines, "Application metadata")
        } finally { loader.shutdown() }
    }

    @Test fun metadataBelongsToTheCachedPixelsWhenOneSourceHasMultipleCacheEntries() = runTest {
        // A stable source can deliver new bytes while its older size/version remains cached.
        val data = Any()
        var bytes = metadataFixture("Old pixels")
        val fetcher = coil3.fetch.Fetcher.Factory<Any> { _, options, _ ->
            coil3.fetch.Fetcher {
                coil3.fetch.SourceFetchResult(
                    coil3.decode.ImageSource(Buffer().write(bytes), options.fileSystem),
                    "image/png", coil3.decode.DataSource.NETWORK,
                )
            }
        }
        val loader = metadataImageLoader()
        try {
            fun request(key: String) = buildMediaImageRequest(PlatformContext.INSTANCE, data).newBuilder()
                .fetcherFactory(fetcher).memoryCacheKey(key).build()
            val original = assertIs<SuccessResult>(loader.execute(request("old-entry")))
            assertContains(assertNotNull(original.mediaMetadata).lines, "Old pixels")
            bytes = metadataFixture("New pixels")
            val newer = assertIs<SuccessResult>(loader.execute(request("new-entry")))
            assertContains(assertNotNull(newer.mediaMetadata).lines, "New pixels")
            val cached = assertIs<SuccessResult>(loader.execute(request("old-entry")))
            assertEquals(coil3.decode.DataSource.MEMORY_CACHE, cached.dataSource)
            assertSame(original.image, cached.image)
            assertContains(assertNotNull(cached.mediaMetadata).lines, "Old pixels")
        } finally { loader.shutdown() }
    }

    @Test fun coilDecodesTheSameBytesAndRetainsMetadataAcrossMemoryCacheHits() = runTest {
        val bytes = metadataFixture("First photo")
        val data = DataWithType(bytes, "png")
        val loader = metadataImageLoader()
        try {
            // ByteArray has no Coil keyer; production URL/file requests already have stable keys.
            val request = buildMediaImageRequest(PlatformContext.INSTANCE, data).newBuilder()
                .memoryCacheKey("metadata-memory-fixture").build()
            val first = assertIs<SuccessResult>(loader.execute(request))
            assertEquals(80, first.image.width)
            assertEquals(40, first.image.height)
            assertContains(assertNotNull(first.mediaMetadata).lines, "First photo")
            val second = assertIs<SuccessResult>(loader.execute(request))
            assertEquals(coil3.decode.DataSource.MEMORY_CACHE, second.dataSource)
            assertContains(assertNotNull(second.mediaMetadata).lines, "First photo")
        } finally { loader.shutdown() }
    }

    @Test fun invalidMetadataDoesNotConsumeOrBreakTheDecoderSource() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val source = Buffer().write(bytes)
        assertNull(readMediaMetadata { MetadataByteReader(source.peek()) })
        assertContentEquals(bytes, source.readByteArray())
        val reader = MetadataByteReader(Buffer())
        assertFailsWith<IllegalArgumentException> { reader.readBytes(Int.MAX_VALUE) }
        val exactLengthReader = MetadataByteReader(Buffer().write(bytes), bytes.size.toLong())
        assertContentEquals(bytes, exactLengthReader.readBytes(bytes.size))
        assertNull(exactLengthReader.readByte())
    }
}

internal fun metadataFixture(title: String): ByteArray {
    val png = Bitmap().use { bitmap ->
        bitmap.allocN32Pixels(80, 40)
        bitmap.erase(0xffcccccc.toInt())
        org.jetbrains.skia.Image.makeFromBitmap(bitmap).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes }
        }
    }
    return Kim.update(png, MetadataUpdate.Title(title))
}

internal fun metadataImageLoader(): ImageLoader = ImageLoader.Builder(PlatformContext.INSTANCE)
    .mediaMetadataCache(coil3.memory.MemoryCache.Builder().maxSizeBytes(8 * 1024 * 1024).build())
    .build()
