package com.alpha.showcase.common.mtphoto

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.alpha.showcase.api.mtphoto.MTPhotoApi
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import okio.Buffer

internal class MTPhotoFetcher(
    private val file: MTPhotoFile,
    private val options: Options,
    private val authManager: MTPhotoAuthManager,
    private val api: MTPhotoApi,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        return try {
            fetchOnce()
        } catch (error: ClientRequestException) {
            if (error.response.status != HttpStatusCode.Unauthorized) throw error
            authManager.invalidate(file.sourceKey)
            fetchOnce()
        }
    }

    private suspend fun fetchOnce(): FetchResult {
        val auth = authManager.getAuthForKey(file.sourceKey)
        val bytes = api.downloadFile(
            baseUrl = authManager.baseUrlForKey(file.sourceKey),
            fileId = file.fileId,
            md5 = file.md5,
            albumId = file.albumId,
            authCode = auth.authCode,
            headerName = auth.headerName,
            headerValue = auth.headerValue,
        )
        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().write(bytes),
                fileSystem = options.fileSystem,
            ),
            mimeType = file.mimeType.takeIf { it.isNotBlank() },
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(
        private val authManager: MTPhotoAuthManager = MTPhotoRuntime.authManager,
        private val api: MTPhotoApi = MTPhotoApi(),
    ) : Fetcher.Factory<MTPhotoFile> {
        override fun create(
            data: MTPhotoFile,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = MTPhotoFetcher(data, options, authManager, api)
    }
}

internal class MTPhotoFileKeyer : Keyer<MTPhotoFile> {
    override fun key(data: MTPhotoFile, options: Options): String = data.cacheKey
}
