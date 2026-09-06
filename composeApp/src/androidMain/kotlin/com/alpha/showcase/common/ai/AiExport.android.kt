package com.alpha.showcase.common.ai

import android.os.Build
import currentActivity
import io.github.vinceglb.filekit.saveImageToGallery
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.dialogs.init
import io.github.vinceglb.filekit.write

internal actual suspend fun exportAiImage(name: String, bytes: ByteArray): Boolean {
    if (Build.VERSION.SDK_INT >= 29) {
        FileKit.saveImageToGallery(bytes, name).getOrThrow()
        return true
    }
    FileKit.init(checkNotNull(currentActivity) { "An active window is required to save the image" })
    val file = FileKit.openFileSaver(suggestedName = name.substringBeforeLast('.'), defaultExtension = name.substringAfterLast('.'))
    if (file == null) return false
    file.write(bytes)
    return true
}
