package com.alpha.ai.imagegeneration.provider.gemini

import com.alpha.ai.imagegeneration.GenerationStage
import com.alpha.ai.imagegeneration.GenerationResult
import com.alpha.ai.imagegeneration.ImageGenerationErrorCategory
import com.alpha.ai.imagegeneration.ProviderGenerationEvent
import com.alpha.ai.imagegeneration.ProviderId
import com.alpha.ai.imagegeneration.AiCapability
import com.alpha.ai.imagegeneration.ProviderModelCatalogRequest
import com.alpha.ai.imagegeneration.ProviderModelCatalogResult
import com.alpha.ai.imagegeneration.RetryAdvice
import com.alpha.ai.imagegeneration.SecretValue
import com.alpha.ai.imagegeneration.base64
import com.alpha.ai.imagegeneration.internal.http.HttpRequest
import com.alpha.ai.imagegeneration.internal.http.HttpResponse
import com.alpha.ai.imagegeneration.internal.http.HttpTransport
import com.alpha.ai.imagegeneration.internal.http.ImageGenerationTransportException
import com.alpha.ai.imagegeneration.internal.http.PreparedHttpCall
import com.alpha.ai.imagegeneration.internal.http.RunningHttpCall
import com.alpha.ai.imagegeneration.lastProviderCompleted
import com.alpha.ai.imagegeneration.request
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy

class GeminiNanoBananaProviderTest {
    private val server = MockWebServer()
    private val provider = GeminiNanoBananaProvider()

    @AfterTest
    fun tearDown() = server.close()

    @Test
    fun `generateContent sends source and extracts inline image`() = runTest {
        server.start()
        server.enqueue(jsonResponse(geminiImageResponse(PNG_BYTES)))

        val events = provider.generate(request(), config()).toList()
        val terminal = events.lastProviderCompleted()
        val recorded = assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject

        assertEquals("POST", recorded.method)
        assertEquals("/v1beta/models/gemini-2.5-flash-image:generateContent", recorded.path)
        assertEquals("token-value", recorded.getHeader("x-goog-api-key"))
        val content = body.getValue("contents").toString()
        assertTrue(content.contains("a test image"))
        assertTrue(content.contains("inlineData"))
        assertTrue(content.contains(byteArrayOf(9, 8, 7).base64()))
        assertEquals(listOf("TEXT", "IMAGE"), body.getValue("generationConfig").jsonObject
            .getValue("responseModalities").toString().trim('[', ']').split(",").map { it.trim('"') })
        assertContentEquals(PNG_BYTES, terminal.successImage().bytes)
        assertEquals("image/png", terminal.successImage().mimeType)
        assertEquals(
            listOf(GenerationStage.PREPARING_INPUT, GenerationStage.UPLOADING, GenerationStage.GENERATING),
            events.filterIsInstance<ProviderGenerationEvent.Stage>().map { it.value },
        )
    }

    @Test
    fun `descriptor and config expose Gemini protocol and conservative input contract`() {
        assertEquals(ProviderId("gemini-nano-banana"), provider.descriptor.id)
        assertEquals(setOf("gemini-generate-content-v1beta"), provider.descriptor.supportedProtocolVersions)
        assertEquals(setOf("image/png", "image/jpeg", "image/webp"), provider.descriptor.capabilities.supportedInputMimeTypes)
        assertEquals(12L * 1024 * 1024, provider.descriptor.capabilities.maxInputBytes)
        assertTrue(provider.descriptor.capabilities.reliableDispatchBoundary)
        assertEquals("https://generativelanguage.googleapis.com/v1beta", GeminiImageConfig(SecretValue("token"), "model").baseUrl)
        assertFailsWith<IllegalArgumentException> { GeminiImageConfig(SecretValue("token"), " ") }
    }

