package com.alpha.showcase.common.repo

import com.alpha.showcase.api.unsplash.UnsplashOrientation
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.storage.remote.TMDBSource
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
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
        assertNull(unsplash.apiKey)
    }

    @Test
    fun legacyTmdbSourceDecodesWithoutAnApiToken() {
        val tmdb = StorageSourceSerializer.sourceJson.decodeFromString<TMDBSource>(
            """{"name":"Popular","contentType":"Popular","language":"en-US","region":"US","imageType":"Poster"}"""
        )

        assertNull(tmdb.apiToken)
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
    fun pexelsPageRequestContainsOnlyCollectionIdentity() {
        val featured = PexelsSource(
            name = "Featured",
            photoType = PexelsSourceType.Collections.type,
            extra = mapOf(PEXELS_COLLECTION_ID_KEY to "featured-id")
        ).toPageRequest()
        val personal = PexelsSource(
            name = "Mine",
            photoType = PexelsSourceType.MyCollection.type,
            extra = mapOf(
                PEXELS_COLLECTION_ID_KEY to "personal-id",
                PEXELS_API_KEY_KEY to "encrypted:secret"
            )
        ).toPageRequest()

        assertEquals("featured-id", assertIs<PexelsPageRequest.CollectionPhotos>(featured).id)
        assertEquals("personal-id", assertIs<PexelsPageRequest.CollectionPhotos>(personal).id)
    }

    @Test
    fun pexelsConfigDraftEncryptsPersonalApiKeyBeforeBuildingSource() {
        val source = PexelsConfigDraft(
            name = "Mine",
            photoType = PexelsSourceType.MyCollection,
            collectionId = "personal-id",
            apiKeyEdit = ExternalImageApiKeyEdit(input = "secret"),
        ).toSource { value -> "encrypted:$value" }

        assertEquals("encrypted:secret", source.extra[PEXELS_API_KEY_KEY])
    }

    @Test
    fun tmdbConfigDraftEncryptsNewApiToken() {
        val source = TmdbConfigDraft(
            name = "Popular",
            contentType = POPULAR_MOVIES,
            language = Language.ENGLISH_US.value,
            region = Region.US.value,
            imageType = ImageType.POSTER.value,
            apiTokenEdit = ExternalImageApiKeyEdit(input = "secret"),
            storeApiToken = true,
        ).toSource { value -> "encrypted:$value" }

        assertEquals("encrypted:secret", source.apiToken)
    }

    @Test
    fun tmdbConfigDraftPreservesStoredApiTokenWhenTheFieldIsHidden() {
        val source = TmdbConfigDraft(
            name = "Popular",
            contentType = POPULAR_MOVIES,
            language = Language.ENGLISH_US.value,
            region = Region.US.value,
            imageType = ImageType.POSTER.value,
            apiTokenEdit = ExternalImageApiKeyEdit(
                existingStoredValue = "encrypted:secret",
                changed = false,
            ),
            storeApiToken = false,
        ).toSource {
            error("An unchanged stored API token must not be encrypted again")
        }

        assertEquals("encrypted:secret", source.apiToken)
    }

    @Test
    fun tmdbConfigDraftDoesNotStoreNewApiTokenWhenTheFieldIsHidden() {
        val source = TmdbConfigDraft(
            name = "Popular",
            contentType = POPULAR_MOVIES,
            language = Language.ENGLISH_US.value,
            region = Region.US.value,
            imageType = ImageType.POSTER.value,
            apiTokenEdit = ExternalImageApiKeyEdit(input = "new-secret"),
            storeApiToken = false,
        ).toSource {
            error("A hidden new API token must not be encrypted")
        }

        assertNull(source.apiToken)
    }

    @Test
    fun webPexelsConfigStoresApiKeyForCuratedPhotos() {
        val source = PexelsConfigDraft(
            name = "Curated",
            photoType = PexelsSourceType.FeedPhotos,
            apiKeyEdit = ExternalImageApiKeyEdit(input = "secret"),
            storeApiKey = true,
        ).toSource { value -> "encrypted:$value" }

        assertEquals("encrypted:secret", source.extra[PEXELS_API_KEY_KEY])
    }

    @Test
    fun nativePexelsConfigDoesNotStoreApiKeyForCuratedPhotos() {
        val source = PexelsConfigDraft(
            name = "Curated",
            photoType = PexelsSourceType.FeedPhotos,
            apiKeyEdit = ExternalImageApiKeyEdit(input = "secret"),
            storeApiKey = false,
        ).toSource { value -> "encrypted:$value" }

        assertNull(source.extra[PEXELS_API_KEY_KEY])
    }

    @Test
    fun nativePexelsConfigPreservesStoredWebApiKeyForCuratedPhotos() {
        val source = PexelsConfigDraft(
            name = "Curated",
            photoType = PexelsSourceType.FeedPhotos,
            apiKeyEdit = ExternalImageApiKeyEdit(
                existingStoredValue = "encrypted:secret",
                changed = false,
            ),
            storeApiKey = false,
        ).toSource {
            error("A hidden stored API key must not be encrypted again")
        }

        assertEquals("encrypted:secret", source.extra[PEXELS_API_KEY_KEY])
    }

    @Test
    fun webUnsplashConfigStoresApiKey() {
        val source = UnsplashConfigDraft(
            name = "Wallpapers",
            photoType = UnSplashSourceType.FeedPhotos,
            apiKeyEdit = ExternalImageApiKeyEdit(input = "secret"),
            storeApiKey = true,
        ).toSource { value -> "encrypted:$value" }

        assertEquals("encrypted:secret", source.apiKey)
    }

    @Test
    fun nativeUnsplashConfigPreservesStoredWebApiKey() {
        val source = UnsplashConfigDraft(
            name = "Wallpapers",
            photoType = UnSplashSourceType.FeedPhotos,
            apiKeyEdit = ExternalImageApiKeyEdit(
                existingStoredValue = "encrypted:secret",
                changed = false,
            ),
            storeApiKey = false,
        ).toSource {
            error("A hidden stored API key must not be encrypted again")
        }

        assertEquals("encrypted:secret", source.apiKey)
    }

    @Test
    fun nativeUnsplashConfigDoesNotStoreNewHiddenApiKey() {
        val source = UnsplashConfigDraft(
            name = "Wallpapers",
            photoType = UnSplashSourceType.FeedPhotos,
            apiKeyEdit = ExternalImageApiKeyEdit(input = "new-secret"),
            storeApiKey = false,
        ).toSource {
            error("A hidden new API key must not be encrypted")
        }

        assertNull(source.apiKey)
    }

    @Test
    fun configuredApiKeysAreDecryptedForAllProviderRequests() = runTest {
        val decryptApiKey: (String) -> String = { value -> value.removePrefix("encrypted:") }
        val unsplash = UnSplashSource(
            name = "Wallpapers",
            photoType = FEED_PHOTOS,
            apiKey = "encrypted:unsplash-secret",
        )
        val pexels = PexelsSource(
            name = "Curated",
            photoType = PEXELS_FEED_PHOTOS,
            extra = mapOf(PEXELS_API_KEY_KEY to "encrypted:pexels-secret"),
        )

        assertEquals("unsplash-secret", unsplash.resolveApiKey(decryptApiKey))
        assertEquals("pexels-secret", pexels.resolveApiKey(decryptApiKey))
    }

    @Test
    fun configuredTmdbApiTokenIsDecryptedForRequests() = runTest {
        val tmdb = TMDBSource(
            name = "Popular",
            contentType = POPULAR_MOVIES,
            language = Language.ENGLISH_US.value,
            region = Region.US.value,
            imageType = ImageType.POSTER.value,
            apiToken = "encrypted:secret",
        )

        assertEquals(
            "secret",
            tmdb.resolveApiToken { value -> value.removePrefix("encrypted:") },
        )
    }

    @Test
    fun webProviderClientsRejectMissingConfiguredApiKeys() {
        assertFailsWith<IllegalArgumentException> {
            requireConfiguredProviderApiKey("Unsplash", null)
        }
        assertFailsWith<IllegalArgumentException> {
            requireConfiguredProviderApiKey("Pexels", "  ")
        }
        assertEquals(
            "user-secret",
            requireConfiguredProviderApiKey("Unsplash", " user-secret "),
        )
    }

    @Test
    fun pexelsConfigDraftPreservesUnchangedStoredApiKeyWhenEditing() {
        val source = PexelsConfigDraft(
            name = "Mine",
            photoType = PexelsSourceType.MyCollection,
            collectionId = "personal-id",
            apiKeyEdit = ExternalImageApiKeyEdit(
                existingStoredValue = "encrypted:secret",
                changed = false,
            ),
        ).toSource {
            error("An unchanged stored API key must not be encrypted again")
        }

        assertEquals("encrypted:secret", source.extra[PEXELS_API_KEY_KEY])
    }

    @Test
    fun legacyPexelsHotCollectionWithoutAnIdKeepsCuratedFeedBehavior() {
        val request = PexelsSource(
            name = "Legacy",
            photoType = PEXELS_HOT_COLLECTION
        ).toPageRequest()

        assertIs<PexelsPageRequest.CuratedPhotos>(request)
    }
}
