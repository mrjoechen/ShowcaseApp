package com.alpha.showcase.common.repo

import org.jetbrains.compose.resources.StringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.unsplash_collections_photos
import showcaseapp.composeapp.generated.resources.unsplash_feed_photos

val PexelsTypes = listOf(
    PexelsSourceType.FeedPhotos,
    PexelsSourceType.HotCollections
)

const val PEXELS_FEED_PHOTOS = "Feed Photo"
const val PEXELS_HOT_COLLECTION = "Hot Collection"

sealed class PexelsSourceType(val type: String, val titleRes: StringResource) {
    data object FeedPhotos : UnSplashSourceType(PEXELS_FEED_PHOTOS, Res.string.unsplash_feed_photos)
    data object HotCollections : UnSplashSourceType(PEXELS_HOT_COLLECTION, Res.string.unsplash_collections_photos)
}