    @Test
    fun `model catalog keeps generate content models and uses base model id`() = runTest {
        server.start()
        server.enqueue(jsonResponse("""
            {"models":[
              {"name":"models/gemini-2.5-flash","baseModelId":"gemini-2.5-flash","displayName":"Gemini 2.5 Flash","supportedGenerationMethods":["generateContent"]},
              {"name":"models/text-embedding-004","baseModelId":"text-embedding-004","supportedGenerationMethods":["embedContent"]}
            ]}
        """.trimIndent()))
        val config = config()

        val result = provider.listModels(
            ProviderModelCatalogRequest(
                providerId = config.providerId,
                capability = AiCapability.IMAGE_TO_IMAGE,
                baseUrl = config.baseUrl,
                protocolVersion = config.protocolVersion,
                apiToken = config.apiToken,
                allowInsecureHttp = true,
            ),
        ) as ProviderModelCatalogResult.Available
        val recorded = assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))

        assertEquals(listOf("gemini-2.5-flash"), result.models.map { it.id })
        assertEquals("Gemini 2.5 Flash", result.models.single().displayName)
        assertEquals("/v1beta/models?pageSize=1000", recorded.path)
        assertEquals("token-value", recorded.getHeader("x-goog-api-key"))
    }

    @Test
    fun `model path segment is encoded and user base URL is preserved`() = runTest {
        server.start()
        server.enqueue(jsonResponse(geminiImageResponse(PNG_BYTES)))
        val config = config(model = "models/gemini/2.5 flash", baseUrl = server.url("/custom/v1beta").toString())

        provider.generate(request(), config).toList()
        val recorded = assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("/custom/v1beta/models/models%2Fgemini%2F2.5%20flash:generateContent", recorded.path)
    }

    @Test
    fun `response accepts snake inline data spelling`() = runTest {
        server.start()
        val body = """{"candidates":[{"content":{"parts":[{"inline_data":{"mime_type":"image/png","data":"${PNG_BYTES.base64()}"}}]}}]}"""
        server.enqueue(jsonResponse(body))

        val terminal = provider.generate(request(), config()).toList().lastProviderCompleted()

        assertContentEquals(PNG_BYTES, terminal.successImage().bytes)
    }

    @Test
    fun `response skips invalid image parts and returns first valid image`() = runTest {
        server.start()
        val invalid = byteArrayOf(1, 2, 3).base64()
        val body = """{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"image/png","data":"$invalid"}},{"inlineData":{"mimeType":"image/png","data":"${PNG_BYTES.base64()}"}}]}}]}"""
        server.enqueue(jsonResponse(body))

        val terminal = provider.generate(request(), config()).toList().lastProviderCompleted()

        assertContentEquals(PNG_BYTES, terminal.successImage().bytes)
    }

    @Test
    fun `safety block maps to content policy rejection`() = runTest {
        server.start()
        server.enqueue(jsonResponse("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))

        val failure = provider.generate(request(), config()).toList().lastProviderCompleted().failure().error

        assertEquals(ImageGenerationErrorCategory.CONTENT_POLICY, failure.category)
        assertEquals(RetryAdvice.DO_NOT_RETRY, failure.retryAdvice)
        assertTrue(failure.requestMayHaveBeenAccepted)
    }

    @Test
    fun `blocked candidate maps to content policy rejection`() = runTest {
        server.start()
        server.enqueue(jsonResponse("""{"candidates":[{"finishReason":"BLOCKLIST","content":{"parts":[]}}]}"""))

        val failure = provider.generate(request(), config()).toList().lastProviderCompleted().failure().error

        assertEquals(ImageGenerationErrorCategory.CONTENT_POLICY, failure.category)
    }

    @Test
    fun `text only successful response is malformed`() = runTest {
        server.start()
        server.enqueue(jsonResponse("""{"candidates":[{"content":{"parts":[{"text":"hello"}]}}]}"""))

        val failure = provider.generate(request(), config()).toList().lastProviderCompleted().failure().error

        assertEquals(ImageGenerationErrorCategory.MALFORMED_RESPONSE, failure.category)
        assertTrue(failure.requestMayHaveBeenAccepted)
    }

    @Test
    fun `successful response accepts only JSON media types`() = runTest {
        server.start()
        val body = geminiImageResponse(PNG_BYTES).encodeToByteArray()

        listOf(null, "image/png").forEach { contentType ->
            val headers = contentType?.let { mapOf("content-type" to it) }.orEmpty()
            val result = providerWith(ResponseTransport(HttpResponse(200, headers, body)))
                .generate(request(), config()).toList().lastProviderCompleted().result
            val category = (result as? GenerationResult.Failure)?.error?.category
            assertEquals(ImageGenerationErrorCategory.MALFORMED_RESPONSE, category, contentType)
        }

        val accepted = providerWith(
            ResponseTransport(HttpResponse(200, mapOf("content-type" to "application/problem+json"), body)),
        ).generate(request(), config()).toList().lastProviderCompleted().successImage()
        assertContentEquals(PNG_BYTES, accepted.bytes)
    }

    @Test
    fun `rate limit is definite rejection and retryable`() = runTest {
        server.start()
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "7"))

        val failure = provider.generate(request(), config()).toList().lastProviderCompleted().failure().error

        assertEquals(ImageGenerationErrorCategory.RATE_LIMITED, failure.category)
        assertEquals(RetryAdvice.SAFE_TO_RETRY, failure.retryAdvice)
        assertEquals(7_000, failure.retryAfterMillis)
        assertFalse(failure.requestMayHaveBeenAccepted)
        assertTrue(failure.definitelyUnprocessed)
    }

    @Test
    fun `oversized decoded image is malformed`() = runTest {
        server.start()
        val oversized = ByteArray(33 * 1024 * 1024) { 1 }.base64()
        server.enqueue(jsonResponse(geminiImageResponse(oversized)))

        val failure = provider.generate(request(), config()).toList().lastProviderCompleted().failure().error

        assertEquals(ImageGenerationErrorCategory.MALFORMED_RESPONSE, failure.category)
    }

    @Test
    fun `prepare and synchronous start failures remain before dispatch`() = runTest {
        val prepare = providerWith(PrepareFailureTransport()).generate(request(), config()).toList().lastProviderCompleted().failure().error
        assertFalse(prepare.requestMayHaveBeenAccepted)
        val start = providerWith(StartFailureTransport()).generate(request(), config()).toList().lastProviderCompleted().failure().error
        assertFalse(start.requestMayHaveBeenAccepted)
    }

    @Test
    fun `await failure after start is conservatively ambiguous`() = runTest {
        val failure = providerWith(AwaitFailureTransport()).generate(request(), config()).toList().lastProviderCompleted().failure().error
        assertTrue(failure.requestMayHaveBeenAccepted)
        assertEquals(RetryAdvice.AMBIGUOUS, failure.retryAdvice)
    }

    @Test
    fun `server failure after dispatch is conservatively ambiguous`() = runTest {
        server.start()
        server.enqueue(MockResponse().setResponseCode(503).setBody("temporarily unavailable"))

        val failure = provider.generate(request(), config()).toList().lastProviderCompleted().failure().error

        assertEquals(ImageGenerationErrorCategory.SERVER, failure.category)
        assertEquals(RetryAdvice.AMBIGUOUS, failure.retryAdvice)
        assertTrue(failure.requestMayHaveBeenAccepted)
    }

    @Test
    fun `cancellation after dispatch cancels active call`() = runTest {
        server.start()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val collection = async { provider.generate(request(), config()).collect() }
        server.takeRequest(1, TimeUnit.SECONDS)
        collection.cancel()
        assertTrue(collection.isCancelled)
    }

    private fun config(
        model: String = "gemini-2.5-flash-image",
        baseUrl: String = server.url("/v1beta").toString(),
    ) = GeminiImageConfig(
        apiToken = SecretValue("token-value"),
        model = model,
        baseUrl = baseUrl,
        allowInsecureHttp = true,
    )

    private fun providerWith(transport: HttpTransport) = GeminiNanoBananaProvider(transport)

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body)

    private fun geminiImageResponse(bytes: ByteArray) = geminiImageResponse(bytes.base64())
    private fun geminiImageResponse(base64: String) = """{"candidates":[{"content":{"parts":[{"text":"caption"},{"inlineData":{"mimeType":"image/png","data":"$base64"}}]}}]}"""

    private fun ProviderGenerationEvent.Completed.successImage() = (result as GenerationResult.Success).image
    private fun ProviderGenerationEvent.Completed.failure() = result as GenerationResult.Failure

    private val PNG_BYTES = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

    private class PrepareFailureTransport : HttpTransport {
        override fun prepare(request: HttpRequest, limitBytes: Long): PreparedHttpCall = throw ImageGenerationTransportException("prepare failed")
    }
    private class StartFailureTransport : HttpTransport {
        override fun prepare(request: HttpRequest, limitBytes: Long) = object : PreparedHttpCall {
            override fun start(): RunningHttpCall = throw ImageGenerationTransportException("start failed")
        }
    }
    private class AwaitFailureTransport : HttpTransport {
        override fun prepare(request: HttpRequest, limitBytes: Long) = object : PreparedHttpCall {
            override fun start() = object : RunningHttpCall {
                override suspend fun await(): HttpResponse = throw ImageGenerationTransportException("await failed")
                override fun cancelIfActive() = Unit
            }
        }
    }
    private class ResponseTransport(private val response: HttpResponse) : HttpTransport {
        override fun prepare(request: HttpRequest, limitBytes: Long) = object : PreparedHttpCall {
            override fun start() = object : RunningHttpCall {
                override suspend fun await() = response
                override fun cancelIfActive() = Unit
            }
        }
    }
}
