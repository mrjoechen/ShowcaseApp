package com.alpha.showcase.common.repo

import com.alpha.showcase.api.pexels.PexelsApi
import com.alpha.showcase.api.pexels.Photo
import com.alpha.showcase.api.pexels.Pagination
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import kotlinx.coroutines.yield


typealias PexelsPageLoader = suspend (PexelsSource, Int, Int) -> Pagination

class PexelsSourceRepo(
    private val pageLoader: PexelsPageLoader? = null,
    private val maxPages: Int = DEFAULT_MAX_PAGES,
) : SourceRepository<PexelsSource, String>,
    BatchSourceRepository<PexelsSource, NetworkFile> {

    companion object {
        private const val DEFAULT_PER_PAGE = 80
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
            var total = 0L
            var pending = mutableListOf<NetworkFile>()

            for (page in 1..maxPages) {
                val pagination = loadPage(remoteApi, page, effectiveBatchSize)
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
        return pexelsService.curatedPhotos(page = page, perPage = perPage)
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
