package com.alpha.showcase.common

import coil3.ComponentRegistry

actual fun ComponentRegistry.Builder.addPlatformComponents() {
    add(NetworkFileKeyer())
    add(NetworkFileFetcher.Factory())
    add(SkiaGifDecoder.Factory())
    add(DesktopHeicDecoder.Factory())
}
