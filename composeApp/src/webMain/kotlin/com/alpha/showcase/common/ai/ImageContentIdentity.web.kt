package com.alpha.showcase.common.ai

import coil3.decode.DecodeResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options

// AI is disabled in browsers; preserve their existing image-loading behavior.
internal actual suspend fun decodeWithImageIdentity(source: SourceFetchResult, options: Options,
    decode: suspend (SourceFetchResult) -> DecodeResult?): IdentifiedDecodeResult = IdentifiedDecodeResult(decode(source), null)
