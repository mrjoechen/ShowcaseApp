package com.alpha.showcase.common

import coil3.ComponentRegistry

actual fun ComponentRegistry.Builder.addPlatformComponents() {
    add(WebHeicDecoder.Factory())
    add(WebAvifDecoder.Factory())
    add(SkiaGifDecoder.Factory())
}
