package com.alpha.showcase.common.ui.settings

import com.alpha.showcase.common.security.initializeConfigEncryption
import kotlinx.browser.localStorage
import kotlinx.coroutines.test.runTest
import org.w3c.dom.get
import org.w3c.dom.set
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SettingPreferenceRepoWebTest {

    @BeforeTest
    fun setUp() {
        localStorage.removeItem(SETTINGS_KEY)
        localStorage.removeItem(PREFERENCE_KEY)
        localStorage.removeItem(CONFIG_KEY)
        initializeConfigEncryption()
    }

    @AfterTest
    fun tearDown() {
        localStorage.removeItem(SETTINGS_KEY)
        localStorage.removeItem(PREFERENCE_KEY)
        localStorage.removeItem(CONFIG_KEY)
    }

    @Test
    fun settingsRoundTripUsesNonBlockingCrypto() = runTest {
        val expected = Settings(showcaseMode = 1)
        val repository = SettingPreferenceRepo()

        repository.updateSettings(expected)

        assertTrue(localStorage[SETTINGS_KEY].orEmpty().startsWith("scenc:v2:"))
        assertEquals(expected, repository.getSettings())
    }

    @Test
    fun unreadableSettingsAreNotSilentlyReplacedWithDefaults() = runTest {
        val unreadable = "scenc:v2:not-valid-ciphertext"
        localStorage[SETTINGS_KEY] = unreadable

        val failure = runCatching { SettingPreferenceRepo().getSettings() }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(unreadable, localStorage[SETTINGS_KEY])
    }

    private companion object {
        const val SETTINGS_KEY = "settings"
        const val PREFERENCE_KEY = "preference"
        const val CONFIG_KEY = "showcase_config_key_v2"
    }
}
