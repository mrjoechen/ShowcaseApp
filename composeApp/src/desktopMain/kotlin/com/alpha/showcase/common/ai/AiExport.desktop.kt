package com.alpha.showcase.common.ai

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write

internal actual suspend fun exportAiImage(name: String, bytes: ByteArray): Boolean {
    val file = FileKit.openFileSaver(suggestedName = name.substringBeforeLast('.'), defaultExtension = name.substringAfterLast('.'))
    if (file == null) return false
    file.write(bytes)
    return true
}
