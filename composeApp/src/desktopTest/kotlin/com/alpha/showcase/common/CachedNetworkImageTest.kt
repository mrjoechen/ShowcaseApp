package com.alpha.showcase.common

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.request.CachePolicy
import coil3.request.Options
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Ftp
import com.alpha.showcase.common.ui.ext.buildImageRequest
import com.alpha.showcase.common.ui.play.UrlWithAuth
import com.alpha.showcase.common.ui.play.ImagePrefetchWindow
import com.alpha.showcase.common.ui.play.prefetchImages
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toOkioPath

class CachedNetworkImageTest {
    private val file = NetworkFile(Ftp(id = "test", host = "nas", user = "user", passwd = "secret", name = "test"),
        "ftp://nas/photo.jpg", "photo.jpg", false, 4, "image/jpeg", "version1")

    @Test fun shortTransferCannotBecomeADurableCacheEntry() = runTest {
        fixture { loader, options, directory ->
            assertFailsWith<IOException> {
                CachedNetworkImage.fetch(file, options, loader.diskCache, directory) { fs, path -> fs.write(path) { writeUtf8("bad") } }
            }
            assertNull(loader.diskCache!!.openSnapshot(networkImageCacheKey(file)))
        }
    }

    @Test fun visibleRequestSharesAnUnfinishedPrefetchWithoutRestartingTransfer() = runTest {
        fixture { loader, options, directory ->
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val window = MutableStateFlow(ImagePrefetchWindow("previous", true, file))
            var transfers = 0
            val prefetch = launch {
                prefetchImages(window) {
                    CachedNetworkImage.fetch(file, options, loader.diskCache, directory) { fs, path ->
                        transfers++
                        started.complete(Unit)
                        finish.await()
                        fs.write(path) { writeUtf8("data") }
                    }.source.close()
                }
            }
            started.await()
            window.value = ImagePrefetchWindow(file, false, null)
            val visible = async {
                CachedNetworkImage.fetch(file, options, loader.diskCache, directory) { fs, path ->
                    transfers++
                    fs.write(path) { writeUtf8("data") }
                }
            }
            yield()
            finish.complete(Unit)
            visible.await().source.use { assertEquals("data", it.source().readUtf8()) }
            prefetch.cancelAndJoin()
            assertEquals(1, transfers)
        }
    }

    @Test fun concurrentLoadsDownloadOnce() = runTest {
        fixture { loader, options, directory ->
            var transfers = 0
            coroutineScope {
                List(4) { async {
                    CachedNetworkImage.fetch(file, options, loader.diskCache, directory) { fs, path ->
                        transfers++
                        delay(10)
                        fs.write(path) { writeUtf8("data") }
                    }.source.use { assertEquals("data", it.source().readUtf8()) }
                } }.awaitAll()
            }
            assertEquals(1, transfers)
        }
    }

    @Test fun failedDownloadIsNeverPublishedAndRetryWorks() = runTest {
        fixture { loader, options, directory ->
            assertFailsWith<IOException> {
                CachedNetworkImage.fetch(file, options, loader.diskCache, directory) { fs, path ->
                    fs.write(path) { writeUtf8("part") }
                    throw IOException("connection reset")
                }
            }
            assertNull(loader.diskCache!!.openSnapshot(networkImageCacheKey(file)))
            CachedNetworkImage.fetch(file, options, loader.diskCache, directory) { fs, path -> fs.write(path) { writeUtf8("full") } }
                .source.use { assertEquals("full", it.source().readUtf8()) }
        }
    }

    @Test fun cancelledDownloadsReleasePermitsEditorsAndLocks() = runTest {
        fixture { loader, options, directory ->
            repeat(5) {
                val started = CompletableDeferred<Unit>()
                val job = launch {
                    CachedNetworkImage.fetch(file, options, loader.diskCache, directory) { fs, path ->
                        fs.write(path) { writeUtf8("part") }
                        started.complete(Unit)
                        awaitCancellation()
                    }
                }
                started.await()
                job.cancelAndJoin()
            }
            withTimeout(1000) {
                CachedNetworkImage.fetch(file, options, loader.diskCache, directory) { fs, path -> fs.write(path) { writeUtf8("good") } }
                    .source.close()
            }
        }
    }

