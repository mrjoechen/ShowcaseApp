package com.alpha.showcase.common

import coil3.ComponentRegistry

actual fun ComponentRegistry.Builder.addPlatformComponents() {
    add(WebHeicDecoder.Factory())
    add(SkiaGifDecoder.Factory())
}
