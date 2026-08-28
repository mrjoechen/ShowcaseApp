package com.alpha.showcase.common.mtphoto

import coil3.Extras
import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.network.HttpException
import coil3.network.httpHeaders
import coil3.request.Options
import com.alpha.showcase.api.mtphoto.buildMTPhotoGatewayUrl
import kotlinx.coroutines.CancellationException

internal class MTPhotoFetchException(
    val statusCode: Int? = null,
) : RuntimeException(
    statusCode?.let { "MTPhoto image request failed with HTTP $it" }
        ?: "MTPhoto image request failed",
)

internal class MTPhotoFetcher(
    private val file: MTPhotoFile,
    private val options: Options,
    private val authManager: MTPhotoAuthManager,
    private val imageLoader: ImageLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        var retriedUnauthorized = false
        while (true) {
            try {
                return fetchOnce()
            } catch (error: CancellationException) {
                throw error
            } catch (error: HttpException) {
                val statusCode = error.response.code
                if (!retriedUnauthorized && statusCode == HTTP_UNAUTHORIZED) {
                    retriedUnauthorized = true
                    authManager.invalidate(file.sourceKey)
                    continue
                }
                throw MTPhotoFetchException(statusCode)
            } catch (error: MTPhotoFetchException) {
                throw error
            } catch (_: Exception) {
                // Delegate failures can retain the credential-bearing request URL.
                throw MTPhotoFetchException()
            }
        }
    }

    private suspend fun fetchOnce(): FetchResult {
        val auth = authManager.getAuthForKey(file.sourceKey)
        val url = buildMTPhotoGatewayUrl(
            baseUrl = authManager.baseUrlForKey(file.sourceKey),
            fileId = file.fileId,
            md5 = file.md5,
            albumId = file.albumId,
            authCode = auth.authCode,
        )
        val delegateOptions = options.withMTPhotoRequest(
            cacheKey = file.cacheKey,
            headerName = auth.headerName,
            headerValue = auth.headerValue,
        )
        val delegateData = imageLoader.components.map(url, delegateOptions)
        if (delegateData is MTPhotoFile) throw MTPhotoFetchException()

        var startIndex = 0
        while (true) {
            val (fetcher, index) = imageLoader.components.newFetcher(
                data = delegateData,
                options = delegateOptions,
                imageLoader = imageLoader,
                startIndex = startIndex,
            ) ?: throw MTPhotoFetchException()
            val result = fetcher.fetch()
            if (result != null) return result.withMTPhotoMimeType(file.mimeType)
            startIndex = index + 1
        }
    }

    class Factory(
        private val authManager: MTPhotoAuthManager = MTPhotoRuntime.authManager,
    ) : Fetcher.Factory<MTPhotoFile> {
        override fun create(
            data: MTPhotoFile,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = MTPhotoFetcher(data, options, authManager, imageLoader)
    }

    override fun toString(): String = "MTPhotoFetcher(file=<redacted>)"
}

private fun FetchResult.withMTPhotoMimeType(fallbackMimeType: String): FetchResult {
    if (this !is SourceFetchResult || !mimeType.isNullOrBlank() || fallbackMimeType.isBlank()) {
        return this
    }
    return SourceFetchResult(
        source = source,
        mimeType = fallbackMimeType,
        dataSource = dataSource,
    )
}

private const val HTTP_UNAUTHORIZED = 401

internal class MTPhotoFileKeyer : Keyer<MTPhotoFile> {
    override fun key(data: MTPhotoFile, options: Options): String = data.cacheKey
}

private fun Options.withMTPhotoRequest(
    cacheKey: String,
    headerName: String,
    headerValue: String,
): Options {
    val headers = httpHeaders.newBuilder().apply {
        this[headerName] = headerValue
    }.build()
    val requestExtras = extras.newBuilder().apply {
        this[Extras.Key.httpHeaders] = headers
    }.build()
    return copy(
        diskCacheKey = cacheKey,
        extras = requestExtras,
    )
}
