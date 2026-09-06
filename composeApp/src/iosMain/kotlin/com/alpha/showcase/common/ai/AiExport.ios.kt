package com.alpha.showcase.common.ai

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.saveImageToGallery

internal actual suspend fun exportAiImage(name: String, bytes: ByteArray): Boolean {
    FileKit.saveImageToGallery(bytes, name).getOrThrow()
    return true
}
