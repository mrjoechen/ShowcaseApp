package com.alpha.showcase.common.ui.settings

import PLATFORM_TYPE
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreditsTest {

    private fun names(platform: PLATFORM_TYPE, offersThirdPartyContent: Boolean) =
        credits(platform, offersThirdPartyContent).map { it.name }.toSet()

    @Test
    fun everyFilteredNameIsInTheList() {
        val listed = openSourceLibraries.map { it.name }.toSet()
        (notInAppleBuilds + appleBuildsOnly + thirdPartyContentServices).forEach { name ->
            assertTrue(name in listed, "$name is not in openSourceLibraries")
        }
    }

    @Test
    fun appStoreBuildsOnApplePlatformsListOnlyTheirComponents() {
        val ios = names(PLATFORM_TYPE.Ios, offersThirdPartyContent = false)
        assertTrue("Sentry Cocoa" in ios && "SMBClient" in ios && "AndroidX Room" in ios && "OpenCV" in ios)
        assertFalse("Google Play Billing" in ios || "Hilt" in ios || "SMBJ" in ios)
        assertFalse("TMDB API" in ios || "Unsplash API" in ios || "Pexels API" in ios)
    }

    @Test
    fun otherPlatformsKeepTheirCredits() {
        val android = names(PLATFORM_TYPE.Android, offersThirdPartyContent = true)
        assertTrue("Google Play Billing" in android && "TMDB API" in android && "OpenCV" in android)
        assertFalse("Sentry Cocoa" in android || "SMBClient" in android)
    }
}
