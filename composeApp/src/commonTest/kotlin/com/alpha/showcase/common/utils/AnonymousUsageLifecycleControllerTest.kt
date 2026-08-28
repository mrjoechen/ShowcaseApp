package com.alpha.showcase.common.utils

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AnonymousUsageLifecycleControllerTest {

    @Test
    fun disablingUsageKeepsSupabaseInitializedAndDisablesCollectors() = runTest {
        val calls = mutableListOf<String>()
        val controller = controllerRecordingCallsIn(calls)

        controller.applyConsent(false)

        assertEquals(
            listOf(
                "supabase:initialize",
                "auth:ensure",
                "collection:false",
                "analytics:false",
                "sentry:false",
            ),
            calls,
        )
    }

    @Test
    fun enablingUsageKeepsSupabaseInitializedAndEnablesCollectors() = runTest {
        val calls = mutableListOf<String>()
        val controller = controllerRecordingCallsIn(calls)

        controller.applyConsent(true)

        assertEquals(
            listOf(
                "supabase:initialize",
                "auth:ensure",
                "analytics:true",
                "sentry:true",
                "collection:true",
            ),
            calls,
        )
    }

    private fun controllerRecordingCallsIn(
        calls: MutableList<String>,
    ) = AnonymousUsageLifecycleController(
        initializeSupabase = { calls += "supabase:initialize" },
        ensureSupabaseAuth = { calls += "auth:ensure" },
        setAnalyticsEnabled = { enabled -> calls += "analytics:$enabled" },
        setSentryEnabled = { enabled -> calls += "sentry:$enabled" },
        setAnonymousCollectionEnabled = { enabled -> calls += "collection:$enabled" },
    )
}
