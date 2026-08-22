package com.alpha.showcase.common.repo

import com.alpha.showcase.api.s3.S3Api
import com.alpha.showcase.api.s3.S3Connection
import com.alpha.showcase.api.s3.S3ListPage
import com.alpha.showcase.api.s3.S3ObjectItem
import com.alpha.showcase.api.s3.S3RequestFactory
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.utils.getMimeType

typealias S3PageLoader = suspend (S3Source, Boolean, String?) -> S3ListPage

internal const val S3_OBJECT_KEY = "s3Key"

class S3SourceRepo(
    private val pageLoader: S3PageLoader? = null,
) : SourceRepository<S3Source, NetworkFile>, BatchSourceRepository<S3Source, NetworkFile> {

    private val api by lazy { S3Api() }

    override suspend fun getItem(remoteApi: S3Source): Result<NetworkFile> =
        Result.failure(UnsupportedOperationException("Single S3 object retrieval is not supported"))

    override suspend fun getItems(
        remoteApi: S3Source,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?,
    ): Result<List<NetworkFile>> {
        val items = mutableListOf<NetworkFile>()
        return streamItems(remoteApi, recursive, filter, batchSize = 500) { batch ->
            items += batch
        }.map { items }
    }

    override suspend fun streamItems(
        remoteApi: S3Source,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?,
        batchSize: Int,
        onBatch: suspend (List<NetworkFile>) -> Unit,
    ): Result<Long> = runCatching {
        val effectiveBatchSize = batchSize.coerceAtLeast(1)
        var pending = mutableListOf<NetworkFile>()
        var continuationToken: String? = null
        var total = 0L

        do {
            val page = loadPage(remoteApi, recursive, continuationToken)
            val pageItems = buildList {
                addAll(page.objects.map { item -> item.toNetworkFile(remoteApi) })
                if (!recursive) {
                    addAll(page.commonPrefixes.map { prefix -> prefix.toDirectory(remoteApi) })
                }
            }
            pageItems.filter { item -> filter?.invoke(item) ?: true }.forEach { item ->
                pending += item
                total += 1
                if (pending.size >= effectiveBatchSize) {
                    onBatch(pending)
                    pending = mutableListOf()
                }
            }

            continuationToken = page.nextContinuationToken
            if (page.isTruncated && continuationToken.isNullOrBlank()) {
                error("S3 returned a truncated page without a continuation token")
            }
        } while (page.isTruncated)

        if (pending.isNotEmpty()) onBatch(pending)
        total
    }

    suspend fun checkConnection(remoteApi: S3Source): Result<Unit> =
        runCatching { loadPage(remoteApi, recursive = false, continuationToken = null) }.map { _ -> }

    private suspend fun loadPage(
        source: S3Source,
        recursive: Boolean,
        continuationToken: String?,
    ): S3ListPage = pageLoader?.invoke(source, recursive, continuationToken)
        ?: api.listObjectsPage(source.toConnection(), recursive, continuationToken)

    private fun S3ObjectItem.toNetworkFile(source: S3Source): NetworkFile {
        val directory = key.endsWith('/')
        val name = key.trimEnd('/').substringAfterLast('/').ifBlank { key }
        return NetworkFile(
            remote = source,
            path = key,
            fileName = name,
            isDirectory = directory,
            size = size,
            mimeType = if (directory) "application/octet-stream" else getMimeType(name),
            modTime = lastModified,
            extra = etag?.let { mapOf("etag" to it, S3_OBJECT_KEY to key) } ?: mapOf(S3_OBJECT_KEY to key),
        )
    }

    private fun String.toDirectory(source: S3Source): NetworkFile {
        val key = this
        return NetworkFile(
            remote = source,
            path = key,
            fileName = key.trimEnd('/').substringAfterLast('/'),
            isDirectory = true,
            size = 0L,
            mimeType = "application/octet-stream",
            modTime = "",
            extra = mapOf(S3_OBJECT_KEY to key),
        )
    }

}

internal fun presignS3ObjectUrl(source: S3Source, key: String): String =
    S3RequestFactory.presignObjectUrl(source.toBlockingConnection(), key)

internal suspend fun presignS3ObjectUrlAsync(source: S3Source, key: String): String =
    S3RequestFactory.presignObjectUrl(source.toConnection(), key)

private suspend fun S3Source.toConnection(): S3Connection = S3Connection(
    endpoint = endpoint,
    accessKey = accessKey,
    secretKey = RConfig.decryptAsync(secretKey),
    bucket = bucket,
    region = region,
    prefix = prefix,
    useSSL = useSSL,
)

private fun S3Source.toBlockingConnection(): S3Connection = S3Connection(
    endpoint = endpoint,
    accessKey = accessKey,
    secretKey = RConfig.decryptBlocking(secretKey),
    bucket = bucket,
    region = region,
    prefix = prefix,
    useSSL = useSSL,
)
