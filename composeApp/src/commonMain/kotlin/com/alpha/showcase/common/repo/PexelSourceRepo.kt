package com.alpha.showcase.common.repo

import com.alpha.showcase.api.pexels.PexelsApi
import com.alpha.showcase.api.pexels.Photo
import com.alpha.showcase.api.pexels.Pagination
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.util.RConfig
import kotlinx.coroutines.yield


typealias PexelsPageLoader = suspend (PexelsSource, Int, Int) -> Pagination

internal sealed interface PexelsPageRequest {
    data object CuratedPhotos : PexelsPageRequest
    data class CollectionPhotos(
        val id: String,
        val apiKey: String?
    ) : PexelsPageRequest
}

internal fun PexelsSource.toPageRequest(
    decryptApiKey: (String) -> String,
): PexelsPageRequest {
    return when (PexelsSourceType.fromStoredType(photoType)) {
        PexelsSourceType.Collections -> {
            val id = extra[PEXELS_COLLECTION_ID_KEY].orEmpty()
            if (id.isBlank()) {
                PexelsPageRequest.CuratedPhotos
            } else {
                PexelsPageRequest.CollectionPhotos(id = id, apiKey = null)
            }
        }

        PexelsSourceType.MyCollection -> PexelsPageRequest.CollectionPhotos(
            id = extra[PEXELS_COLLECTION_ID_KEY].orEmpty(),
            apiKey = extra[PEXELS_API_KEY_KEY]?.let(decryptApiKey)
        )

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

    private val pexelsService by lazy {
        PexelsApi()
    }
    override suspend fun getItem(remoteApi: PexelsSource): Result<String> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: PexelsSource,
        recursive: Boolean,
        filter: ((String) -> Boolean)?
    ): Result<List<String>> {

        return try {
            val result = when (remoteApi.photoType) {
                PexelsSourceType.FeedPhotos.type -> {
                    loadPage(remoteApi, page = 1, perPage = DEFAULT_PER_PAGE)
                }

                else -> {
                    loadPage(remoteApi, page = 1, perPage = DEFAULT_PER_PAGE)
                }

            }

            if (result.photos.isNotEmpty()) {
                return Result.success(result.photos.map { it.src.original })
            } else {
                Result.failure(Exception("No data!"))
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
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

            for (page in 1..maxPages) {
                val pagination = loadPage(remoteApi, page, apiPageSize)
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

            Result.success(total)
        } catch (ex: Exception) {
            ex.printStackTrace()
            Result.failure(ex)
        }
    }

    private suspend fun loadPage(remoteApi: PexelsSource, page: Int, perPage: Int): Pagination {
        pageLoader?.let {
            return it(remoteApi, page, perPage)
        }
        return when (val request = remoteApi.toPageRequest(RConfig.decrypt)) {
            PexelsPageRequest.CuratedPhotos -> pexelsService.curatedPhotos(page = page, perPage = perPage)
            is PexelsPageRequest.CollectionPhotos -> {
                val api = request.apiKey?.let(::PexelsApi) ?: pexelsService
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
