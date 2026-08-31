package com.alpha.showcase.common.repo

import com.alpha.showcase.api.unsplash.Photo
import com.alpha.showcase.api.unsplash.UnsplashOrientation
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.ui.play.DataWithType
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield


typealias UnsplashPageLoader = suspend (UnSplashSource, Int, Int) -> List<Photo>

internal sealed interface UnsplashPageRequest {
    data class UserPhotos(
        val username: String,
        val orientation: UnsplashOrientation
    ) : UnsplashPageRequest

    data class UserLikes(val username: String) : UnsplashPageRequest

    data class CollectionPhotos(
        val id: String,
        val orientation: UnsplashOrientation
    ) : UnsplashPageRequest

    data class TopicPhotos(
        val idOrSlug: String,
        val orientation: UnsplashOrientation
    ) : UnsplashPageRequest

    data object FeedPhotos : UnsplashPageRequest
}

internal fun UnSplashSource.toPageRequest(): UnsplashPageRequest {
    val orientation = UnsplashOrientation.fromStoredValue(orientation)
    return when (photoType) {
        UnSplashSourceType.UsersPhotos.type -> UnsplashPageRequest.UserPhotos(user, orientation)
        UnSplashSourceType.UsersLiked.type -> UnsplashPageRequest.UserLikes(user)
        UnSplashSourceType.Collections.type -> UnsplashPageRequest.CollectionPhotos(collectionId, orientation)
        UnSplashSourceType.TopicsPhotos.type -> UnsplashPageRequest.TopicPhotos(topic, orientation)
        else -> UnsplashPageRequest.FeedPhotos
    }
}

class UnsplashRepo(
    private val pageLoader: UnsplashPageLoader? = null,
    private val maxPages: Int = DEFAULT_MAX_PAGES,
) : SourceRepository<UnSplashSource, DataWithType>,
    BatchSourceRepository<UnSplashSource, NetworkFile> {

    companion object {
        private const val DEFAULT_PER_PAGE = 30
        private const val DEFAULT_MAX_PAGES = 100
    }

    private val unsplashService by lazy { createUnsplashApi() }

    override suspend fun getItem(remoteApi: UnSplashSource): Result<DataWithType> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: UnSplashSource,
        recursive: Boolean,
        filter: ((DataWithType) -> Boolean)?
    ): Result<List<DataWithType>> {
        val items = mutableListOf<DataWithType>()
        val streamResult = streamItems(
            remoteApi = remoteApi,
            recursive = recursive,
            batchSize = DEFAULT_PER_PAGE,
        ) { batch ->
            items += batch.map { file ->
                DataWithType(
                    data = file.path,
                    type = file.mimeType.substringAfter("image/", "jpg"),
                )
            }
        }

        streamResult.exceptionOrNull()?.let { return Result.failure(it) }
        val filteredItems = items.filter { filter?.invoke(it) ?: true }
        return if (filteredItems.isNotEmpty()) {
            Result.success(filteredItems)
        } else {
            Result.failure(Exception("No data!"))
        }
    }

    suspend fun checkConnection(remoteApi: UnSplashSource): Result<Unit> {
        return try {
            Result.success(loadPage(remoteApi, page = 1, perPage = DEFAULT_PER_PAGE)).map { _ -> }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Throwable) {
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
            var terminalFailure: Exception? = null

            for (page in 1..maxPages) {
                val photos = try {
                    loadPage(remoteApi, page, effectiveBatchSize)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    terminalFailure = ex
                    break
                }
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

            terminalFailure?.let { Result.failure(it) } ?: Result.success(total)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            ex.printStackTrace()
            Result.failure(ex)
        }
    }

    private suspend fun loadPage(remoteApi: UnSplashSource, page: Int, perPage: Int): List<Photo> {
        pageLoader?.let {
            return it(remoteApi, page, perPage)
        }

        val api = remoteApi.resolveApiKey { RConfig.decryptAsync(it) }
            ?.let(::createUnsplashApi)
            ?: unsplashService

        return when (val request = remoteApi.toPageRequest()) {
            is UnsplashPageRequest.UserPhotos -> api.getUserPhotos(
                username = request.username,
                page = page,
                perPage = perPage,
                orientation = request.orientation
            )

            is UnsplashPageRequest.UserLikes -> api.getUserLikes(
                username = request.username,
                page = page,
                perPage = perPage
            )

            is UnsplashPageRequest.CollectionPhotos -> api.getCollectionPhotos(
                id = request.id,
                page = page,
                perPage = perPage,
                orientation = request.orientation
            )

            is UnsplashPageRequest.TopicPhotos -> api.getTopicPhotos(
                idOrSlug = request.idOrSlug,
                page = page,
                perPage = perPage,
                orientation = request.orientation
            )

            UnsplashPageRequest.FeedPhotos -> api.getFeedPhotos(page = page, perPage = perPage)
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
