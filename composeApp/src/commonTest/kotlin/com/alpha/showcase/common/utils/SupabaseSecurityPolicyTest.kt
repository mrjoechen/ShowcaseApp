package com.alpha.showcase.common.utils

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SupabaseSecurityPolicyTest {

    @Test
    fun nativeStoragePreservesThePreviousSdkSessionKeyAcrossUpgrade() {
        assertEquals(
            "sb-example-supabase-co-session",
            supabaseNativeSessionStorageKey("https://example.supabase.co/"),
        )
        assertEquals(
            "sb-localhost:54321-session",
            supabaseNativeSessionStorageKey("http://localhost:54321"),
        )
    }

    @Test
    fun publicConfigAccessRejectsKeysOutsideTheClientAllowlist() {
        assertEquals("github_proxy", requirePublicConfigKey("github_proxy"))
        assertEquals("music_api_baseurl", requirePublicConfigKey("music_api_baseurl"))
        assertEquals("online_track_interval", requirePublicConfigKey("online_track_interval"))

        assertFailsWith<IllegalArgumentException> {
            requirePublicConfigKey("music_api_auth")
        }
        assertFailsWith<IllegalArgumentException> {
            requirePublicConfigKey("gitee_access_token")
        }
    }

    @Test
    fun criticalConfigRequestsAcceptOnlyBoundedIdentifierKeys() {
        assertEquals("music_api_auth", requireCriticalConfigKey("music_api_auth"))
        assertEquals(
            "{\"keys\":[\"music_api_auth\"]}",
            Json.encodeToString(CriticalConfigRequest(listOf("music_api_auth"))),
        )

        listOf("", "../secret", "key with spaces", "service.api_token", "Uppercase", "a".repeat(65)).forEach { key ->
            assertFailsWith<IllegalArgumentException> {
                requireCriticalConfigKey(key)
            }
        }
    }

    @Test
    fun criticalConfigResponseDecodesReferenceAppBatchProtocol() {
        val response = Json.decodeFromString<CriticalConfigResponse>(
            """{"values":{"music_api_auth":"test-value"},"missing":["api_proxy_token"]}"""
        )
        assertEquals("test-value", response.values["music_api_auth"])
        assertEquals(listOf("api_proxy_token"), response.missing)
    }

    @Test
    fun deviceRegistrationDropsHighEntropyAndPersonallyNamedFields() {
        val registration = device().toSupabaseRegistration()
        val encoded = Json.encodeToString(registration)

        assertEquals("zh", registration.locale)
        assertFalse(encoded.contains("auth-user-id"))
        assertEquals(
            JsonPrimitive("00000000-0000-4000-8000-000000000001"),
            Json.parseToJsonElement(encoded).jsonObject["p_device_id"],
        )
        assertFalse(encoded.contains("auth_user_id"))
        assertFalse(encoded.contains("Alice's MacBook"))
        assertFalse(encoded.contains("carrier_name"))
        assertFalse(encoded.contains("carrier_country"))
        assertFalse(encoded.contains("screen_size"))
        assertFalse(encoded.contains("timezone_offset"))
        assertFalse(encoded.contains("build_id"))
    }

    @Test
    fun telemetryPayloadsDoNotAcceptClientControlledOwnerOrRowIds() {
        val event = Json.encodeToString(
            EventLog(
                name = "screen_opened",
                sid = "session-id",
                buildType = "release",
            )
        )
        val feedback = Json.encodeToString(
            UserFeedback(
                feedbackType = "user_feedback",
                content = "Useful feedback",
            )
        )

        listOf(event, feedback).forEach { encoded ->
            assertFalse(encoded.contains("device_id"))
            assertFalse(encoded.contains("auth_user_id"))
        }
        assertFalse(event.contains("\"id\""))
    }

    @Test
    fun stateUploadsAttachTheRegisteredDeviceButNeverAnOwnerClaim() {
        val payload = Json.encodeToJsonElement(UserFeedback(
            feedbackType = "user_feedback", content = "Feedback",
        )).jsonObject
        val bound = withRegisteredDeviceId(payload, "00000000-0000-4000-8000-000000000001")
        assertEquals(JsonPrimitive("00000000-0000-4000-8000-000000000001"), bound["device_id"])
        assertEquals(JsonPrimitive("Feedback"), bound["content"])
        assertFalse("auth_user_id" in bound)
        assertFalse("user_id" in bound)
        assertFailsWith<IllegalArgumentException> { withRegisteredDeviceId(payload, "arbitrary-device") }
        assertFailsWith<IllegalArgumentException> {
            withRegisteredDeviceId(JsonObject(payload + ("auth_user_id" to JsonPrimitive("foreign-user"))),
                "00000000-0000-4000-8000-000000000001")
        }
    }

    private fun device() = Device(
        id = "00000000-0000-4000-8000-000000000001",
        name = "Alice's MacBook",
        model = "Browser",
        oemName = "Vendor",
        osName = "Web",
        osVersion = "1",
        locale = "zh-CN",
        screenSize = "3840x2160",
        appVersion = "1.0.55",
        appNameSpace = "com.alpha.showcase.web",
        appBuild = "build",
        carrierName = "Carrier",
        carrierCountry = "CN",
        buildType = "release",
        osApi = "",
        buildId = "fingerprinting-build-id",
        timezoneOffset = "28800",
        cpuArch = "arm64",
    )
}
