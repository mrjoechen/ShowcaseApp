package com.alpha.showcase.common.repo

import com.alpha.showcase.api.unsplash.UnsplashApi
import com.alpha.showcase.common.networkfile.storage.external.UnSplashSource
import com.alpha.showcase.common.ui.play.CONTENT_TYPE_IMAGE
import com.alpha.showcase.common.ui.play.DataWithType


class UnsplashRepo : SourceRepository<UnSplashSource, Any> {

    private val unsplashService by lazy {
        UnsplashApi()
    }

    override suspend fun getItem(remoteApi: UnSplashSource): Result<Any> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: UnSplashSource,
        recursive: Boolean,
        filter: ((Any) -> Boolean)?
    ): Result<List<Any>> {
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
                return Result.success(result.map { DataWithType(it.urls.regular!!, CONTENT_TYPE_IMAGE) })
            } else {
                Result.failure(Exception("No data!"))
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            Result.failure(ex)
        }
    }

}
