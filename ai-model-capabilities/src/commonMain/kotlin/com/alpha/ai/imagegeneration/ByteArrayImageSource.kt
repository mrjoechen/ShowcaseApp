package com.alpha.ai.imagegeneration

import okio.Buffer
import okio.Source

/** Repeatable image input that owns a snapshot of [bytes]. */
class ByteArrayImageSource(
    bytes: ByteArray,
    override val mimeType: String,
    override val fileName: String = "source-image",
) : ImageSource {
    private val bytes = bytes.copyOf()
    override val contentLength: Long get() = bytes.size.toLong()
    override fun openSource(): Source = Buffer().write(bytes)
}
