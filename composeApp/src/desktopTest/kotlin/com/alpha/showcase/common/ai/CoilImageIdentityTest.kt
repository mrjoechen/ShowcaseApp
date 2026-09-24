package com.alpha.showcase.common.ai

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.DecodeResult
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import coil3.disk.DiskCache
import coil3.size.Size
import com.alpha.showcase.common.ui.play.*
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.FileSystem
import okio.ByteString.Companion.toByteString
import okio.Path.Companion.toOkioPath
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.*

class CoilImageIdentityTest {
    @Test fun sameFileAcrossLocalPathsHttpAndMemoryHasTheSameFullByteIdentity() = runBlocking {
        val bytes = png(Color.BLUE)
        val expected = ImageContentIdentity("sha256-file-v1:${bytes.toByteString().sha256().hex()}", bytes.size.toLong())
        val directory = Files.createTempDirectory("identity-").toFile()
        val first = java.io.File(directory, "one.png").apply { writeBytes(bytes) }
        val second = java.io.File(directory, "other.png").apply { writeBytes(bytes) }
        var requests = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/photo.png") { exchange ->
                requests++
                exchange.responseHeaders.add("Content-Type", "image/png")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).mediaMetadataCache(
            MemoryCache.Builder().maxSizeBytes(16 * 1024 * 1024).build()).build()
        try {
            val url = "http://127.0.0.1:${server.address.port}/photo.png"
            suspend fun load(model: Any) = assertIs<SuccessResult>(loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE)
                .data(model).withMediaMetadata().size(Size.ORIGINAL).build()))
            for (model in listOf(first.path, second.path, url)) assertEquals(expected, load(model).mediaMetadata?.imageIdentity)
            val cached = load(url)
            assertEquals(DataSource.MEMORY_CACHE, cached.dataSource)
            assertEquals(expected, cached.mediaMetadata?.imageIdentity)
            assertEquals(1, requests) // Hashing does not download the source again.
            loader.memoryCache!!.clear()
            first.writeBytes(png(Color.RED))
            assertNotEquals(expected, load(first.path).mediaMetadata?.imageIdentity)
        } finally { loader.shutdown(); server.stop(0); directory.deleteRecursively() }
    }

    @Test fun decoderReadsTheSameSnapshotEvenWhenOriginalPathChanges() = runBlocking {
        val directory = Files.createTempDirectory("snapshot-").toFile()
        val file = java.io.File(directory, "original.png").apply { writeBytes(png(Color.BLUE)) }
        val original = file.readBytes()
        val result = SourceFetchResult(ImageSource(file.toPath().toOkioPath(), FileSystem.SYSTEM), "image/png", DataSource.DISK)
        try {
            val decoded = decodeWithImageIdentity(result, Options(PlatformContext.INSTANCE)) { snapshot ->
                file.writeBytes(png(Color.RED))
                assertContentEquals(original, snapshot.source.source().readByteArray())
                DecodeResult(Bitmap().apply { allocN32Pixels(1, 1) }.asImage(), false)
            }
            assertEquals("sha256-file-v1:${original.toByteString().sha256().hex()}", decoded.identity?.contentId)
        } finally { result.source.close(); directory.deleteRecursively() }
    }

    @Test fun diskCacheRetainsFullFileIdentityAndRejectsPartialHttpResponses(): Unit = runBlocking {
        val bytes = png(Color.BLUE)
        val directory = Files.createTempDirectory("identity-cache-").toFile()
        var requests = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/image.png") { exchange ->
                requests++
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            createContext("/partial.png") { exchange ->
                exchange.responseHeaders.add("Content-Range", "bytes 0-${bytes.size - 1}/${bytes.size + 10}")
                exchange.sendResponseHeaders(206, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).mediaMetadataCache(
            MemoryCache.Builder().maxSizeBytes(1024 * 1024).build())
            .diskCache(DiskCache.Builder().directory(directory.toPath().toOkioPath()).maxSizeBytes(4 * 1024 * 1024).build()).build()
        fun request(path: String) = ImageRequest.Builder(PlatformContext.INSTANCE)
            .data("http://127.0.0.1:${server.address.port}/$path").size(Size.ORIGINAL).withMediaMetadata().build()
        try {
            val first = assertIs<SuccessResult>(loader.execute(request("image.png")))
            loader.memoryCache!!.clear()
            val second = assertIs<SuccessResult>(loader.execute(request("image.png")))
            assertEquals(DataSource.DISK, second.dataSource)
            assertEquals(first.mediaMetadata?.imageIdentity, second.mediaMetadata?.imageIdentity)
            assertNotNull(second.mediaMetadata?.imageIdentity)
            assertEquals(1, requests)
            assertIs<ErrorResult>(loader.execute(request("partial.png")))
        } finally { loader.shutdown(); server.stop(0); directory.deleteRecursively() }
    }

    @Test fun galleryConversionCarriesOriginalFileHashInsteadOfConvertedPngHash() = runBlocking {
        val original = testImageIdentity("complete-heic-file")
        val result = SourceFetchResult(ImageSource(Buffer().write(png(Color.BLUE)), FileSystem.SYSTEM,
            metadata = MediaSourceMetadata(null, original)), "image/png", DataSource.MEMORY)
        try {
            val decoded = decodeWithImageIdentity(result, Options(PlatformContext.INSTANCE)) { snapshot ->
                snapshot.source.source().readByteArray()
                DecodeResult(Bitmap().apply { allocN32Pixels(1, 1) }.asImage(), false)
            }
            assertEquals(original, decoded.identity)
        } finally { result.source.close() }
    }

    @Test fun fullFileHashIncludesTrailingBytesAndRejectsTruncatedLength(): Unit = runBlocking {
        val bytes = png(Color.BLUE)
        val plain = hashImageFile(Buffer().write(bytes), bytes.size.toLong())
        val changed = hashImageFile(Buffer().write(bytes).writeUtf8("metadata"))
        assertNotEquals(plain.contentId, changed.contentId)
        assertFailsWith<IllegalArgumentException> { hashImageFile(Buffer().write(bytes), bytes.size.toLong() + 1) }
    }

    private fun png(color: Int): ByteArray = Bitmap().use { bitmap ->
        bitmap.allocN32Pixels(32, 24); bitmap.erase(color)
        org.jetbrains.skia.Image.makeFromBitmap(bitmap).use { it.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes } }
    }
}
