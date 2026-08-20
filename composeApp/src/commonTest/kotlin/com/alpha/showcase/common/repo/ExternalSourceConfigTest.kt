package com.alpha.showcase.common.repo

import com.alpha.showcase.api.unsplash.UnsplashOrientation
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalSourceConfigTest {

    @Test
    fun legacyRemoteSourcesReceiveBackwardCompatibleDefaults() {
        val pexels = StorageSourceSerializer.sourceJson.decodeFromString<PexelsSource>(
            """{"name":"Featured","photoType":"Hot Collection"}"""
        )
        val unsplash = StorageSourceSerializer.sourceJson.decodeFromString<UnSplashSource>(
            """{"name":"Wallpapers","photoType":"Topic's Photos","topic":"wallpapers"}"""
        )

        assertTrue(pexels.extra.isEmpty())
        assertEquals(UnsplashOrientation.All.storedValue, unsplash.orientation)
    }

    @Test
    fun legacyPexelsHotCollectionResolvesToFeaturedCollection() {
        assertEquals(
            PexelsSourceType.Collections,
            PexelsSourceType.fromStoredType("Hot Collection")
        )
    }

    @Test
    fun unsplashOrientationIsOfferedOnlyForApiSupportedPhotoTypes() {
        assertTrue(UnSplashSourceType.UsersPhotos.supportsOrientation())
        assertTrue(UnSplashSourceType.Collections.supportsOrientation())
        assertTrue(UnSplashSourceType.TopicsPhotos.supportsOrientation())
        assertTrue(!UnSplashSourceType.UsersLiked.supportsOrientation())
        assertTrue(!UnSplashSourceType.FeedPhotos.supportsOrientation())
    }

    @Test
    fun unsplashPageRequestIncludesOrientationOnlyForSupportedTypes() {
        val portrait = UnsplashOrientation.Portrait.storedValue

        assertEquals(
            UnsplashOrientation.Portrait,
            assertIs<UnsplashPageRequest.UserPhotos>(
                UnSplashSource("User", USERS_PHOTOS, user = "alice", orientation = portrait)
                    .toPageRequest()
            ).orientation
        )
        assertEquals(
            UnsplashOrientation.Portrait,
            assertIs<UnsplashPageRequest.CollectionPhotos>(
                UnSplashSource("Collection", COLLECTION_PHOTOS, collectionId = "123", orientation = portrait)
                    .toPageRequest()
            ).orientation
        )
        assertEquals(
            UnsplashOrientation.Portrait,
            assertIs<UnsplashPageRequest.TopicPhotos>(
                UnSplashSource("Topic", TOPICS_PHOTOS, topic = "wallpapers", orientation = portrait)
                    .toPageRequest()
            ).orientation
        )
        assertIs<UnsplashPageRequest.UserLikes>(
            UnSplashSource("Likes", USERS_LIKED_PHOTOS, user = "alice", orientation = portrait)
                .toPageRequest()
        )
        assertIs<UnsplashPageRequest.FeedPhotos>(
            UnSplashSource("Feed", FEED_PHOTOS, orientation = portrait).toPageRequest()
        )
    }

    @Test
    fun pexelsPageRequestDecryptsOnlyPersonalCollectionCredential() {
        val decryptApiKey: (String) -> String = { value -> value.removePrefix("encrypted:") }
        val featured = PexelsSource(
            name = "Featured",
            photoType = PexelsSourceType.Collections.type,
            extra = mapOf(PEXELS_COLLECTION_ID_KEY to "featured-id")
        ).toPageRequest(decryptApiKey)
        val personal = PexelsSource(
            name = "Mine",
            photoType = PexelsSourceType.MyCollection.type,
            extra = mapOf(
                PEXELS_COLLECTION_ID_KEY to "personal-id",
                PEXELS_API_KEY_KEY to "encrypted:secret"
            )
        ).toPageRequest(decryptApiKey)

        assertEquals("featured-id", assertIs<PexelsPageRequest.CollectionPhotos>(featured).id)
        assertNull(featured.apiKey)
        assertEquals("personal-id", assertIs<PexelsPageRequest.CollectionPhotos>(personal).id)
        assertEquals("secret", personal.apiKey)
    }

    @Test
    fun pexelsConfigDraftEncryptsPersonalApiKeyBeforeBuildingSource() {
        val source = PexelsConfigDraft(
            name = "Mine",
            photoType = PexelsSourceType.MyCollection,
            collectionId = "personal-id",
            apiKey = "secret",
        ).toSource { value -> "encrypted:$value" }

        assertEquals("encrypted:secret", source.extra[PEXELS_API_KEY_KEY])
    }

    @Test
    fun pexelsConfigDraftMigratesUnchangedLegacyApiKeyWhenEditing() {
        val source = PexelsConfigDraft(
            name = "Mine",
            photoType = PexelsSourceType.MyCollection,
            collectionId = "personal-id",
            existingStoredApiKey = "legacy-secret",
            apiKeyChanged = false,
        ).toSource { value -> "encrypted:$value" }

        assertEquals("encrypted:legacy-secret", source.extra[PEXELS_API_KEY_KEY])
    }

    @Test
    fun legacyPexelsApiKeyIsEncryptedDuringSensitiveFieldMigration() {
        val legacy = PexelsSource(
            name = "Mine",
            photoType = PexelsSourceType.MyCollection.type,
            extra = mapOf(
                PEXELS_COLLECTION_ID_KEY to "personal-id",
                PEXELS_API_KEY_KEY to "legacy-secret",
            ),
        )

        val normalized = legacy.withEncryptedApiKey { value -> "encrypted:$value" }

        assertEquals("encrypted:legacy-secret", normalized.extra[PEXELS_API_KEY_KEY])
    }

    @Test
    fun legacyPexelsHotCollectionWithoutAnIdKeepsCuratedFeedBehavior() {
        val request = PexelsSource(
            name = "Legacy",
            photoType = PEXELS_HOT_COLLECTION
        ).toPageRequest { it }

        assertIs<PexelsPageRequest.CuratedPhotos>(request)
    }
}
