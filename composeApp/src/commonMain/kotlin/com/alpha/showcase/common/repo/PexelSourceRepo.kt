package com.alpha.showcase.common.repo

import com.alpha.showcase.api.pexels.Photo
import com.alpha.showcase.api.pexels.Pagination
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.util.RConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield


typealias PexelsPageLoader = suspend (PexelsSource, Int, Int) -> Pagination

internal sealed interface PexelsPageRequest {
    data object CuratedPhotos : PexelsPageRequest
    data class CollectionPhotos(val id: String) : PexelsPageRequest
}

internal fun PexelsSource.toPageRequest(): PexelsPageRequest {
    return when (PexelsSourceType.fromStoredType(photoType)) {
        PexelsSourceType.Collections -> {
            val id = extra[PEXELS_COLLECTION_ID_KEY].orEmpty()
            if (id.isBlank()) {
                PexelsPageRequest.CuratedPhotos
            } else {
                PexelsPageRequest.CollectionPhotos(id)
            }
        }

        PexelsSourceType.MyCollection ->
            PexelsPageRequest.CollectionPhotos(extra[PEXELS_COLLECTION_ID_KEY].orEmpty())

        PexelsSourceType.FeedPhotos -> PexelsPageRequest.CuratedPhotos
    }
}

class PexelsSourceRepo(
    private val pageLoader: PexelsPageLoader? = null,
    private val maxPages: Int = DEFAULT_MAX_PAGES,
) : SourceRepository<PexelsSource, String>,
    BatchSourceRepository<PexelsSource, NetworkFile> {

    companion object {
        private const val MAX_API_PER_PAGE = 80
        private const val DEFAULT_PER_PAGE = MAX_API_PER_PAGE
        private const val DEFAULT_MAX_PAGES = 100
    }

    private val pexelsService by lazy { createPexelsApi() }
    override suspend fun getItem(remoteApi: PexelsSource): Result<String> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: PexelsSource,
        recursive: Boolean,
        filter: ((String) -> Boolean)?
    ): Result<List<String>> {
        val items = mutableListOf<String>()
        val streamResult = streamItems(
            remoteApi = remoteApi,
            recursive = recursive,
            batchSize = DEFAULT_PER_PAGE,
        ) { batch ->
            items += batch.map { it.path }
        }

        streamResult.exceptionOrNull()?.let { return Result.failure(it) }
        val filteredItems = items.filter { filter?.invoke(it) ?: true }
        return if (filteredItems.isNotEmpty()) {
            Result.success(filteredItems)
        } else {
            Result.failure(Exception("No data!"))
        }
    }

    suspend fun checkConnection(remoteApi: PexelsSource): Result<Unit> {
        return try {
            Result.success(loadPage(remoteApi, page = 1, perPage = DEFAULT_PER_PAGE)).map { _ -> }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Throwable) {
            Result.failure(ex)
        }
    }

    override suspend fun streamItems(
        remoteApi: PexelsSource,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?,
        batchSize: Int,
        onBatch: suspend (List<NetworkFile>) -> Unit
    ): Result<Long> {
        return try {
            val effectiveBatchSize = batchSize.coerceAtLeast(1)
            val apiPageSize = effectiveBatchSize.coerceAtMost(MAX_API_PER_PAGE)
            var total = 0L
            var pending = mutableListOf<NetworkFile>()
            var terminalFailure: Exception? = null

            for (page in 1..maxPages) {
                val pagination = try {
                    loadPage(remoteApi, page, apiPageSize)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    terminalFailure = ex
                    break
                }
                if (pagination.photos.isEmpty()) {
                    break
                }

                pagination.photos.map { it.toNetworkFile(remoteApi) }
                    .filter { filter?.invoke(it) ?: true }
                    .forEach { item ->
                        pending += item
                        total += 1
                        if (pending.size >= effectiveBatchSize) {
                            onBatch(pending)
                            pending = mutableListOf()
                            yield()
                        }
                    }

                if (pagination.nextPage.isNullOrBlank()) {
                    break
                }
            }

            if (pending.isNotEmpty()) {
                onBatch(pending)
            }

            terminalFailure?.let { Result.failure(it) } ?: Result.success(total)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            ex.printStackTrace()
            Result.failure(ex)
        }
    }

    private suspend fun loadPage(remoteApi: PexelsSource, page: Int, perPage: Int): Pagination {
        pageLoader?.let {
            return it(remoteApi, page, perPage)
        }
        val configuredApiKey = remoteApi.resolveApiKey { RConfig.decryptAsync(it) }
        val api = configuredApiKey?.let(::createPexelsApi) ?: pexelsService
        return when (val request = remoteApi.toPageRequest()) {
            PexelsPageRequest.CuratedPhotos -> api.curatedPhotos(page = page, perPage = perPage)
            is PexelsPageRequest.CollectionPhotos -> {
                val result = api.collectionPhotos(
                    id = request.id,
                    page = page,
                    perPage = perPage
                )
                Pagination(
                    nextPage = result.nextPage,
                    page = result.page,
                    perPage = result.perPage,
                    photos = result.media
                )
            }
        }
    }

    private fun Photo.toNetworkFile(remoteApi: PexelsSource): NetworkFile {
        return NetworkFile(
            remote = remoteApi,
            path = src.original,
            fileName = "$id.jpg",
            isDirectory = false,
            size = 0L,
            mimeType = "image/jpeg",
            modTime = "",
        )
    }

}
