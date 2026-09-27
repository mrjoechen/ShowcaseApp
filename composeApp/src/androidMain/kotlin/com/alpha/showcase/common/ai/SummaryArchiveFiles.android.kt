package com.alpha.showcase.common.ai

import currentActivity
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.init
import io.github.vinceglb.filekit.dialogs.openFilePicker

internal actual fun prepareSummaryFileDialogs() {
    FileKit.init(checkNotNull(currentActivity) { "An active window is required" })
}

internal actual suspend fun openSummaryArchivePicker(): PlatformFile? =
    FileKit.openFilePicker(type = FileKitType.File(extensions = listOf("scsummary")))
