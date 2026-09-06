package com.alpha.ai.imagegeneration.provider.openai

import com.alpha.ai.imagegeneration.AiCapability
import com.alpha.ai.imagegeneration.GenerationStage
import com.alpha.ai.imagegeneration.ImageGeneration
import com.alpha.ai.imagegeneration.ImageGenerationErrorCategory
import com.alpha.ai.imagegeneration.ImageGenerationEvent
import com.alpha.ai.imagegeneration.ProviderGenerationEvent
import com.alpha.ai.imagegeneration.ProviderId
import com.alpha.ai.imagegeneration.ProviderConnectionTestResult
import com.alpha.ai.imagegeneration.ProviderModelCatalogRequest
import com.alpha.ai.imagegeneration.ProviderModelCatalogResult
import com.alpha.ai.imagegeneration.RetryAdvice
import com.alpha.ai.imagegeneration.SecretValue
import com.alpha.ai.imagegeneration.base64
import com.alpha.ai.imagegeneration.lastProviderCompleted
import com.alpha.ai.imagegeneration.request
import com.alpha.ai.imagegeneration.internal.http.HttpRequest
import com.alpha.ai.imagegeneration.internal.http.HttpResponse
import com.alpha.ai.imagegeneration.internal.http.PreparedHttpCall
import com.alpha.ai.imagegeneration.internal.http.RunningHttpCall
import com.alpha.ai.imagegeneration.internal.http.HttpTransport
import com.alpha.ai.imagegeneration.internal.http.ImageGenerationTransportException
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy

