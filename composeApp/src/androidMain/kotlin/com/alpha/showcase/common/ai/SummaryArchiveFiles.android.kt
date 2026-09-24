package com.alpha.showcase.common.ai

import currentActivity
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init

internal actual fun prepareSummaryFileDialogs() {
    FileKit.init(checkNotNull(currentActivity) { "An active window is required" })
}
