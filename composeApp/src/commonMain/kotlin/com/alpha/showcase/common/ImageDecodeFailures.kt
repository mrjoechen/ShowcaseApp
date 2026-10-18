package com.alpha.showcase.common

import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.CancellationException

/** The bytes were fetched, but no decoder could turn them into pixels. */
internal class ImageDecodeException(cause: Throwable) : Exception(cause.message, cause)

/**
 * Registered before every other decoder so it wraps whichever one Coil picks, including
 * Coil's own Skia and BitmapFactory decoders. Codec messages such as
 * "Failed to Image::makeFromEncoded" mean nothing to users; this marker lets the UI
 * explain them as a format problem while Coil's logger keeps the original error.
 */
internal class DecodeFailureReporter : Decoder.Factory {
    override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
        val components = imageLoader.components
        val next = components.decoderFactories.indexOfFirst { it === this } + 1
        val first = components.newDecoder(result, options, imageLoader, next) ?: return null
        return Decoder {
            var candidate: Pair<Decoder, Int>? = first
            var decoded: DecodeResult? = null
            while (decoded == null && candidate != null) {
                decoded = try {
                    candidate.first.decode()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw ImageDecodeException(error)
                }
                // A null result means "not mine"; keep Coil's fallthrough without restarting the chain.
                if (decoded == null) candidate = components.newDecoder(result, options, imageLoader, candidate.second + 1)
            }
            decoded ?: throw ImageDecodeException(IllegalStateException("No decoder produced an image"))
        }
    }
}

/** True when the image arrived but could not be decoded, as opposed to failing to load. */
internal fun Throwable.isImageDecodeFailure(): Boolean = generateSequence(this) { it.cause }.any {
    it is ImageDecodeException || it.message?.startsWith("Unable to create a decoder") == true
}
