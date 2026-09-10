package com.alpha.showcase.common

import coil3.ComponentRegistry

actual fun ComponentRegistry.Builder.addPlatformComponents() {
    add(GalleryAssetKeyer())
    add(GalleryAssetFetcher.Factory())
    add(SkiaGifDecoder.Factory())
    add(NetworkFileKeyer())
    add(NetworkFileFetcher.Factory())
}
