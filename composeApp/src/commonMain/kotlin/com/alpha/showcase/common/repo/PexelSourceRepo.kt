package com.alpha.showcase.common.repo

import com.alpha.showcase.api.pexels.PexelsApi
import com.alpha.showcase.api.pexels.Photo
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource


class PexelsSourceRepo : SourceRepository<PexelsSource, String> {

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

                    val photoList = mutableListOf<Photo>()
                    var pagination = pexelsService.curatedPhotos()
                    repeat(5){
                        photoList.addAll(pagination.photos)
                        pagination = pexelsService.curatedNextPagePhotos(pagination)
                    }
                    pagination.copy(photos = photoList)
                }

                else -> {
                    pexelsService.curatedPhotos()
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

}
