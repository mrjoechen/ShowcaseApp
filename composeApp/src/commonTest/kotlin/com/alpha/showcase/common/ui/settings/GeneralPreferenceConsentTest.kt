package com.alpha.showcase.common.ui.settings

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneralPreferenceConsentTest {

    @Test
    fun anonymousUsageUsesCurrentDefault() {
        val preference = GeneralPreference(language = 0, darkMode = 0)

        assertTrue(preference.anonymousUsage)
    }

    @Test
    fun serializedEnabledValueRemainsEnabled() {
        val preference = Json.decodeFromString<GeneralPreference>(
            """{"language":0,"darkMode":0,"anonymousUsage":true}"""
        )

        assertTrue(preference.anonymousUsage)
    }

    @Test
    fun explicitOptOutDisablesAnonymousUsage() {
        val preference = GeneralPreference(
            language = 0,
            darkMode = 0,
            anonymousUsage = false,
        )

        assertFalse(preference.anonymousUsage)
    }
}
