package com.alpha.ai.imagegeneration.provider.openai

import com.alpha.ai.imagegeneration.AiCapability
import com.alpha.ai.imagegeneration.GenerationStage
import com.alpha.ai.imagegeneration.ProviderUnderstandingEvent
import com.alpha.ai.imagegeneration.ImageUnderstandingRequest
import com.alpha.ai.imagegeneration.ImageUnderstandingResult
import com.alpha.ai.imagegeneration.OperationId
import com.alpha.ai.imagegeneration.SecretValue
import com.alpha.ai.imagegeneration.base64
import com.alpha.ai.imagegeneration.fixtureSource
import com.alpha.ai.imagegeneration.internal.http.HttpTransport
import com.alpha.ai.imagegeneration.internal.http.PreparedHttpCall
import com.alpha.ai.imagegeneration.internal.http.RunningHttpCall
import com.alpha.ai.imagegeneration.lastProviderCompleted
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

class OpenAiVisionProviderTest {
    private val server = MockWebServer()

    @AfterTest
    fun tearDown() = server.close()

    @Test
    fun `chat completions request includes image and schema and parses object`() = runTest {
        server.start()
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""{"choices":[{"message":{"content":"{\"summary\":\"a cat\"}"}}]}"""))

        val provider = OpenAiVisionProvider()
        val events = provider.understand(
            ImageUnderstandingRequest(OperationId("vision-1"), fixtureSource(), "Summarize this image"),
            OpenAiVisionConfig(SecretValue("token-value"), "vision-model", server.url("/v1").toString(), allowInsecureHttp = true),
        ).toList()
        val recorded = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject

        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer token-value", recorded.getHeader("Authorization"))
        assertEquals("vision-model", body.getValue("model").toString().trim('"'))
        assertTrue(body.toString().contains("data:image/png;base64,${byteArrayOf(9, 8, 7).base64()}"))
        assertTrue(body.toString().contains("Return only one JSON object"))
        val completed = events.filterIsInstance<ProviderUnderstandingEvent.Completed>().single()
        val success = completed.result as ImageUnderstandingResult.Success
        assertEquals("a cat", success.output.data.getValue("summary").toString().trim('"'))
        assertEquals(
            listOf(GenerationStage.PREPARING_INPUT, GenerationStage.UPLOADING, GenerationStage.GENERATING),
            events.filterIsInstance<ProviderUnderstandingEvent.Stage>().map { it.value },
        )
        assertTrue(AiCapability.IMAGE_UNDERSTANDING in provider.descriptor.capabilities.supportedCapabilities)
    }
}
