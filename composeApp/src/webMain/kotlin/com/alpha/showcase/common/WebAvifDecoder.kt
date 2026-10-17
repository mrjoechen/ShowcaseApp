package com.alpha.showcase.common

import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.BufferedSource

/** Skia on the web has no AV1 codec; the browser decodes AVIF, including its display transforms. */
internal class WebAvifDecoder(private val source: ImageSource, private val options: Options) : Decoder {
    override suspend fun decode(): DecodeResult = slots.withPermit {
        decodeWithBridge("AVIF", source, options, HeicBridge::decodeAvif)
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? =
            if (isAvif(result.source.source())) WebAvifDecoder(result.source, options) else null
    }

    companion object {
        private val slots = Semaphore(1)
        private val brands = setOf("avif", "avis")

        internal fun isAvif(source: BufferedSource): Boolean = hasFtypBrand(source, brands)
    }
}
