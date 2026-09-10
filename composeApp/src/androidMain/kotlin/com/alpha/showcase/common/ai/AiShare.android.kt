package com.alpha.showcase.common.ai

import io.github.vinceglb.filekit.*
import io.github.vinceglb.filekit.dialogs.shareFile
import kotlin.uuid.Uuid

internal actual suspend fun shareAiImage(name: String, bytes: ByteArray) {
    val file = FileKit.cacheDir / "${Uuid.random()}-${name.substringAfterLast('/')}"
    file.write(bytes)
    FileKit.shareFile(file)
}