    @Test fun disabledCacheUsesTemporaryFileAndDeletesItOnClose() = runTest {
        fixture { loader, options, directory ->
            var downloaded: Path? = null
            val result = CachedNetworkImage.fetch(file, options.copy(diskCachePolicy = CachePolicy.DISABLED), loader.diskCache, directory) { fs, path ->
                downloaded = path
                fs.write(path) { writeUtf8("data") }
            }
            assertTrue(FileSystem.SYSTEM.exists(downloaded!!))
            result.source.close()
            assertFalse(FileSystem.SYSTEM.exists(downloaded!!))
            assertNull(loader.diskCache!!.openSnapshot(networkImageCacheKey(file)))
        }
    }

    @Test fun offlineCacheMissDoesNotStartNetworkIO() = runTest {
        fixture { loader, options, directory ->
            assertFailsWith<IOException> {
                CachedNetworkImage.fetch(file, options.copy(networkCachePolicy = CachePolicy.DISABLED), loader.diskCache, directory) { _, _ ->
                    fail("Unexpected network request")
                }
            }
        }
    }

    @Test fun cacheFailuresKeepDownloadedBytesWithoutRepeatingNetworkIO() = runTest {
        for (stage in listOf("read", "edit", "write", "commit", "nullSnapshot")) {
            fixture(cacheDecorator = { delegate ->
                object : DiskCache by delegate {
                    override fun openSnapshot(key: String): DiskCache.Snapshot? {
                        if (stage == "read") throw IOException("cache unavailable")
                        return delegate.openSnapshot(key)
                    }
                    override fun openEditor(key: String): DiskCache.Editor? {
                        if (stage == "edit") throw IOException("cache unavailable")
                        val editor = delegate.openEditor(key) ?: return null
                        return object : DiskCache.Editor by editor {
                            override val metadata: Path get() {
                                if (stage == "write") throw IOException("cache disk full")
                                return editor.metadata
                            }
                            override fun commitAndOpenSnapshot(): DiskCache.Snapshot? {
                                if (stage == "commit") throw IOException("commit failed")
                                if (stage == "nullSnapshot") { editor.abort(); return null }
                                return editor.commitAndOpenSnapshot()
                            }
                        }
                    }
                }
            }) { loader, options, directory ->
                var transfers = 0
                var downloaded: Path? = null
                val result = CachedNetworkImage.fetch(file, options, loader.diskCache, directory) { fs, path ->
                    transfers++
                    downloaded = path
                    fs.write(path) { writeUtf8("data") }
                }
                result.source.use { assertEquals("data", it.source().readUtf8(), stage) }
                assertEquals(1, transfers, stage)
                assertFalse(FileSystem.SYSTEM.exists(downloaded!!), stage)
            }
        }
    }

    @Test fun editedCredentialsAndFileVersionsHaveDifferentKeys() {
        val remote = file.remote as Ftp
        assertNotEquals(networkImageCacheKey(file), networkImageCacheKey(file.copy(remote = remote.copy(passwd = "new"))))
        assertNotEquals(networkImageCacheKey(file), networkImageCacheKey(file.copy(modTime = "version2")))
        assertFalse(networkImageCacheKey(file).contains("secret"))
        val alice = buildImageRequest(PlatformContext.INSTANCE, UrlWithAuth("https://nas/photo.jpg", "Authorization", "alice"))
        val bob = buildImageRequest(PlatformContext.INSTANCE, UrlWithAuth("https://nas/photo.jpg", "Authorization", "bob"))
        assertNotEquals(alice.diskCacheKey, bob.diskCacheKey)
        assertNotEquals(alice.memoryCacheKey, bob.memoryCacheKey)
    }

    private suspend fun fixture(cacheDecorator: (DiskCache) -> DiskCache = { it }, block: suspend (ImageLoader, Options, Path) -> Unit) {
        val directory = Files.createTempDirectory("network-cache-contract").toFile()
        val path = directory.toPath().toOkioPath()
        val cache = DiskCache.Builder().directory(path / "cache").maxSizeBytes(1024 * 1024).build()
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(cacheDecorator(cache)).build()
        try { block(loader, Options(PlatformContext.INSTANCE), path) }
        finally { loader.shutdown(); cache.shutdown(); directory.deleteRecursively() }
    }
}
