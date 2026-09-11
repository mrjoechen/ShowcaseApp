package com.alpha.showcase.common.cache

import coil3.ImageLoader
import com.alpha.showcase.common.ai.AiServices
import isWeb

/** Legacy image/summary caches have no source ownership index. They are regenerable. */
internal object SourceDerivedCache {
    var imageLoader: ImageLoader? = null

    suspend fun clear() {
        if (!isWeb()) AiServices.engine.clearSummaries()
        imageLoader?.memoryCache?.clear()
        imageLoader?.diskCache?.clear()
    }
}
