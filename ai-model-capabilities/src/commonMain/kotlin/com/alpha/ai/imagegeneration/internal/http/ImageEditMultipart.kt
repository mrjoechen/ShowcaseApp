package com.alpha.ai.imagegeneration.internal.http

import kotlin.random.Random
import okio.Buffer

internal data class ImageEditMultipart(val contentType: String, val bytes: ByteArray) {
    companion object {
        fun encode(model: String, prompt: String, fileName: String, mimeType: String, bytes: ByteArray): ImageEditMultipart {
            val boundary = "ai-image-" + Random.nextBytes(24).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
            val body = Buffer()
            fun field(name: String, value: String) {
                body.writeUtf8("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
            }
            field("model", model)
            field("prompt", prompt)
            field("output_format", "png")
            val safeFileName = fileName.replace("\n", "%0A").replace("\r", "%0D").replace("\"", "%22")
            body.writeUtf8("--$boundary\r\nContent-Disposition: form-data; name=\"image\"; filename=\"$safeFileName\"\r\n")
            body.writeUtf8("Content-Type: $mimeType\r\nContent-Length: ${bytes.size}\r\n\r\n")
            body.write(bytes)
            body.writeUtf8("\r\n--$boundary--\r\n")
            return ImageEditMultipart("multipart/form-data; boundary=$boundary", body.readByteArray())
        }
    }
}
