package com.alpha.facedetection.internal

import java.security.MessageDigest

internal object YuNetModel {
    private const val RESOURCE = "/com/alpha/facedetection/models/face_detection_yunet_2026may.onnx"
    private const val SIZE = 229_738
    private const val SHA256 = "ebafce4e3c118d6554634be5c27ab333b4c047a9a8c3faf1d7cf93101c22f0f0"

    val bytes: ByteArray by lazy {
        checkNotNull(YuNetModel::class.java.getResourceAsStream(RESOURCE)) {
            "Bundled YuNet model is missing"
        }.use { it.readBytes() }.also(::verify)
    }

    fun verify(bytes: ByteArray) {
        check(bytes.size == SIZE) { "Bundled YuNet model size mismatch" }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        check(hash == SHA256) { "Bundled YuNet model SHA-256 mismatch" }
    }
}
