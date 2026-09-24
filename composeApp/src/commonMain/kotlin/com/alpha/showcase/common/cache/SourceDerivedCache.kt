package com.alpha.showcase.common.cache

import coil3.ImageLoader

/** Only regenerable image bytes belong here. Paid AI summaries are durable user data. */
internal object SourceDerivedCache {
    var imageLoader: ImageLoader? = null

    suspend fun clear() {
        imageLoader?.memoryCache?.clear()
        imageLoader?.diskCache?.clear()
    }
}
