package com.alpha.showcase.common

import android.os.Build.VERSION.SDK_INT
import coil3.ComponentRegistry
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder

actual fun ComponentRegistry.Builder.addPlatformComponents() {
    add(NetworkFileKeyer())
    add(NetworkFileFetcher.Factory())
    if (SDK_INT >= 28) {
        add(AnimatedImageDecoder.Factory())
    } else {
        add(GifDecoder.Factory())
    }
}
