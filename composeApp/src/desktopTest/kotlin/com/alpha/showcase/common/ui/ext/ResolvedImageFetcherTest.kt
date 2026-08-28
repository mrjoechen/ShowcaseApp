package com.alpha.showcase.common.ui.ext

import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.Uri
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.network.httpHeaders
import coil3.request.Options
import com.alpha.showcase.common.repo.SignedS3ObjectUrl
import com.alpha.showcase.common.ui.play.ResolvedImageModel
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ResolvedImageFetcherTest {

    @Test
    fun delegatesMappedSignedUrlAndScopesHeadersToInternalOptions() = runTest {
        val rawUrl = "https://signed.example/album/cat.jpg?credential=url-secret"
        val delegate = RecordingUriFactory()
        val loader = imageLoader(delegate, includeNullFetcher = true)
        val model = model(rawUrl, headers = mapOf("X-Playback-Token" to "header-secret"))

        try {
            val outerOptions = Options(PlatformContext.INSTANCE, diskCacheKey = model.cacheKey)
            val fetcher = ResolvedImageFetcher.Factory(nowMillis = { 1_000L })
                .create(model, outerOptions, loader)!!

            assertIs<ImageFetchResult>(fetcher.fetch())
            assertEquals(rawUrl, delegate.data.toString())
            assertEquals("header-secret", delegate.options!!.httpHeaders["x-playback-token"])
            assertEquals(model.cacheKey, delegate.options!!.diskCacheKey)
            assertTrue(outerOptions.httpHeaders.asMap().isEmpty())
            assertFalse(fetcher.toString().contains(rawUrl))
            assertFalse(fetcher.toString().contains("header-secret"))
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun delegateFailureIsReplacedBySecretSafeExceptionWithoutCause() = runTest {
        val rawUrl = "https://signed.example/cat.jpg?credential=url-secret"
        val headerSecret = "Bearer header-secret"
        val delegate = RecordingUriFactory { _, _ ->
            throw IllegalStateException("network failed for $rawUrl using $headerSecret")
        }
        val loader = imageLoader(delegate)
        val model = model(rawUrl, headers = mapOf("Authorization" to headerSecret))

        try {
            val fetcher = ResolvedImageFetcher.Factory(nowMillis = { 1_000L })
                .create(model, Options(PlatformContext.INSTANCE), loader)!!
            val thrown = assertFailsWith<ResolvedImageFetchException> { fetcher.fetch() }

            assertNull(thrown.cause)
            assertFalse(thrown.toString().contains(rawUrl))
            assertFalse(thrown.toString().contains(headerSecret))
            assertFalse(thrown.stackTraceToString().contains(rawUrl))
            assertFalse(thrown.stackTraceToString().contains(headerSecret))
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun delegateCancellationPropagatesWithIdentityPreserved() = runTest {
        val cancellation = CancellationException("request left composition")
        val delegate = RecordingUriFactory { _, _ -> throw cancellation }
        val loader = imageLoader(delegate)

        try {
            val fetcher = ResolvedImageFetcher.Factory(nowMillis = { 1_000L })
                .create(model("https://signed.example/cat.jpg"), Options(PlatformContext.INSTANCE), loader)!!
            val thrown = assertFailsWith<CancellationException> { fetcher.fetch() }

            assertSame(cancellation, thrown)
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun refreshFailureIsSanitizedBeforeLeavingFetcher() = runTest {
        val rawUrl = "https://signed.example/expired.jpg?credential=expired-secret"
        val refreshFailure = IllegalStateException("could not refresh $rawUrl")
        val initial = SignedS3ObjectUrl(rawUrl, expiresAtEpochMillis = 1_000L)
        val model = ResolvedImageModel(
            initialSignedRequest = initial,
            stableKey = "album/cat.jpg",
            cacheKey = "s3:stable",
            refreshSignedRequest = { throw refreshFailure },
        )
        val delegate = RecordingUriFactory()
        val loader = imageLoader(delegate)

        try {
            val fetcher = ResolvedImageFetcher.Factory(nowMillis = { 1_000L })
                .create(model, Options(PlatformContext.INSTANCE), loader)!!
            val thrown = assertFailsWith<ResolvedImageFetchException> { fetcher.fetch() }

            assertNull(thrown.cause)
            assertFalse(thrown.toString().contains(rawUrl))
            assertFalse(thrown.toString().contains("expired-secret"))
            assertNull(delegate.options)
        } finally {
            loader.shutdown()
        }
    }

    private fun imageLoader(
        delegate: RecordingUriFactory,
        includeNullFetcher: Boolean = false,
    ) = ImageLoader.Builder(PlatformContext.INSTANCE)
        .components {
            add(ResolvedImageFetcher.Factory())
            if (includeNullFetcher) add(NullUriFactory())
            add(delegate)
        }
        .build()

    private fun model(
        rawUrl: String,
        headers: Map<String, String> = emptyMap(),
    ): ResolvedImageModel {
        val signed = SignedS3ObjectUrl(rawUrl, expiresAtEpochMillis = Long.MAX_VALUE)
        return ResolvedImageModel(
            initialSignedRequest = signed,
            stableKey = "album/cat.jpg",
            cacheKey = "s3:stable",
            headers = headers,
            refreshSignedRequest = { signed },
        )
    }

    private class NullUriFactory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher =
            Fetcher { null }
    }

    private class RecordingUriFactory(
        private val fetchBlock: suspend (Uri, Options) -> FetchResult? = { _, _ ->
            ImageFetchResult(
                image = ColorImage(),
                isSampled = false,
                dataSource = DataSource.MEMORY,
            )
        },
    ) : Fetcher.Factory<Uri> {
        lateinit var data: Uri
        var options: Options? = null

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher {
            this.data = data
            this.options = options
            return Fetcher { fetchBlock(data, options) }
        }
    }
}
