package com.alpha.showcase.common

import coil3.ComponentRegistry

actual fun ComponentRegistry.Builder.addPlatformComponents() {
    // No officially supported default GIF decoder for Coil 3 on Desktop, leaving empty.
    // SVG and standard image formats are handled generically.
}
