package com.alpha.showcase.common.repo

import org.jetbrains.compose.resources.StringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.unsplash_collections_photos
import showcaseapp.composeapp.generated.resources.unsplash_feed_photos
import showcaseapp.composeapp.generated.resources.unsplash_topics_photos
import showcaseapp.composeapp.generated.resources.unsplash_users_collections
import showcaseapp.composeapp.generated.resources.unsplash_users_likes
import showcaseapp.composeapp.generated.resources.unsplash_users_photos

val Types = listOf(
    UnSplashSourceType.UsersPhotos,
    UnSplashSourceType.UsersLiked,
//    UnSplashSourceType.UsersCollection,
    UnSplashSourceType.Collections,
    UnSplashSourceType.TopicsPhotos,
    UnSplashSourceType.FeedPhotos
)


const val USERS_PHOTOS = "User's Photos"
const val USERS_LIKED_PHOTOS = "User's Liked Photos"
const val USERS_COLLECTIONS = "User's Collections"
const val COLLECTION_PHOTOS = "Collection's Photos"
const val TOPICS_PHOTOS = "Topic's Photos"
const val FEED_PHOTOS = "Feed Photo"


sealed class UnSplashSourceType(val type: String, val titleRes: StringResource) {
    data object UsersPhotos : UnSplashSourceType(USERS_PHOTOS, Res.string.unsplash_users_photos)
    data object UsersLiked : UnSplashSourceType(USERS_LIKED_PHOTOS, Res.string.unsplash_users_likes)
    data object UsersCollection :
        UnSplashSourceType(USERS_COLLECTIONS, Res.string.unsplash_users_collections)

    data object Collections :
        UnSplashSourceType(COLLECTION_PHOTOS, Res.string.unsplash_collections_photos)

    data object TopicsPhotos : UnSplashSourceType(TOPICS_PHOTOS, Res.string.unsplash_topics_photos)
    data object FeedPhotos : UnSplashSourceType(FEED_PHOTOS, Res.string.unsplash_feed_photos)

}