package com.alpha.showcase.common.repo

import com.alpha.showcase.api.unsplash.UnsplashApi
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.ui.play.DataWithType
import io.ktor.http.Url


class UnsplashRepo : SourceRepository<UnSplashSource, DataWithType> {

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
            val result = when (remoteApi.photoType) {
                UnSplashSourceType.UsersPhotos.type -> {
                    unsplashService.getUserPhotos(remoteApi.user)
                }

                UnSplashSourceType.UsersLiked.type -> {
                    unsplashService.getUserLikes(remoteApi.user)
                }

//            UnSplashSourceType.UsersCollection.type -> {
//                unsplashService.getUserCollections(remoteApi.user)
//
//            }

                UnSplashSourceType.Collections.type -> {
                    unsplashService.getCollectionPhotos(remoteApi.collectionId)
                }

                UnSplashSourceType.TopicsPhotos.type -> {
                    unsplashService.getTopicPhotos(remoteApi.topic)
                }

                else -> {
                    unsplashService.getFeedPhotos()
                }

            }

            if (result.isNotEmpty()) {
                return Result.success(result.map {
                    DataWithType(
                        it.urls.regular!!,
                        Url(it.urls.regular!!).parameters["fm"] ?: "jpg",
                    )
                })
            } else {
                Result.failure(Exception("No data!"))
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            Result.failure(ex)
        }
    }

}
