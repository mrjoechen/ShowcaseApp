package com.alpha.showcase.common

import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.alpha.showcase.common.networkfile.model.NetworkFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import okio.buffer
import okio.source

internal class NetworkFileFetcher(
    private val networkFile: NetworkFile,
    private val options: Options,
    private val imageLoader: ImageLoader,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        var result: SourceFetchResult? = null
        try {
            return withContext(Dispatchers.IO) {
                CachedNetworkImage.fetch(networkFile, options, imageLoader.diskCache, FileSystem.SYSTEM_TEMPORARY_DIRECTORY) { fs, path ->
                    NetworkFileReader.getInstance().readFile(networkFile).getOrThrow().source().use { source ->
                        fs.sink(path).buffer().use { sink ->
                            val buffer = Buffer()
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = source.read(buffer, 64 * 1024L)
                                if (count == -1L) break
                                sink.write(buffer, count)
                            }
                        }
                    }
                }.also { result = it }
            }
        } catch (failure: Throwable) {
            // withContext can cancel while handing the completed result back to Coil.
            try { result?.source?.close() } catch (closeFailure: Throwable) { failure.addSuppressed(closeFailure) }
            throw failure
        }
    }

    class Factory : Fetcher.Factory<NetworkFile> {
        override fun create(data: NetworkFile, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.path.substringBefore("://").lowercase() in setOf("smb", "ftp", "sftp")) {
                NetworkFileFetcher(data, options, imageLoader)
            } else null
    }
}
