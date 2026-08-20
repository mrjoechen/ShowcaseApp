package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import org.jetbrains.compose.resources.StringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.featured_collections_photos
import showcaseapp.composeapp.generated.resources.my_collections_photos
import showcaseapp.composeapp.generated.resources.unsplash_feed_photos

val PexelsTypes = listOf(
    PexelsSourceType.FeedPhotos,
    PexelsSourceType.Collections,
    PexelsSourceType.MyCollection
)

const val PEXELS_FEED_PHOTOS = "Feed Photo"
const val PEXELS_HOT_COLLECTION = "Hot Collection"
const val PEXELS_COLLECTION = "Collection"
const val PEXELS_MY_COLLECTION = "My Collections"

const val PEXELS_COLLECTION_ID_KEY = "id"
const val PEXELS_API_KEY_KEY = "ApiKey"

internal data class PexelsConfigDraft(
    val name: String,
    val photoType: PexelsSourceType,
    val collectionId: String = "",
    val apiKey: String = "",
    val existingStoredApiKey: String? = null,
    val apiKeyChanged: Boolean = true,
) {
    fun toSource(encryptApiKey: (String) -> String): PexelsSource {
        val extra = when (photoType) {
            PexelsSourceType.Collections -> mapOf(PEXELS_COLLECTION_ID_KEY to collectionId)
            PexelsSourceType.MyCollection -> mapOf(
                PEXELS_COLLECTION_ID_KEY to collectionId,
                PEXELS_API_KEY_KEY to when {
                    apiKeyChanged -> encryptApiKey(apiKey)
                    !existingStoredApiKey.isNullOrBlank() -> encryptApiKey(existingStoredApiKey)
                    else -> ""
                },
            )
            PexelsSourceType.FeedPhotos -> emptyMap()
        }
        return PexelsSource(name = name, photoType = photoType.type, extra = extra)
    }
}

internal fun PexelsSource.withEncryptedApiKey(
    encryptApiKey: (String) -> String,
): PexelsSource {
    val storedApiKey = extra[PEXELS_API_KEY_KEY] ?: return this
    val encryptedApiKey = encryptApiKey(storedApiKey)
    if (encryptedApiKey == storedApiKey) return this
    return PexelsSource(
        name = name,
        photoType = photoType,
        extra = extra + (PEXELS_API_KEY_KEY to encryptedApiKey),
    )
}

sealed class PexelsSourceType(val type: String, val titleRes: StringResource) {
    data object FeedPhotos : PexelsSourceType(PEXELS_FEED_PHOTOS, Res.string.unsplash_feed_photos)
    data object Collections : PexelsSourceType(PEXELS_COLLECTION, Res.string.featured_collections_photos)
    data object MyCollection : PexelsSourceType(PEXELS_MY_COLLECTION, Res.string.my_collections_photos)

    companion object {
        fun fromStoredType(value: String?): PexelsSourceType {
            return when (value) {
                PEXELS_COLLECTION, PEXELS_HOT_COLLECTION -> Collections
                PEXELS_MY_COLLECTION -> MyCollection
                else -> FeedPhotos
            }
        }
    }
}
