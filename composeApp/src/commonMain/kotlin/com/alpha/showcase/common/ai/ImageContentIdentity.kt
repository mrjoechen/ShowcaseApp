package com.alpha.showcase.common.ai

import coil3.decode.DecodeResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.BufferedSource
import okio.Buffer
import okio.HashingSink
import okio.blackholeSink
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Identity of the complete encoded file, before decoding, EXIF rotation or AI resizing. */
internal data class ImageContentIdentity(val contentId: String, val byteCount: Long) {
    init {
        require(contentId.matches(Regex("sha256-file-v1:[0-9a-f]{64}")))
        require(byteCount >= 0)
    }
}

internal data class IdentifiedDecodeResult(val result: DecodeResult?, val identity: ImageContentIdentity?)

internal expect suspend fun decodeWithImageIdentity(
    source: SourceFetchResult, options: Options,
    decode: suspend (SourceFetchResult) -> DecodeResult?,
): IdentifiedDecodeResult

/** Only EOF produces an identity; cancellation and read/transfer failures never publish one. */
internal suspend fun hashImageFile(source: BufferedSource, expectedLength: Long? = null): ImageContentIdentity {
    val digest = HashingSink.sha256(blackholeSink())
    val buffer = Buffer()
    var size = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = source.read(buffer, 64 * 1024L)
        if (count == -1L) break
        size += count
        digest.write(buffer, count)
    }
    require(expectedLength == null || size == expectedLength) { "Incomplete image file" }
    return ImageContentIdentity("sha256-file-v1:${digest.hash.hex()}", size)
}
