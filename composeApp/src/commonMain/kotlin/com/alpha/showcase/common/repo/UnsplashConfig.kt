package com.alpha.showcase.common.repo

import com.alpha.showcase.api.unsplash.UnsplashOrientation
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
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

internal data class UnsplashConfigDraft(
    val name: String,
    val photoType: UnSplashSourceType,
    val user: String = "",
    val collectionId: String = "",
    val topic: String = "",
    val orientation: String = UnsplashOrientation.All.storedValue,
    val apiKeyEdit: ExternalImageApiKeyEdit = ExternalImageApiKeyEdit(),
    val storeApiKey: Boolean = false,
) {
    fun toSource(encryptApiKey: (String) -> String): UnSplashSource {
        return UnSplashSource(
            name = name,
            photoType = photoType.type,
            user = user,
            collectionId = collectionId,
            topic = topic,
            orientation = orientation,
            apiKey = apiKeyEdit.valueForStorage(storeApiKey, encryptApiKey),
        )
    }
}

internal suspend fun UnSplashSource.resolveApiKey(
    decryptApiKey: suspend (String) -> String,
): String? {
    val storedApiKey = apiKey?.takeIf { it.isNotBlank() } ?: return null
    return decryptApiKey(storedApiKey)
}

internal fun shouldRequestUnsplashApiKey(): Boolean = externalImageApiRequiresUserCredentials


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

fun UnSplashSourceType.supportsOrientation(): Boolean {
    return this == UnSplashSourceType.UsersPhotos ||
        this == UnSplashSourceType.Collections ||
        this == UnSplashSourceType.TopicsPhotos
}
