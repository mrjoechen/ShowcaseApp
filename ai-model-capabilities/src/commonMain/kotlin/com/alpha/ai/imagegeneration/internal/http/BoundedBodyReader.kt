package com.alpha.ai.imagegeneration.internal.http

import okio.Buffer
import okio.BufferedSource

internal object BoundedBodyReader {
    fun read(source: BufferedSource, limitBytes: Long): ByteArray {
        if (limitBytes < 0) throw ImageGenerationTransportException("Invalid response limit")
        val buffer = Buffer()
        var total = 0L
        while (true) {
            val remaining = limitBytes - total
            val probeBytes = if (remaining == Long.MAX_VALUE) Long.MAX_VALUE else remaining + 1
            val read = source.read(buffer, minOf(8_192L, probeBytes))
            if (read == -1L) return buffer.readByteArray()
            total += read
            if (total > limitBytes) throw ImageGenerationTransportException("Response exceeds size limit")
        }
    }
}
