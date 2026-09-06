package com.alpha.ai.imagegeneration.provider.gemini

import com.alpha.ai.imagegeneration.ProviderUnderstandingEvent
import com.alpha.ai.imagegeneration.ImageUnderstandingRequest
import com.alpha.ai.imagegeneration.ImageUnderstandingResult
import com.alpha.ai.imagegeneration.OperationId
import com.alpha.ai.imagegeneration.SecretValue
import com.alpha.ai.imagegeneration.fixtureSource
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class GeminiVisionProviderTest {
    private val server = MockWebServer()

    @AfterTest
    fun tearDown() = server.close()

    @Test
    fun `generateContent requests JSON and parses text object`() = runTest {
        server.start()
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""{"candidates":[{"content":{"parts":[{"text":"{\"category\":\"animal\"}"}]}}]}"""))

        val events = GeminiVisionProvider().understand(
            ImageUnderstandingRequest(OperationId("gemini-vision-1"), fixtureSource(), "Classify the image"),
            GeminiVisionConfig(SecretValue("token"), "gemini-vision-model", server.url("/v1beta").toString(), allowInsecureHttp = true),
        ).toList()
        val recorded = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject

        assertEquals("/v1beta/models/gemini-vision-model:generateContent", recorded.path)
        assertEquals("token", recorded.getHeader("x-goog-api-key"))
        assertEquals("application/json", body.getValue("generationConfig").jsonObject.getValue("responseMimeType").toString().trim('"'))
        assertTrue(body.toString().contains("responseSchema"))
        val success = events.filterIsInstance<ProviderUnderstandingEvent.Completed>().single().result as ImageUnderstandingResult.Success
        assertEquals("animal", success.output.data.getValue("category").toString().trim('"'))
    }
}