class OpenAiImageProviderTest {
    private val server = MockWebServer()
    private val provider = OpenAiImageProvider()

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun `image edit request is multipart and returns normalized png`() = runTest {
        server.start()
        server.enqueue(jsonResponse("""{"data":[{"b64_json":"${PNG_BYTES.base64()}"}]}"""))

        val events = provider.generate(request(), config()).toList()
        val terminal = events.lastProviderCompleted()
        val recorded = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))

        assertEquals("POST", recorded.method)
        assertEquals("/v1/images/edits", recorded.path)
        assertEquals("Bearer token-value", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("name=\"model\""))
        assertTrue(body.contains("name=\"prompt\""))
        assertTrue(body.contains("name=\"output_format\""))
        assertTrue(body.contains("name=\"image\"; filename=\"source.png\""))
        assertTrue(body.contains("Content-Type: image/png"))
        assertTrue(body.contains("name=\"output_format\""))
        assertTrue(body.replace("\r\n", "\n").contains("\n\npng"))
        assertTrue(body.contains("a test image"))
        assertTrue(body.contains("source.png"))
        assertContentEquals(PNG_BYTES, terminal.successImage().bytes)
        assertEquals("image/png", terminal.successImage().mimeType)
        assertEquals(
            listOf(GenerationStage.PREPARING_INPUT, GenerationStage.UPLOADING, GenerationStage.GENERATING),
            events.filterIsInstance<ProviderGenerationEvent.Stage>().map { it.value },
        )
    }

    @Test
    fun `descriptor accepts supported source formats through twelve mib`() {
        val descriptor = provider.descriptor

        assertEquals(ProviderId("openai"), descriptor.id)
        assertEquals(setOf("image/png", "image/jpeg", "image/webp"), descriptor.capabilities.supportedInputMimeTypes)
        assertEquals(12L * 1024 * 1024, descriptor.capabilities.maxInputBytes)
        assertTrue(descriptor.capabilities.reliableDispatchBoundary)
    }

    @Test
    fun `connection test checks model metadata endpoint without submitting image work`() = runTest {
        server.start()
        server.enqueue(jsonResponse("""{"data":[]}"""))

        val result = provider.testConnection(AiCapability.IMAGE_TO_IMAGE, config())
        val recorded = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))

        assertTrue(result is ProviderConnectionTestResult.Available)
        assertEquals("GET", recorded.method)
        assertEquals("/v1/models", recorded.path)
        assertEquals("Bearer token-value", recorded.getHeader("Authorization"))
        assertEquals(0L, recorded.bodySize)
    }

    @Test
    fun `model catalog returns editable model identifiers`() = runTest {
        server.start()
        server.enqueue(jsonResponse("""{"object":"list","data":[{"id":"gpt-image-1"},{"id":"gpt-4.1"}]}"""))

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
        val recorded = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))

        assertEquals(listOf("gpt-image-1", "gpt-4.1"), result.models.map { it.id })
        assertEquals("GET", recorded.method)
        assertEquals("/v1/models", recorded.path)
        assertEquals("Bearer token-value", recorded.getHeader("Authorization"))
    }

    @Test
    fun `connection test reports rejected credentials without response details`() = runTest {
        server.start()
        server.enqueue(MockResponse().setResponseCode(401).setBody("credential details"))

        val result = provider.testConnection(AiCapability.IMAGE_TO_IMAGE, config())
            as ProviderConnectionTestResult.Unavailable

        assertEquals(ImageGenerationErrorCategory.AUTHENTICATION, result.error.category)
        assertNull(result.error.safeMessage)
        assertFalse(result.error.requestMayHaveBeenAccepted)
    }

    @Test
    fun `config supplies the OpenAI default URL and rejects a blank model`() {
        val config = OpenAiImageConfig(apiToken = SecretValue("token"), model = "gpt-image-1")

        assertEquals("https://api.openai.com/v1", config.baseUrl)
        assertFailsWith<IllegalArgumentException> {
            OpenAiImageConfig(apiToken = SecretValue("token"), model = "   ")
        }
    }

    @Test
    fun `unauthorized response maps to authentication failure without credentials`() = runTest {
        server.start()
        server.enqueue(MockResponse().setResponseCode(401).setBody("secret token must not escape"))

        val terminal = provider.generate(request(), config()).toList().lastProviderCompleted()
        val failure = terminal.failure()

        assertEquals(ImageGenerationErrorCategory.AUTHENTICATION, failure.error.category)
        assertEquals(RetryAdvice.DO_NOT_RETRY, failure.error.retryAdvice)
        assertEquals(401, failure.error.httpStatus)
        assertFalse(failure.error.requestMayHaveBeenAccepted)
        assertNull(failure.error.safeMessage)
        assertNull(failure.error.diagnosticCode)
    }

    @Test
    fun `rate limit maps retry after seconds and definite rejection`() = runTest {
        server.start()
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "7")
                .addHeader("x-request-id", "req-rate-limit")
                .setBody("rate limited"),
        )

        val failure = provider.generate(request(), config()).toList().lastProviderCompleted().failure().error

        assertEquals(ImageGenerationErrorCategory.RATE_LIMITED, failure.category)
        assertEquals(RetryAdvice.SAFE_TO_RETRY, failure.retryAdvice)
        assertEquals(7_000, failure.retryAfterMillis)
        assertEquals("req-rate-limit", failure.providerRequestId)
        assertTrue(failure.definitelyUnprocessed)
        assertFalse(failure.requestMayHaveBeenAccepted)
    }

    @Test
    fun `malformed response maps to malformed response failure`() = runTest {
        server.start()
        server.enqueue(jsonResponse("not-json"))

        val failure = provider.generate(request(), config()).toList().lastProviderCompleted().failure().error

        assertEquals(ImageGenerationErrorCategory.MALFORMED_RESPONSE, failure.category)
        assertEquals(RetryAdvice.DO_NOT_RETRY, failure.retryAdvice)
        assertTrue(failure.requestMayHaveBeenAccepted)
    }

    @Test
    fun `successful response accepts only JSON media types`() = runTest {
        server.start()
        val body = """{"data":[{"b64_json":"${PNG_BYTES.base64()}"}]}""".encodeToByteArray()

        listOf(null, "text/plain").forEach { contentType ->
            val headers = contentType?.let { mapOf("content-type" to it) }.orEmpty()
            val result = OpenAiImageProvider(ResponseTransport(HttpResponse(200, headers, body)))
                .generate(request(), config()).toList().lastProviderCompleted().result
            val category = (result as? com.alpha.ai.imagegeneration.GenerationResult.Failure)?.error?.category
            assertEquals(ImageGenerationErrorCategory.MALFORMED_RESPONSE, category, contentType)
        }

        val accepted = OpenAiImageProvider(
            ResponseTransport(HttpResponse(200, mapOf("content-type" to "application/vnd.openai.image+json; charset=utf-8"), body)),
        ).generate(request(), config()).toList().lastProviderCompleted().successImage()
        assertContentEquals(PNG_BYTES, accepted.bytes)
    }

    @Test
    fun `empty and multiple image results are rejected`() = runTest {
        server.start()
        server.enqueue(jsonResponse("""{"data":[]}"""))
        val empty = provider.generate(request(), config()).toList().lastProviderCompleted().failure().error
        assertEquals(ImageGenerationErrorCategory.MALFORMED_RESPONSE, empty.category)

        server.enqueue(jsonResponse("""{"data":[{"b64_json":"${PNG_BYTES.base64()}"},{"b64_json":"${PNG_BYTES.base64()}"}]}"""))
        val multiple = provider.generate(request(), config()).toList().lastProviderCompleted().failure().error
        assertEquals(ImageGenerationErrorCategory.MALFORMED_RESPONSE, multiple.category)
    }

    @Test
    fun `decoded image larger than ceiling is rejected before result`() = runTest {
        server.start()
        val oversized = ByteArray(32 * 1024 * 1024) { 1 }.base64()
        server.enqueue(jsonResponse("""{"data":[{"b64_json":"$oversized"}]}"""))

        val failure = provider.generate(request(), config()).toList().lastProviderCompleted().failure().error

        assertEquals(ImageGenerationErrorCategory.MALFORMED_RESPONSE, failure.category)
        assertTrue(failure.requestMayHaveBeenAccepted)
    }

    @Test
    fun `cancellation after generating stage cancels active call`() = runTest {
        server.start()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val collection = async {
            provider.generate(request(), config()).collect()
        }
        server.takeRequest(1, TimeUnit.SECONDS)
        collection.cancel()
        assertTrue(collection.isCancelled)
    }

    @Test
    fun `prepare failure is definitely before dispatch`() = runTest {
        server.start()

        val failure = OpenAiImageProvider(PrepareFailureTransport()).generate(request(), config())
            .toList().lastProviderCompleted().failure().error

        assertEquals(ImageGenerationErrorCategory.NETWORK, failure.category)
        assertEquals(RetryAdvice.SAFE_TO_RETRY, failure.retryAdvice)
        assertFalse(failure.requestMayHaveBeenAccepted)
    }

    @Test
    fun `synchronous start failure is definitely before dispatch`() = runTest {
        server.start()

        val failure = OpenAiImageProvider(StartFailureTransport()).generate(request(), config())
            .toList().lastProviderCompleted().failure().error

        assertEquals(ImageGenerationErrorCategory.NETWORK, failure.category)
        assertEquals(RetryAdvice.SAFE_TO_RETRY, failure.retryAdvice)
        assertFalse(failure.requestMayHaveBeenAccepted)
    }

    @Test
    fun `await failure after successful start is accepted and ambiguous`() = runTest {
        server.start()

        val failure = OpenAiImageProvider(AwaitFailureTransport()).generate(request(), config())
            .toList().lastProviderCompleted().failure().error

        assertEquals(ImageGenerationErrorCategory.NETWORK, failure.category)
        assertEquals(RetryAdvice.AMBIGUOUS, failure.retryAdvice)
        assertTrue(failure.requestMayHaveBeenAccepted)
    }

    @Test
    fun `server failure after dispatch is accepted and ambiguous`() = runTest {
        server.start()
        server.enqueue(MockResponse().setResponseCode(503).setBody("temporarily unavailable"))

        val failure = provider.generate(request(), config()).toList().lastProviderCompleted().failure().error

        assertEquals(ImageGenerationErrorCategory.SERVER, failure.category)
        assertEquals(RetryAdvice.AMBIGUOUS, failure.retryAdvice)
        assertTrue(failure.requestMayHaveBeenAccepted)
    }

    @Test
    fun `facade preserves prepare failure as definitely unaccepted after persisted upload fence`() = runTest {
        server.start()

        val events = facade(PrepareFailureTransport()).generate(request(), config()).toList()
        val failure = (events.last() as ImageGenerationEvent.Completed).failure().error

        assertEquals(RetryAdvice.SAFE_TO_RETRY, failure.retryAdvice)
        assertFalse(failure.requestMayHaveBeenAccepted)
        assertEquals(
            listOf(GenerationStage.VALIDATING_CONFIGURATION, GenerationStage.PREPARING_INPUT, GenerationStage.UPLOADING),
            events.filterIsInstance<ImageGenerationEvent.Stage>().map { it.value },
        )
    }

    @Test
    fun `facade preserves synchronous start failure as definitely unaccepted`() = runTest {
        server.start()

        val failure = (facade(StartFailureTransport()).generate(request(), config()).toList().last() as ImageGenerationEvent.Completed)
            .failure().error

        assertEquals(RetryAdvice.SAFE_TO_RETRY, failure.retryAdvice)
        assertFalse(failure.requestMayHaveBeenAccepted)
    }

    @Test
    fun `facade preserves received rate limit as definitely rejected`() = runTest {
        server.start()
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "7"))

        val events = ImageGeneration.builder().register(OpenAiImageProvider()).build()
            .generate(request(), config()).toList()
        val failure = (events.last() as ImageGenerationEvent.Completed).failure().error

        assertEquals(RetryAdvice.SAFE_TO_RETRY, failure.retryAdvice)
        assertTrue(failure.definitelyUnprocessed)
        assertFalse(failure.requestMayHaveBeenAccepted)
        assertEquals(GenerationStage.GENERATING, events.filterIsInstance<ImageGenerationEvent.Stage>().last().value)
    }

    @Test
    fun `facade marks post start transport failure as accepted and ambiguous`() = runTest {
        server.start()

        val events = facade(AwaitFailureTransport()).generate(request(), config()).toList()
        val failure = (events.last() as ImageGenerationEvent.Completed).failure().error

        assertEquals(RetryAdvice.AMBIGUOUS, failure.retryAdvice)
        assertTrue(failure.requestMayHaveBeenAccepted)
        assertEquals(GenerationStage.GENERATING, events.filterIsInstance<ImageGenerationEvent.Stage>().last().value)
    }

    private fun config() = OpenAiImageConfig(
        apiToken = SecretValue("token-value"),
        model = "gpt-image-1",
        baseUrl = server.url("/v1").toString(),
        allowInsecureHttp = true,
    )

    private fun facade(transport: HttpTransport) = ImageGeneration.builder()
        .register(OpenAiImageProvider(transport))
        .build()

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body)

    private fun ProviderGenerationEvent.Completed.successImage() =
        (result as com.alpha.ai.imagegeneration.GenerationResult.Success).image

    private fun ProviderGenerationEvent.Completed.failure() =
        (result as com.alpha.ai.imagegeneration.GenerationResult.Failure)

    private fun ImageGenerationEvent.Completed.failure() =
        (result as com.alpha.ai.imagegeneration.GenerationResult.Failure)

    private val PNG_BYTES = byteArrayOf(
        0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
    )

    private class PrepareFailureTransport : HttpTransport {
        override fun prepare(request: HttpRequest, limitBytes: Long): PreparedHttpCall =
            throw ImageGenerationTransportException("prepare failed")

    }

    private class StartFailureTransport : HttpTransport {
        override fun prepare(request: HttpRequest, limitBytes: Long) = object : PreparedHttpCall {
            override fun start(): RunningHttpCall = throw ImageGenerationTransportException("start failed")
        }

    }

    private class AwaitFailureTransport : HttpTransport {
        override fun prepare(request: HttpRequest, limitBytes: Long) = object : PreparedHttpCall {
            override fun start(): RunningHttpCall = object : RunningHttpCall {
                override suspend fun await(): HttpResponse =
                    throw ImageGenerationTransportException("callback failed")

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
