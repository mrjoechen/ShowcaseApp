package com.alpha.showcase.common.ui.settings

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneralPreferenceConsentTest {

    @Test
    fun anonymousUsageIsOffByDefault() {
        val preference = GeneralPreference(language = 0, darkMode = 0)

        assertFalse(preference.anonymousUsage)
        assertFalse(preference.hasAnonymousUsageConsent)
    }

    @Test
    fun legacyEnabledValueIsNotExplicitConsent() {
        val preference = Json.decodeFromString<GeneralPreference>(
            """{"language":0,"darkMode":0,"anonymousUsage":true}"""
        )

        assertTrue(preference.anonymousUsage)
        assertFalse(preference.hasAnonymousUsageConsent)
    }

    @Test
    fun currentConsentVersionEnablesAnonymousUsage() {
        val preference = GeneralPreference(
            language = 0,
            darkMode = 0,
            anonymousUsage = true,
            anonymousUsageConsentVersion = ANONYMOUS_USAGE_CONSENT_VERSION
        )

        assertTrue(preference.hasAnonymousUsageConsent)
    }
}
