package com.alpha.showcase.common

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.alpha.showcase.common.networkfile.model.NetworkFile
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.buffer
import okio.source

internal class NetworkFileFetcher(
    private val networkFile: NetworkFile,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        return networkFetchSemaphore.withPermit {
            val streamInfo = NetworkFileReader.getInstance()
                .readFileWithInfo(networkFile)
                .getOrElse { throw it }

            SourceFetchResult(
                source = ImageSource(
                    source = streamInfo.inputStream.source().buffer(),
                    fileSystem = options.fileSystem,
                ),
                mimeType = networkFile.mimeType.takeIf { it.isNotBlank() },
                dataSource = DataSource.NETWORK,
            )
        }
    }

    class Factory : Fetcher.Factory<NetworkFile> {
        override fun create(
            data: NetworkFile,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            return if (isSupportedPath(data.path)) {
                NetworkFileFetcher(data, options)
            } else {
                null
            }
        }

        private fun isSupportedPath(path: String): Boolean {
            val value = path.lowercase()
            return value.startsWith("smb://") ||
                value.startsWith("ftp://") ||
                value.startsWith("sftp://")
        }
    }

    companion object {
        // Limit dense-grid network image fetches to reduce memory/network spikes.
        private val networkFetchSemaphore = Semaphore(3)
    }
}

internal class NetworkFileKeyer : Keyer<NetworkFile> {
    override fun key(data: NetworkFile, options: Options): String {
        val remoteId = data.remote.id.ifBlank {
            "${data.remote.schema}://${data.remote.host}:${data.remote.port}/${data.remote.path}"
        }
        val modTime = data.modTime.ifBlank { "unknown" }
        return "network-file:$remoteId:${data.path}:$modTime:${data.size}"
    }
}
