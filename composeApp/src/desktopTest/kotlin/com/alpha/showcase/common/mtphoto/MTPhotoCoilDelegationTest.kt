package com.alpha.showcase.common.mtphoto

import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.network.HttpException
import coil3.network.NetworkResponse
import coil3.network.httpHeaders
import coil3.request.Options
import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO_AUTH_TYPE_API_KEY
import com.alpha.showcase.common.networkfile.storage.remote.MTPhotoSource
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MTPhotoCoilDelegationTest {

    @Test
    fun delegatesAuthenticatedRequestToCoilWithStableCacheKey() = runBlocking {
        val directDownloadCount = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/gateway/file/23/abc123") { exchange ->
                directDownloadCount.incrementAndGet()
                val body = byteArrayOf(1, 2, 3)
                exchange.responseHeaders.add("Content-Type", "image/jpeg")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
        val authManager = MTPhotoAuthManager(
            authLoader = {
                MTPhotoAuthSession(
                    authCode = "auth-code-secret",
                    headerName = "x-api-key",
                    headerValue = "api-key-secret",
                )
            },
        )
        val delegate = RecordingUriFactory()
        val imageLoader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(delegate) }
            .build()
        server.start()

        try {
            val sourceKey = authManager.register(source(server.address.port))
            val file = file(sourceKey)
            val outerOptions = Options(
                context = PlatformContext.INSTANCE,
                diskCacheKey = "caller-cache-key",
            )
            val fetcher = MTPhotoFetcher.Factory(authManager = authManager)
                .create(file, outerOptions, imageLoader)

            assertIs<ImageFetchResult>(fetcher.fetch())
            assertEquals(0, directDownloadCount.get())
            assertEquals(
                "http://127.0.0.1:${server.address.port}/gateway/file/23/abc123" +
                    "?albumId=17&type=ori&auth_code=auth-code-secret",
                delegate.data.toString(),
            )
            assertEquals("api-key-secret", delegate.options.httpHeaders["x-api-key"])
            assertEquals(file.cacheKey, delegate.options.diskCacheKey)
            assertTrue(outerOptions.httpHeaders.asMap().isEmpty())
            assertFalse(fetcher.toString().contains("auth-code-secret"))
            assertFalse(fetcher.toString().contains("api-key-secret"))
        } finally {
            imageLoader.shutdown()
            server.stop(0)
        }
    }

    @Test
    fun unauthorizedRequestRefreshesAuthAndRetriesThroughCoilOnce() = runBlocking {
        var authLoads = 0
        val authManager = MTPhotoAuthManager(
            authLoader = {
                authLoads += 1
                MTPhotoAuthSession(
                    authCode = "auth-$authLoads",
                    headerName = "x-api-key",
                    headerValue = "key-$authLoads",
                )
            },
        )
        val sourceKey = authManager.register(source(port = 12345))
        val delegate = RecordingUriFactory { attempt, _, _ ->
            if (attempt == 1) throw HttpException(NetworkResponse(code = 401))
            successfulResult()
        }
        val imageLoader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(delegate) }
            .build()

        try {
            val fetcher = MTPhotoFetcher.Factory(authManager = authManager).create(
                data = file(sourceKey),
                options = Options(PlatformContext.INSTANCE),
                imageLoader = imageLoader,
            )

            assertIs<ImageFetchResult>(fetcher.fetch())
            assertEquals(2, authLoads)
            assertEquals(2, delegate.requests.size)
            assertTrue(delegate.requests[0].data.toString().endsWith("auth_code=auth-1"))
            assertTrue(delegate.requests[1].data.toString().endsWith("auth_code=auth-2"))
            assertEquals("key-1", delegate.requests[0].options.httpHeaders["x-api-key"])
            assertEquals("key-2", delegate.requests[1].options.httpHeaders["x-api-key"])
        } finally {
            imageLoader.shutdown()
        }
    }

    @Test
    fun usesFileMimeTypeWhenTheCoilResponseHasNoContentType() = runBlocking {
        val authManager = MTPhotoAuthManager(
            authLoader = {
                MTPhotoAuthSession("auth", "x-api-key", "key")
            },
        )
        val sourceKey = authManager.register(source(port = 12345))
        val delegate = RecordingUriFactory { _, _, options ->
            SourceFetchResult(
                source = ImageSource(Buffer().write(byteArrayOf(1, 2, 3)), options.fileSystem),
                mimeType = null,
                dataSource = DataSource.NETWORK,
            )
        }
        val imageLoader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(delegate) }
            .build()

        try {
            val fetcher = MTPhotoFetcher.Factory(authManager = authManager).create(
                data = file(sourceKey),
                options = Options(PlatformContext.INSTANCE, fileSystem = FileSystem.SYSTEM),
                imageLoader = imageLoader,
            )

            val result = assertIs<SourceFetchResult>(fetcher.fetch())
            try {
                assertEquals("image/jpeg", result.mimeType)
            } finally {
                result.source.close()
            }
        } finally {
            imageLoader.shutdown()
        }
    }

    @Test
    fun repeatedUnauthorizedStopsAfterOneRetry() = runBlocking {
        var authLoads = 0
        val authManager = MTPhotoAuthManager(
            authLoader = {
                authLoads += 1
                MTPhotoAuthSession("secret-auth-$authLoads", "x-api-key", "secret-key-$authLoads")
            },
        )
        val sourceKey = authManager.register(source(port = 12345))
        val delegate = RecordingUriFactory { _, _, _ ->
            throw HttpException(NetworkResponse(code = 401))
        }
        val imageLoader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(delegate) }
            .build()

        try {
            val fetcher = MTPhotoFetcher.Factory(authManager = authManager).create(
                data = file(sourceKey),
                options = Options(PlatformContext.INSTANCE),
                imageLoader = imageLoader,
            )

            val error = assertFailsWith<MTPhotoFetchException> { fetcher.fetch() }
            assertEquals(401, error.statusCode)
            assertNull(error.cause)
            assertEquals(2, authLoads)
            assertEquals(2, delegate.requests.size)
            assertFalse(error.stackTraceToString().contains("secret-auth"))
            assertFalse(error.stackTraceToString().contains("secret-key"))
        } finally {
            imageLoader.shutdown()
        }
    }

    @Test
    fun delegateCancellationPropagatesWithoutRetry() = runBlocking {
        var authLoads = 0
        val cancellation = CancellationException("request left composition")
        val authManager = MTPhotoAuthManager(
            authLoader = {
                authLoads += 1
                MTPhotoAuthSession("auth", "x-api-key", "key")
            },
        )
        val sourceKey = authManager.register(source(port = 12345))
        val delegate = RecordingUriFactory { _, _, _ -> throw cancellation }
        val imageLoader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(delegate) }
            .build()

        try {
            val fetcher = MTPhotoFetcher.Factory(authManager = authManager).create(
                data = file(sourceKey),
                options = Options(PlatformContext.INSTANCE),
                imageLoader = imageLoader,
            )

            val thrown = assertFailsWith<CancellationException> { fetcher.fetch() }
            assertSame(cancellation, thrown)
            assertEquals(1, authLoads)
            assertEquals(1, delegate.requests.size)
        } finally {
            imageLoader.shutdown()
        }
    }

    @Test
    fun delegateFailureIsSanitizedWithoutLeakingRequestCredentials() = runBlocking {
        val authCode = "auth-code-secret"
        val headerValue = "api-key-secret"
        val authManager = MTPhotoAuthManager(
            authLoader = {
                MTPhotoAuthSession(authCode, "x-api-key", headerValue)
            },
        )
        val sourceKey = authManager.register(source(port = 12345))
        val delegate = RecordingUriFactory { _, data, options ->
            throw IllegalStateException(
                "failed ${data} with ${options.httpHeaders["x-api-key"]}",
            )
        }
        val imageLoader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(delegate) }
            .build()

        try {
            val fetcher = MTPhotoFetcher.Factory(authManager = authManager).create(
                data = file(sourceKey),
                options = Options(PlatformContext.INSTANCE),
                imageLoader = imageLoader,
            )

            val error = assertFailsWith<MTPhotoFetchException> { fetcher.fetch() }
            assertNull(error.cause)
            assertFalse(error.stackTraceToString().contains(authCode))
            assertFalse(error.stackTraceToString().contains(headerValue))
        } finally {
            imageLoader.shutdown()
        }
    }

    private fun source(port: Int) = MTPhotoSource(
        name = "Photos",
        url = "http://127.0.0.1:$port",
        authType = MTPHOTO_AUTH_TYPE_API_KEY,
        apiKey = "stored-key",
        albumId = 17,
        albumName = "Album",
    )

    private fun file(sourceKey: String) = MTPhotoFile(
        sourceKey = sourceKey,
        albumId = 17,
        fileId = 23,
        md5 = "abc123",
        fileName = "photo.jpg",
        tokenAt = "2026-01-01",
        mimeType = "image/jpeg",
        width = 100,
        height = 100,
    )

    private data class DelegatedRequest(val data: Uri, val options: Options)

    private class RecordingUriFactory(
        private val fetchBlock: suspend (Int, Uri, Options) -> FetchResult? = { _, _, _ ->
            successfulResult()
        },
    ) : Fetcher.Factory<Uri> {
        val requests = mutableListOf<DelegatedRequest>()

        val data: Uri
            get() = requests.last().data

        val options: Options
            get() = requests.last().options

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher {
            requests += DelegatedRequest(data, options)
            val attempt = requests.size
            return Fetcher { fetchBlock(attempt, data, options) }
        }
    }

    companion object {
        private fun successfulResult() = ImageFetchResult(
            image = ColorImage(),
            isSampled = false,
            dataSource = DataSource.MEMORY,
        )
    }
}
