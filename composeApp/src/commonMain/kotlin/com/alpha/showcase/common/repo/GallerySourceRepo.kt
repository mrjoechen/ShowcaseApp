package com.alpha.showcase.common.repo

import com.alpha.showcase.common.cache.GallerySourceMediaStore
import com.alpha.showcase.common.networkfile.storage.remote.GallerySource
import com.alpha.showcase.common.ui.play.DataWithType
import com.alpha.showcase.common.ui.source.toGalleryDisplayUri

class GallerySourceRepo(
    private val mediaStore: GallerySourceMediaStore = GallerySourceMediaStore()
) : SourceRepository<GallerySource, DataWithType> {

    override suspend fun getItem(remoteApi: GallerySource): Result<DataWithType> {
        return Result.failure(UnsupportedOperationException("Not supported"))
    }

    override suspend fun getItems(
        remoteApi: GallerySource,
        recursive: Boolean,
        filter: ((DataWithType) -> Boolean)?
    ): Result<List<DataWithType>> {
        return runCatching {
            val records = mediaStore.listMedias(remoteApi.name)
            val items = records.map {
                DataWithType(
                    data = toGalleryDisplayUri(it.mediaUri),
                    type = it.mimeType,
                    extra = mapOf("displayName" to it.displayName)
                )
            }
            filter?.let { items.filter(it) } ?: items
        }
    }
}
