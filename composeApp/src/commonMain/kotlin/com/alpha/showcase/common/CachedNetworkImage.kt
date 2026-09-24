package com.alpha.showcase.common

import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.alpha.showcase.common.networkfile.model.NetworkFile
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.buffer
import okio.use
import randomUUID

/** Owns downloaded files until either a cache snapshot or the decoder takes ownership. */
internal object CachedNetworkImage {
    private val downloads = Semaphore(3)
    private val locksMutex = Mutex()
    private class Entry(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val locks = mutableMapOf<String, Entry>()

    suspend fun fetch(file: NetworkFile, options: Options, cache: DiskCache?, temporaryDirectory: Path,
        download: suspend (FileSystem, Path) -> Unit): SourceFetchResult {
        val key = options.diskCacheKey ?: networkImageCacheKey(file)
        val entry = locksMutex.withLock { locks.getOrPut(key) { Entry() }.also { it.users++ } }
        try {
            return entry.mutex.withLock {
                if (options.diskCachePolicy.readEnabled && cache != null) {
                    cacheIO { cache.openSnapshot(key) }?.let {
                        if (file.size <= 0 || cache.fileSystem.metadata(it.data).size == file.size) {
                            return@withLock result(file, cache, it, key, DataSource.DISK)
                        }
                        it.close()
                        cacheIO { cache.remove(key) }
                    }
                }
                if (!options.networkCachePolicy.readEnabled) throw IOException("Network image is not cached")
                downloads.withPermit {
                    // Keep an independent file: a failed cache write/commit must not lose a
                    // completed transfer or require the NAS to send the same image again.
                    val fs = options.fileSystem
                    val path = temporaryDirectory / "showcase-image-${randomUUID()}.tmp"
                    var decoderOwnsFile = false
                    try {
                        download(fs, path)
                        currentCoroutineContext().ensureActive()
                        if (file.size > 0 && fs.metadata(path).size != file.size) throw IOException("Incomplete network image file")
                        if (options.diskCachePolicy.writeEnabled && cache != null) {
                            publish(cache, key, fs, path)?.let {
                                return@withPermit result(file, cache, it, key, DataSource.NETWORK)
                            }
                        }
                        SourceFetchResult(ImageSource(path, fs, closeable = AutoCloseable { deleteTemporary(fs, path) }),
                            file.mimeType.takeIf { '/' in it }, DataSource.NETWORK).also { decoderOwnsFile = true }
                    } finally {
                        if (!decoderOwnsFile) deleteTemporary(fs, path)
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                locksMutex.withLock { if (--entry.users == 0) locks.remove(key) }
            }
        }
    }

    private suspend fun publish(cache: DiskCache, key: String, fs: FileSystem, path: Path): DiskCache.Snapshot? {
        val editor = cacheIO { cache.openEditor(key) } ?: return null
        var completed = false
        try {
            fs.source(path).use { input ->
                cache.fileSystem.sink(editor.data).buffer().use { output ->
                    val buffer = Buffer()
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer, 64 * 1024L)
                        if (count == -1L) break
                        output.write(buffer, count)
                    }
                }
            }
            cache.fileSystem.write(editor.metadata) { writeUtf8("network-image-v2") }
            currentCoroutineContext().ensureActive()
            return editor.commitAndOpenSnapshot().also { completed = true }
        } catch (_: IOException) {
            return null
        } finally {
            if (!completed) cacheIO { editor.abort() }
        }
    }

    // Cache/storage failures are recoverable. Cancellation and programming errors still propagate.
    private inline fun <T> cacheIO(block: () -> T): T? = try { block() } catch (_: IOException) { null }
    private fun deleteTemporary(fs: FileSystem, path: Path) { cacheIO { fs.delete(path, mustExist = false) } }

    private fun result(file: NetworkFile, cache: DiskCache, snapshot: DiskCache.Snapshot, key: String, source: DataSource) =
        SourceFetchResult(ImageSource(snapshot.data, cache.fileSystem, key, snapshot),
            file.mimeType.takeIf { '/' in it }, source)
}
