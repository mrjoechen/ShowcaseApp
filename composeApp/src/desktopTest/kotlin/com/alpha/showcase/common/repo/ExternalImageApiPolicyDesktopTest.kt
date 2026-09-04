package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExternalImageApiPolicyDesktopTest {

    @Test
    fun nativeUsesBundledCredentialsExceptForPersonalPexelsCollections() {
        assertFalse(shouldRequestUnsplashApiKey())
        assertFalse(shouldRequestPexelsApiKey(PexelsSourceType.FeedPhotos))
        assertTrue(shouldRequestPexelsApiKey(PexelsSourceType.MyCollection))
        assertFalse(shouldRequestTmdbApiToken())
    }

    @Test
    fun nativeFreshInstallRetainsTheSampleSource() {
        assertEquals(1, defaultRemoteSources().size)
        assertIs<UnSplashSource>(defaultRemoteSources().single())
    }
}
