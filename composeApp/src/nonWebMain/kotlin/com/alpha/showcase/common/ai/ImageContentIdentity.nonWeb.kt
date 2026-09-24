package com.alpha.showcase.common.ai

import coil3.decode.DecodeResult
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.alpha.showcase.common.ui.play.MediaSourceMetadata
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Buffer
import okio.FileSystem
import okio.HashingSink
import okio.buffer
import kotlin.uuid.Uuid

/** Hash and decode one immutable snapshot of Coil's fetched bytes; never refetch the address. */
internal actual suspend fun decodeWithImageIdentity(
    source: SourceFetchResult, options: Options,
    decode: suspend (SourceFetchResult) -> DecodeResult?,
): IdentifiedDecodeResult {
    val fs = options.fileSystem
    val temporary = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "showcase-identity-${Uuid.random()}.tmp"
    val originalMetadata = source.source.metadata as? MediaSourceMetadata
    val originalPath = source.source.fileOrNull()
    val before = originalPath?.let { source.source.fileSystem.metadata(it) }
    val expected = before?.size
    try {
        var count = 0L
        val digest = HashingSink.sha256(fs.sink(temporary))
        digest.buffer().use { output ->
            val input = source.source.source()
            val buffer = Buffer()
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer, 64 * 1024L)
                if (read == -1L) break
                count += read
                output.write(buffer, read)
            }
        }
        require(expected == null || expected == count) { "Incomplete image file" }
        originalPath?.let {
            val after = source.source.fileSystem.metadata(it)
            require(before?.size == after.size && before?.lastModifiedAtMillis == after.lastModifiedAtMillis) { "Image changed while reading" }
        }
        val identity = originalMetadata?.identity ?: ImageContentIdentity("sha256-file-v1:${digest.hash.hex()}", count)
        // Do not retain URI/FD decoder shortcuts: decoding must read the very bytes just hashed.
        ImageSource(temporary, fs, metadata = originalMetadata).use { snapshot ->
            val decoded = decode(SourceFetchResult(snapshot, source.mimeType, source.dataSource))
            return IdentifiedDecodeResult(decoded, identity)
        }
    } finally {
        fs.delete(temporary, mustExist = false)
    }
}
