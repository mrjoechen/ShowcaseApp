package com.alpha.showcase.common.ui.play

import coil3.request.ImageRequest
import coil3.request.allowHardware

internal actual fun ImageRequest.Builder.prepareBlurSource() {
    allowHardware(false)
}
