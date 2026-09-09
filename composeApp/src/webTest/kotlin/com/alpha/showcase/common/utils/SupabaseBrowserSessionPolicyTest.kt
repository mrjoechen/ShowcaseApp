package com.alpha.showcase.common.utils

import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SupabaseBrowserSessionPolicyTest {

    @AfterTest
    fun cleanUp() {
        TEST_KEYS.forEach(localStorage::removeItem)
        localStorage.removeItem("showcase_durable_device_id")
        sessionStorage.removeItem(SESSION_MARKER)
        sessionStorage.removeItem(SESSION_KEY)
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

    @Test
    fun missingSessionUsesTheSdkNoSessionContract() = runTest {
        sessionStorage.removeItem(SESSION_KEY)
        val manager = requireNotNull(createSupabaseSessionManager("https://session-test.supabase.co"))

        assertFailsWith<NoSessionFoundException> { manager.loadSession() }
        assertNull(manager.loadSessionOrNull())
    }

    @Test
    fun corruptSessionIsRemovedAndReportedAsMissing() = runTest {
        sessionStorage.setItem(SESSION_KEY, "invalid session json")
        val manager = requireNotNull(createSupabaseSessionManager("https://session-test.supabase.co"))

        assertFailsWith<NoSessionFoundException> { manager.loadSession() }
        assertNull(sessionStorage.getItem(SESSION_KEY))
        assertNull(manager.loadSessionOrNull())
    }

    @Test
    fun sessionSurvivesManagerRecreationAndDeleteClearsIt() = runTest {
        val session = UserSession(
            accessToken = "test-access-token",
            refreshToken = "test-refresh-token",
            expiresIn = 3600,
            tokenType = "bearer",
        )
        val manager = requireNotNull(createSupabaseSessionManager("https://session-test.supabase.co"))
        manager.saveSession(session)

        val recreated = requireNotNull(createSupabaseSessionManager("https://session-test.supabase.co"))
        assertEquals(session, recreated.loadSession())
        assertNull(localStorage.getItem(SESSION_KEY))
        recreated.deleteSession()
        assertNull(recreated.loadSessionOrNull())
        assertNull(sessionStorage.getItem(SESSION_KEY))
    }

    private companion object {
        const val SESSION_MARKER = "showcase_test_session_marker"
        const val SESSION_KEY = "showcase_session-test-supabase-co_session_v2"
        val TEST_KEYS = listOf(
            "sb-example-supabase-co-session",
            "sb-https:--example-supabase-co-session",
            "session",
            "supabase_auth_session",
            "device_prefs",
        )
    }
}
