package com.alpha.showcase.common.ui.ext

import coil3.Extras
import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.network.httpHeaders
import coil3.request.Options
import com.alpha.showcase.common.ui.play.ResolvedImageModel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal class ResolvedImageFetcher(
    private val data: ResolvedImageModel,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val nowMillis: () -> Long,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        try {
            val signedRequest = data.resolveForFetch(nowMillis())
            val delegateOptions = options.withResolvedImage(data)
            val delegateData = imageLoader.components.map(
                signedRequest.urlForFetch(),
                delegateOptions,
            )
            if (delegateData is ResolvedImageModel) throw ResolvedImageFetchException()

            var startIndex = 0
            while (true) {
                val (fetcher, index) = imageLoader.components.newFetcher(
                    data = delegateData,
                    options = delegateOptions,
                    imageLoader = imageLoader,
                    startIndex = startIndex,
                ) ?: throw ResolvedImageFetchException()
                fetcher.fetch()?.let { return it }
                startIndex = index + 1
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            throw ResolvedImageFetchException()
        }
    }

    override fun toString(): String = "ResolvedImageFetcher(data=<redacted>)"

    internal class Factory(
        private val nowMillis: () -> Long = ::systemClockMillis,
    ) : Fetcher.Factory<ResolvedImageModel> {
        override fun create(
            data: ResolvedImageModel,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = ResolvedImageFetcher(data, options, imageLoader, nowMillis)
    }
}

internal class ResolvedImageFetchException :
    RuntimeException("Unable to fetch resolved image.")

private fun Options.withResolvedImage(data: ResolvedImageModel): Options {
    val headers = httpHeaders.newBuilder().apply {
        data.headers.forEach { (name, value) -> this[name] = value }
    }.build()
    val extras = extras.newBuilder().apply {
        this[Extras.Key.httpHeaders] = headers
    }.build()
    return copy(
        diskCacheKey = data.cacheKey,
        extras = extras,
    )
}

@OptIn(ExperimentalTime::class)
private fun systemClockMillis(): Long = Clock.System.now().toEpochMilliseconds()
