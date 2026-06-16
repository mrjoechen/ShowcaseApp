package com.alpha.showcase.common.repo

import com.alpha.showcase.api.unsplash.Photo
import com.alpha.showcase.api.unsplash.UnsplashApi
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.ui.play.DataWithType
import io.ktor.http.Url
import kotlinx.coroutines.yield


typealias UnsplashPageLoader = suspend (UnSplashSource, Int, Int) -> List<Photo>

class UnsplashRepo(
    private val pageLoader: UnsplashPageLoader? = null,
    private val maxPages: Int = DEFAULT_MAX_PAGES,
) : SourceRepository<UnSplashSource, DataWithType>,
    BatchSourceRepository<UnSplashSource, NetworkFile> {

    companion object {
        private const val DEFAULT_PER_PAGE = 30
        private const val DEFAULT_MAX_PAGES = 100
    }

    private val unsplashService by lazy {
        UnsplashApi()
    }

    override suspend fun getItem(remoteApi: UnSplashSource): Result<DataWithType> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: UnSplashSource,
        recursive: Boolean,
        filter: ((DataWithType) -> Boolean)?
    ): Result<List<DataWithType>> {
        return try {
            val result = loadPage(remoteApi, page = 1, perPage = DEFAULT_PER_PAGE)

            if (result.isNotEmpty()) {
                return Result.success(result.map {
                    it.toDataWithType()
                })
            } else {
                Result.failure(Exception("No data!"))
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            Result.failure(ex)
        }
    }

    override suspend fun streamItems(
        remoteApi: UnSplashSource,
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
                val photos = loadPage(remoteApi, page, effectiveBatchSize)
                if (photos.isEmpty()) {
                    break
                }

                photos.mapNotNull { it.toNetworkFile(remoteApi) }
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

    private suspend fun loadPage(remoteApi: UnSplashSource, page: Int, perPage: Int): List<Photo> {
        pageLoader?.let {
            return it(remoteApi, page, perPage)
        }

        return when (remoteApi.photoType) {
            UnSplashSourceType.UsersPhotos.type -> {
                unsplashService.getUserPhotos(remoteApi.user, page = page, perPage = perPage)
            }

            UnSplashSourceType.UsersLiked.type -> {
                unsplashService.getUserLikes(remoteApi.user, page = page, perPage = perPage)
            }

            UnSplashSourceType.Collections.type -> {
                unsplashService.getCollectionPhotos(remoteApi.collectionId, page = page, perPage = perPage)
            }

            UnSplashSourceType.TopicsPhotos.type -> {
                unsplashService.getTopicPhotos(remoteApi.topic, page = page, perPage = perPage)
            }

            else -> {
                unsplashService.getFeedPhotos(page = page, perPage = perPage)
            }
        }
    }

    private fun Photo.toDataWithType(): DataWithType {
        val url = urls.regular ?: urls.full ?: urls.raw ?: urls.small ?: urls.thumb
        return DataWithType(
            url ?: "",
            url?.let { Url(it).parameters["fm"] } ?: "jpg",
        )
    }

    private fun Photo.toNetworkFile(remoteApi: UnSplashSource): NetworkFile? {
        val data = toDataWithType()
        val url = data.data as? String ?: return null
        if (url.isBlank()) return null
        return NetworkFile(
            remote = remoteApi,
            path = url,
            fileName = "$id.${data.type}",
            isDirectory = false,
            size = 0L,
            mimeType = "image/${data.type}",
            modTime = createdAt ?: updatedAt ?: promotedAt ?: "",
        )
    }

}
