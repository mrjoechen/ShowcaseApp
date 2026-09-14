package com.alpha.showcase.common

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Ftp
import com.alpha.showcase.common.networkfile.util.RConfig
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath

/** Exercises the production FTP reader and Coil fetcher over loopback, without a NAS. */
class NetworkImageLoadingTest {
    @Test
    fun serverClosedIdleConnectionIsReplacedBeforeNextImage() = runBlocking {
        configureTestEncryption()
        LoopbackFtp().use { server ->
            val reader = NetworkFileReader.getInstance()
            try {
                reader.readFile(server.file("first.jpg")).getOrThrow().use { it.readBytes() }
                server.closeControlConnections()
                reader.readFile(server.file("next.jpg")).getOrThrow().use {
                    assertContentEquals(server.payload, it.readBytes())
                }
                assertEquals(2, server.logins.get())
            } finally { reader.cleanup() }
        }
    }

    @Test
    fun literalFilenamesSurviveAndBatchCanExceedConnectionCapacity() = runBlocking {
        configureTestEncryption()
        LoopbackFtp().use { server ->
            val reader = NetworkFileReader.getInstance()
            try {
                val results = reader.readFiles(List(8) { server.file("a%20+b c#1.jpg") })
                results.forEach { result -> result.getOrThrow().use { assertContentEquals(server.payload, it.readBytes()) } }
                assertEquals("RETR /a%20+b c#1.jpg", server.lastRetrieval)
                assertEquals(1, server.logins.get())
                assertEquals(100, reader.getConnectionStats()["semaphore_global"])
            } finally { reader.cleanup() }
        }
    }

