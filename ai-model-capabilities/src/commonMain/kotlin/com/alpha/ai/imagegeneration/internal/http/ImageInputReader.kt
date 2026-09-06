package com.alpha.ai.imagegeneration.internal.http

import com.alpha.ai.imagegeneration.ImageSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Buffer
import okio.use

internal suspend fun readImageSource(source: ImageSource, supportedMimeTypes: Set<String>, limitBytes: Long): ByteArray {
    if (source.mimeType !in supportedMimeTypes) throw ImageGenerationTransportException("Unsupported image input")
    if (limitBytes < 0 || source.contentLength?.let { it < 0 || it > limitBytes } == true) {
        throw ImageGenerationTransportException("Image input exceeds size limit")
    }
    try {
        return source.openSource().use { input ->
            val result = Buffer()
            while (true) {
                currentCoroutineContext().ensureActive()
                val remaining = limitBytes - result.size
                val count = input.read(result, minOf(16_384L, if (remaining == Long.MAX_VALUE) remaining else remaining + 1))
                if (count == -1L) break
                if (result.size > limitBytes) throw ImageGenerationTransportException("Image input exceeds size limit")
            }
            result.readByteArray()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: ImageGenerationTransportException) {
        throw error
    } catch (_: Exception) {
        throw ImageGenerationTransportException("Unable to read image input")
    }
}
