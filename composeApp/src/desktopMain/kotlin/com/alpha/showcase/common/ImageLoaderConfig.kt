package com.alpha.showcase.common

import coil3.ComponentRegistry

actual fun ComponentRegistry.Builder.addPlatformComponents() {
    add(NetworkFileKeyer())
    add(NetworkFileFetcher.Factory())
    // No officially supported default GIF decoder for Coil 3 on Desktop, leaving empty.
    // SVG and standard image formats are handled generically.
}
