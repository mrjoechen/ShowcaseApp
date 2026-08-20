package com.alpha.showcase.common.repo

import com.alpha.showcase.api.rss.RssApi
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.RssSource
import com.alpha.showcase.common.utils.getMimeType

typealias RssFeedLoader = suspend (RssSource) -> List<String>

class RssSourceRepo(
    private val feedLoader: RssFeedLoader? = null,
) : SourceRepository<RssSource, NetworkFile>, BatchSourceRepository<RssSource, NetworkFile> {

    private val api by lazy { RssApi() }

    override suspend fun getItem(remoteApi: RssSource): Result<NetworkFile> =
        Result.failure(UnsupportedOperationException("Single RSS item retrieval is not supported"))

    override suspend fun getItems(
        remoteApi: RssSource,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?,
    ): Result<List<NetworkFile>> = runCatching {
        load(remoteApi).mapIndexed { index, url -> url.toNetworkFile(remoteApi, index) }
            .filter { item -> filter?.invoke(item) ?: true }
    }

    override suspend fun streamItems(
        remoteApi: RssSource,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?,
        batchSize: Int,
        onBatch: suspend (List<NetworkFile>) -> Unit,
    ): Result<Long> = getItems(remoteApi, recursive, filter).mapCatching { items ->
        items.chunked(batchSize.coerceAtLeast(1)).forEach { batch -> onBatch(batch) }
        items.size.toLong()
    }

    private suspend fun load(source: RssSource): List<String> =
        feedLoader?.invoke(source) ?: api.getImageUrls(source.url)

    private fun String.toNetworkFile(source: RssSource, index: Int): NetworkFile {
        val rawName = substringBefore('?').substringBefore('#').substringAfterLast('/').ifBlank {
            "rss-image-$index.jpg"
        }
        val detectedMime = getMimeType(rawName)
        val mime = if (detectedMime == "application/octet-stream") "image/jpeg" else detectedMime
        val fileName = if (rawName.contains('.')) rawName else "$rawName.jpg"
        return NetworkFile(
            remote = source,
            path = this,
            fileName = fileName,
            isDirectory = false,
            size = 0L,
            mimeType = mime,
            modTime = "",
        )
    }
}
