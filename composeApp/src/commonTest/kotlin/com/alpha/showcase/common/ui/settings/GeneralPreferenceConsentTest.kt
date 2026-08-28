package com.alpha.showcase.common.ui.settings

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneralPreferenceConsentTest {

    @Test
    fun anonymousUsageUsesCurrentDefault() {
        val preference = GeneralPreference(language = 0, darkMode = 0)

        assertFalse(preference.anonymousUsage)
        assertFalse(preference.hasAnonymousUsageConsent)
    }

    @Test
    fun serializedEnabledValueRemainsEnabled() {
        val preference = Json.decodeFromString<GeneralPreference>(
            """{"language":0,"darkMode":0,"anonymousUsage":true}"""
        )

        assertTrue(preference.anonymousUsage)
        assertFalse(preference.hasAnonymousUsageConsent)
    }

    @Test
    fun explicitOptOutDisablesAnonymousUsage() {
        val preference = GeneralPreference(
            language = 0,
            darkMode = 0,
            anonymousUsage = false,
        )

        assertFalse(preference.anonymousUsage)
        assertFalse(preference.hasAnonymousUsageConsent)
    }

    @Test
    fun legacyOptInWithoutCurrentConsentIsMigratedToOptOut() {
        val migrated = migrateAnonymousUsageConsent(
            GeneralPreference(
                language = 0,
                darkMode = 0,
                anonymousUsage = true,
                anonymousUsageConsentVersion = 0,
            )
        )

        assertFalse(migrated.anonymousUsage)
        assertEquals(ANONYMOUS_USAGE_CONSENT_VERSION, migrated.anonymousUsageConsentVersion)
        assertFalse(migrated.hasAnonymousUsageConsent)
    }

    @Test
    fun currentExplicitConsentIsPreservedByMigration() {
        val preference = GeneralPreference(
            language = 0,
            darkMode = 0,
            anonymousUsage = true,
            anonymousUsageConsentVersion = ANONYMOUS_USAGE_CONSENT_VERSION,
        )

        assertEquals(preference, migrateAnonymousUsageConsent(preference))
        assertTrue(preference.hasAnonymousUsageConsent)
    }

    @Test
    fun staleUnrelatedUpdateCannotRestoreConsentAfterOptOut() {
        val staleSnapshot = GeneralPreference(
            language = 0,
            darkMode = 0,
            anonymousUsage = true,
            anonymousUsageConsentVersion = ANONYMOUS_USAGE_CONSENT_VERSION,
            autoCheckUpdate = true,
        )
        val currentAfterOptOut = staleSnapshot.copy(anonymousUsage = false)
        val staleAutoUpdateAction = staleSnapshot.copy(autoCheckUpdate = false)

        val merged = mergeGeneralPreferenceUpdate(
            current = currentAfterOptOut,
            expected = staleSnapshot,
            updated = staleAutoUpdateAction,
        )

        assertFalse(merged.anonymousUsage)
        assertFalse(merged.hasAnonymousUsageConsent)
        assertFalse(merged.autoCheckUpdate)
    }

    @Test
    fun explicitConsentChangeAndConcurrentThemeChangeAreBothPreserved() {
        val expected = GeneralPreference(
            language = 0,
            darkMode = 0,
            themeStyle = 0,
            anonymousUsage = false,
            anonymousUsageConsentVersion = ANONYMOUS_USAGE_CONSENT_VERSION,
        )
        val current = expected.copy(themeStyle = 2)
        val explicitOptIn = expected.copy(anonymousUsage = true)

        val merged = mergeGeneralPreferenceUpdate(current, expected, explicitOptIn)

        assertEquals(2, merged.themeStyle)
        assertTrue(merged.hasAnonymousUsageConsent)
    }
}
