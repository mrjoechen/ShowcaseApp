package com.alpha.showcase.common.utils

import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SupabaseBrowserSessionPolicyTest {

    @AfterTest
    fun cleanUp() {
        TEST_KEYS.forEach(localStorage::removeItem)
        localStorage.removeItem("showcase_durable_device_id")
        sessionStorage.removeItem(SESSION_MARKER)
    }

    @Test
    fun legacyTokensAreRemovedWithoutDeletingTheDurableDeviceIdentity() {
        TEST_KEYS.forEach { localStorage.setItem(it, "sensitive") }
        localStorage.setItem("showcase_durable_device_id", "stable-device")
        sessionStorage.setItem(SESSION_MARKER, "current-tab")

        clearLegacySupabaseBrowserSession("https://example.supabase.co")

        TEST_KEYS.forEach { assertNull(localStorage.getItem(it)) }
        assertEquals("stable-device", localStorage.getItem("showcase_durable_device_id"))
        assertEquals("current-tab", sessionStorage.getItem(SESSION_MARKER))
    }

    private companion object {
        const val SESSION_MARKER = "showcase_test_session_marker"
        val TEST_KEYS = listOf(
            "sb-example-supabase-co-session",
            "sb-https:--example-supabase-co-session",
            "session",
            "supabase_auth_session",
            "device_prefs",
        )
    }
}
