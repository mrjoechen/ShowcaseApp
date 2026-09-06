package com.alpha.ai.imagegeneration.internal.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PortableProtocolTest {
    @Test
    fun absoluteSchemesRemainCaseInsensitive() {
        assertEquals("https", BaseUrlValidator.validate("HTTPS://provider.test/v1", false).protocol.name)
    }

    @Test
    fun unpaddedBase64RemainsCompatibleWithProviderResponses() {
        assertEquals("image/png", ImageBytesValidator.decodeBase64("iVBORw0KGgo", null, 8).mimeType)
    }

    @Test
    fun modelSlashesAndSpacesStayInOneUrlSegment() {
        val url = BaseUrlValidator.validate("https://provider.test/custom/v1beta/", false)
            .toProviderUrlBuilder().addPathSegment("models").addPathSegment("models/gemini/2.5 flash:generateContent").build()
        assertEquals("https://provider.test/custom/v1beta/models/models%2Fgemini%2F2.5%20flash:generateContent", url.toString())
    }

    @Test
    fun relativeRootsAndCredentialDecorationsAreRejected() {
        listOf("/v1", "api.test/v1", "https:///v1", "https://api.test/v1?", "https://api.test/v1#", "https://@api.test/v1", "https://api.test\\@other.test").forEach {
            assertFailsWith<ImageGenerationTransportException> { BaseUrlValidator.validate(it, false) }
        }
    }

    @Test
    fun retryAfterSupportsSaturatedSecondsAndHttpDates() {
        assertEquals(Long.MAX_VALUE, parseRetryAfter(Long.MAX_VALUE.toString(), 0))
        assertEquals(60_000L, parseRetryAfter("Thu, 01 Jan 1970 00:01:00 GMT", 0))
        assertEquals(0L, parseRetryAfter("Thu, 01 Jan 1970 00:01:00 GMT", 90_000))
    }

    @Test
    fun multipartEscapesFilenameHeadersAndPreservesImageBytes() {
        val body = ImageEditMultipart.encode("gpt-image-1", "ink", "a\"\r\nInjected: true.png", "image/png", byteArrayOf(1, 2, 3))
        val encoded = body.bytes.decodeToString()
        assertTrue(encoded.contains("filename=\"a%22%0D%0AInjected: true.png\""))
        assertTrue(!encoded.contains("\r\nInjected:"))
        assertTrue(encoded.contains("\r\n\r\n\u0001\u0002\u0003\r\n"))
    }
}
