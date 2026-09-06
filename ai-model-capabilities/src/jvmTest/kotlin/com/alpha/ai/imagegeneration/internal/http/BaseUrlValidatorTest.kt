package com.alpha.ai.imagegeneration.internal.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BaseUrlValidatorTest {
    @Test
    fun `accepts an HTTPS API root`() {
        val url = BaseUrlValidator.validate("https://api.example.test/v1/", allowInsecureHttp = false)

        assertEquals("https://api.example.test/v1/", url.toString())
    }

    @Test
    fun `rejects HTTP API roots unless explicitly enabled`() {
        assertFailsWith<ImageGenerationTransportException> {
            BaseUrlValidator.validate("http://api.example.test/v1/", allowInsecureHttp = false)
        }

        assertEquals(
            "http://api.example.test/v1/",
            BaseUrlValidator.validate("http://api.example.test/v1/", allowInsecureHttp = true).toString(),
        )
    }

    @Test
    fun `rejects API roots with user info query or fragment`() {
        listOf(
            "https://token@api.example.test/v1/",
            "https://api.example.test/v1/?token=secret",
            "https://api.example.test/v1/#section",
        ).forEach { value ->
            assertFailsWith<ImageGenerationTransportException> {
                BaseUrlValidator.validate(value, allowInsecureHttp = false)
            }
        }
    }
}
