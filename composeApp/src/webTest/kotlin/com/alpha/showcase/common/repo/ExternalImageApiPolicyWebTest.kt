package com.alpha.showcase.common.repo

import kotlin.test.Test
import kotlin.test.assertTrue

class ExternalImageApiPolicyWebTest {

    @Test
    fun webRequiresUserCredentialsForEveryExternalImageProvider() {
        assertTrue(shouldRequestUnsplashApiKey())
        assertTrue(shouldRequestPexelsApiKey(PexelsSourceType.FeedPhotos))
        assertTrue(shouldRequestTmdbApiToken())
    }
}
