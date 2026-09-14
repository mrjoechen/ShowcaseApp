package com.alpha.showcase.common

import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.smb.invokeRegisteredSmbBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem

private val smbImages = SmbImageFileReader(::invokeRegisteredSmbBridge)

internal class NetworkFileFetcher(
    private val networkFile: NetworkFile,
    private val options: Options,
    private val imageLoader: ImageLoader,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        var result: SourceFetchResult? = null
        try {
            return withContext(Dispatchers.Default) {
                CachedNetworkImage.fetch(networkFile, options, imageLoader.diskCache, FileSystem.SYSTEM_TEMPORARY_DIRECTORY) { _, path ->
                    smbImages.download(networkFile, path)
                }.also { result = it }
            }
        } catch (failure: Throwable) {
            try { result?.source?.close() } catch (closeFailure: Throwable) { failure.addSuppressed(closeFailure) }
            throw failure
        }
    }
    class Factory : Fetcher.Factory<NetworkFile> {
        override fun create(data: NetworkFile, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.remote is Smb && data.path.startsWith("smb://", ignoreCase = true)) {
                NetworkFileFetcher(data, options, imageLoader)
            } else null
    }
}