    @Test
    fun realCoilDecodeReusesDiskAcrossRequestsAndSupportsOfflinePlayback() = runBlocking {
        configureTestEncryption()
        val bytes = java.io.ByteArrayOutputStream().apply {
            javax.imageio.ImageIO.write(java.awt.image.BufferedImage(96, 64, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", this)
        }.toByteArray()
        LoopbackFtp(bytes).use { server ->
            val directory = Files.createTempDirectory("network-image-decode").toFile()
            val cache = DiskCache.Builder().directory(directory.toPath().toOkioPath()).maxSizeBytes(1024 * 1024).build()
            val loader = ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(cache)
                .components { add(NetworkFileKeyer()); add(NetworkFileFetcher.Factory()) }.build()
            try {
                val request = coil3.request.ImageRequest.Builder(PlatformContext.INSTANCE).data(server.file("photo.jpg"))
                    .memoryCachePolicy(coil3.request.CachePolicy.DISABLED).size(96, 64).build()
                val first = kotlin.test.assertIs<coil3.request.SuccessResult>(loader.execute(request))
                assertEquals(coil3.decode.DataSource.NETWORK, first.dataSource)
                val offline = request.newBuilder().networkCachePolicy(coil3.request.CachePolicy.DISABLED).build()
                val second = kotlin.test.assertIs<coil3.request.SuccessResult>(loader.execute(offline))
                assertEquals(coil3.decode.DataSource.DISK, second.dataSource)
                assertEquals(96, second.image.width)
                assertEquals(64, second.image.height)
                assertEquals(1, server.retrievals.get())
            } finally {
                loader.shutdown(); cache.shutdown(); NetworkFileReader.getInstance().cleanup(); directory.deleteRecursively()
            }
        }
    }

    @Test
    fun secondLoadUsesDiskWithoutDownloadingAgain() = runBlocking {
        configureTestEncryption()
        LoopbackFtp().use { server ->
            val directory = Files.createTempDirectory("network-image-cache").toFile()
            val cache = DiskCache.Builder().directory(directory.toPath().toOkioPath()).maxSizeBytes(32L * 1024 * 1024).build()
            val loader = ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(cache).build()
            try {
                val file = server.file("photo.jpg")
                repeat(2) {
                    val fetcher = NetworkFileFetcher.Factory().create(file, Options(PlatformContext.INSTANCE), loader)!!
                    val result = fetcher.fetch() as SourceFetchResult
                    result.source.use { assertContentEquals(server.payload, it.source().readByteArray()) }
                }
                assertEquals(1, server.retrievals.get(), "A second display must not download the original again")
            } finally {
                loader.shutdown()
                cache.shutdown()
                NetworkFileReader.getInstance().cleanup()
                directory.deleteRecursively()
            }
        }
    }

    @Test
    fun missingFileDoesNotLeakBorrowedConnections() = runBlocking {
        configureTestEncryption()
        LoopbackFtp().use { server ->
            val reader = NetworkFileReader.getInstance()
            try {
                repeat(3) { assertTrue(reader.readFile(server.file("missing.jpg")).isFailure) }
                reader.readFile(server.file("photo.jpg")).getOrThrow().use {
                    assertContentEquals(server.payload, it.readBytes())
                }
                assertEquals(1, server.logins.get(), "550 replies must return a usable connection to the pool")
                assertEquals(15, reader.getConnectionStats()["semaphore_ftp"])
            } finally {
                reader.cleanup()
            }
        }
    }

    private fun configureTestEncryption() {
        RConfig.initEnCryptAndDecrypt({ it }, { it }, { it }, { it })
    }
}

private class LoopbackFtp(val payload: ByteArray = ByteArray(128 * 1024) { (it % 251).toByte() }) : AutoCloseable {
    @Volatile var lastRetrieval: String? = null
    val retrievals = AtomicInteger()
    val logins = AtomicInteger()
    private val listener = ServerSocket(0, 16, java.net.InetAddress.getLoopbackAddress())
    private val sockets = CopyOnWriteArrayList<Socket>()
    private val remote = Ftp(id = "loopback-${listener.localPort}", host = "127.0.0.1", port = listener.localPort,
        user = "test", passwd = "test", name = "test")
    init {
        thread(isDaemon = true, name = "ftp-test-accept") {
            while (!listener.isClosed) {
                val socket = try { listener.accept() } catch (_: Exception) { break }
                sockets += socket
                thread(isDaemon = true, name = "ftp-test-session") { serve(socket) }
            }
        }
    }

    fun file(name: String) = NetworkFile(remote, "ftp://127.0.0.1:${listener.localPort}/$name",
        name, false, payload.size.toLong(), "image/jpeg", "2026-09-14")

    private fun serve(socket: Socket) {
        var passive: ServerSocket? = null
        try {
            socket.soTimeout = 5_000
            val input = socket.getInputStream().bufferedReader()
            val output = socket.getOutputStream().bufferedWriter()
            fun reply(value: String) { output.write("$value\r\n"); output.flush() }
            reply("220 Test FTP")
            while (true) {
                val command = input.readLine() ?: break
                when (command.substringBefore(' ').uppercase()) {
                    "USER" -> reply("331 Password required")
                    "PASS" -> { logins.incrementAndGet(); reply("230 Logged in") }
                    "TYPE", "OPTS", "NOOP" -> reply("200 OK")
                    "PASV" -> {
                        passive?.close()
                        passive = ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).apply { soTimeout = 5_000 }
                        val port = passive.localPort
                        reply("227 Entering Passive Mode (127,0,0,1,${port / 256},${port % 256})")
                    }
                    "RETR" -> {
                        lastRetrieval = command
                        if (command.endsWith("missing.jpg")) {
                            reply("550 Not found")
                        } else {
                            retrievals.incrementAndGet()
                            reply("150 Opening data connection")
                            passive!!.accept().use { it.getOutputStream().write(payload) }
                            reply("226 Transfer complete")
                        }
                        passive?.close()
                        passive = null
                    }
                    "QUIT" -> { reply("221 Goodbye"); break }
                    else -> reply("502 Unsupported")
                }
            }
        } catch (_: Exception) {
            // Closing the fixture also closes any outstanding control/data connection.
        } finally {
            passive?.close()
            socket.close()
        }
    }

    override fun close() {
        listener.close()
        closeControlConnections()
    }

    fun closeControlConnections() {
        sockets.forEach { it.close() }
    }
}
