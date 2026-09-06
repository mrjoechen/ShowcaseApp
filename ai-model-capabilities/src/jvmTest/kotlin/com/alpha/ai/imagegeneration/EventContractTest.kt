package com.alpha.ai.imagegeneration

import com.alpha.ai.imagegeneration.internal.ProviderFailureMapper
import com.alpha.ai.imagegeneration.internal.http.HttpRequest
import com.alpha.ai.imagegeneration.internal.http.OkHttpTransport
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import io.ktor.http.Url

class EventContractTest {
    @Test
    fun `second terminal is rejected and never reaches caller`() = runTest {
        val events = client(
            events = listOf(
                ProviderGenerationEvent.Completed(GenerationResult.Success(IMAGE)),
                ProviderGenerationEvent.Completed(GenerationResult.Success(IMAGE)),
            ),
        ).generate(request(), FakeConfig()).toList()

        assertEquals(1, events.filterIsInstance<ImageGenerationEvent.Completed>().size)
    }

    @Test
    fun `first terminal aborts a hanging provider without exposing internal cancellation`() = runTest {
        val provider = object : ImageGenerationProvider<FakeConfig> {
            override val descriptor = FakeProvider().descriptor
            override val configType = FakeConfig::class
            override fun generate(request: GenerateImageRequest, config: FakeConfig): Flow<ProviderGenerationEvent> = flow {
                emit(ProviderGenerationEvent.Completed(GenerationResult.Success(IMAGE)))
                awaitCancellation()
            }
        }

        val events = withTimeout(1_000) {
            ImageGeneration.builder().register(provider).build().generate(request(), FakeConfig()).toList()
        }

        assertEquals(1, events.filterIsInstance<ImageGenerationEvent.Completed>().size)
    }

    @Test
    fun `provider ending without terminal emits malformed response failure`() = runTest {
        val events = client(events = listOf(ProviderGenerationEvent.Stage(GenerationStage.PREPARING_INPUT)))
            .generate(request(), FakeConfig())
            .toList()

        assertEquals(
            ImageGenerationErrorCategory.MALFORMED_RESPONSE,
            events.lastCompleted().result.failureOrNull()?.error?.category,
        )
    }

    @Test
    fun `reliable provider success requires generating after upload fence`() = runTest {
        val capabilities = ProviderCapabilities.conservative(setOf(PNG), 1024).copy(reliableDispatchBoundary = true)
        val events = client(
            events = listOf(
                ProviderGenerationEvent.Stage(GenerationStage.UPLOADING),
                ProviderGenerationEvent.Completed(GenerationResult.Success(IMAGE)),
            ),
            capabilities = capabilities,
        ).generate(request(), FakeConfig()).toList()

        assertEquals(
            ImageGenerationErrorCategory.MALFORMED_RESPONSE,
            events.lastCompleted().result.failureOrNull()?.error?.category,
        )
    }

    @Test
    fun `duplicate stage is replaced by malformed response terminal`() = runTest {
        val events = client(
            events = listOf(
                ProviderGenerationEvent.Stage(GenerationStage.PREPARING_INPUT),
                ProviderGenerationEvent.Stage(GenerationStage.PREPARING_INPUT),
                ProviderGenerationEvent.Completed(GenerationResult.Success(IMAGE)),
            ),
        ).generate(request(), FakeConfig()).toList()

        assertEquals(1, events.filterIsInstance<ImageGenerationEvent.Completed>().size)
        assertEquals(
            ImageGenerationErrorCategory.MALFORMED_RESPONSE,
            events.lastCompleted().result.failureOrNull()?.error?.category,
        )
    }

    @Test
    fun `provider exception is converted to a redacted terminal failure`() = runTest {
        val token = "token-value"
        val events = client(exception = IllegalStateException("Authorization: Bearer $token image=QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo="))
            .generate(request(), FakeConfig(apiToken = SecretValue(token)))
            .toList()

        val error = events.lastCompleted().result.failureOrNull()?.error
        assertEquals(ImageGenerationErrorCategory.SERVER, error?.category)
        assertFalse(error?.safeMessage.orEmpty().contains(token))
        assertFalse(error?.safeMessage.orEmpty().contains("QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo="))
    }

    @Test
    fun `callback failure after generating remains a persisted non-cancellation terminal`() = runTest {
        val transport = OkHttpTransport(callFactory = okhttp3.Call.Factory { httpRequest ->
            object : okhttp3.Call by okhttp3.OkHttpClient().newCall(httpRequest) {
                override fun enqueue(responseCallback: okhttp3.Callback) {
                    responseCallback.onFailure(this, IOException("connection failed"))
                }
            }
        })
        val provider = object : ImageGenerationProvider<FakeConfig> {
            override val descriptor = FakeProvider().descriptor
            override val configType = FakeConfig::class
            override fun generate(request: GenerateImageRequest, config: FakeConfig): Flow<ProviderGenerationEvent> = flow {
                emit(ProviderGenerationEvent.Stage(GenerationStage.PREPARING_INPUT))
                emit(ProviderGenerationEvent.Stage(GenerationStage.UPLOADING))
                emit(ProviderGenerationEvent.Stage(GenerationStage.GENERATING))
                transport.prepare(HttpRequest.get(Url("https://api.example.test/v1")), 16).start().await()
            }
        }
        val persisted = mutableListOf<ImageGenerationEvent>()

        ImageGeneration.builder().register(provider).build().generate(request(), FakeConfig()).collect { persisted += it }

        assertEquals(GenerationStage.GENERATING, persisted.filterIsInstance<ImageGenerationEvent.Stage>().last().value)
        assertEquals(ImageGenerationErrorCategory.SERVER, persisted.lastCompleted().result.failureOrNull()?.error?.category)
    }

    @Test
    fun `provider exception while creating its flow is converted to a terminal failure`() = runTest {
        val provider = object : ImageGenerationProvider<FakeConfig> {
            override val descriptor = FakeProvider().descriptor
            override val configType = FakeConfig::class

            override fun generate(request: GenerateImageRequest, config: FakeConfig): Flow<ProviderGenerationEvent> =
                throw IllegalStateException("provider setup failed")
        }

        val events = ImageGeneration.builder().register(provider).build().generate(request(), FakeConfig()).toList()

        assertEquals(ImageGenerationErrorCategory.SERVER, events.lastCompleted().result.failureOrNull()?.error?.category)
    }

    @Test
    fun `caller cancellation is rethrown unchanged`() = runTest {
        val cancellation = CancellationException("caller stopped collection")

        val thrown = assertFailsWith<CancellationException> {
            client(events = listOf(ProviderGenerationEvent.Stage(GenerationStage.PREPARING_INPUT)))
                .generate(request(), FakeConfig())
                .collect { throw cancellation }
        }

        assertEquals(cancellation, thrown)
    }

    @Test
    fun `downstream collector exception propagates unchanged`() = runTest {
        val downstreamFailure = IllegalStateException("Room stage write failed")

        val thrown = assertFailsWith<IllegalStateException> {
            client(events = listOf(ProviderGenerationEvent.Stage(GenerationStage.PREPARING_INPUT)))
                .generate(request(), FakeConfig())
                .collect { throw downstreamFailure }
        }

        assertEquals(downstreamFailure, thrown)
    }

    @Test
    fun `provider metadata and signed remote URL are removed from public event`() = runTest {
        val image = IMAGE.copy(
            providerRequestId = "request-1",
            remoteUrl = "https://images.test/output.png?X-Amz-Signature=secret",
            metadata = mapOf("allowed" to "visible", "internal" to "Bearer token-value"),
        )
        val provider = eventProvider(
            events = listOf(ProviderGenerationEvent.Completed(GenerationResult.Success(image))),
            safeMetadataKeys = setOf("allowed"),
        )

        val result = ImageGeneration.builder().register(provider).build().generate(request(), FakeConfig()).toList()
            .lastCompleted().result as GenerationResult.Success

        assertEquals(mapOf("allowed" to "visible"), result.image.metadata)
        assertNull(result.image.remoteUrl)
        assertEquals("request-1", result.image.providerRequestId)
    }

    @Test
    fun `retained remote URL must be a parsed credential free network URL`() = runTest {
        val unsafeUrls = listOf(
            "ftp://images.test/output.png",
            "https://user:password@images.test/output.png",
            "https://images.test/output.png?download=1",
            "https://images.test/output.png#preview",
        )

        unsafeUrls.forEach { remoteUrl ->
            val image = IMAGE.copy(remoteUrl = remoteUrl)
            val result = client(events = listOf(ProviderGenerationEvent.Completed(GenerationResult.Success(image))))
                .generate(request(), FakeConfig()).toList().lastCompleted().result as GenerationResult.Success

            assertNull(result.image.remoteUrl, remoteUrl)
        }
    }

    @Test
    fun `retained HTTP URL requires explicit insecure profile permission`() = runTest {
        val remoteUrl = "http://images.test/output.png"
        val image = IMAGE.copy(remoteUrl = remoteUrl)
        val client = client(events = listOf(ProviderGenerationEvent.Completed(GenerationResult.Success(image))))

        val rejected = client.generate(request(), FakeConfig(allowInsecureHttp = false)).toList()
            .lastCompleted().result as GenerationResult.Success
        val allowed = client.generate(request(), FakeConfig(allowInsecureHttp = true)).toList()
            .lastCompleted().result as GenerationResult.Success

        assertNull(rejected.image.remoteUrl)
        assertEquals(remoteUrl, allowed.image.remoteUrl)
    }

    @Test
    fun `safe HTTPS remote URL is retained`() = runTest {
        val remoteUrl = "https://images.test/output.png"
        val image = IMAGE.copy(remoteUrl = remoteUrl)

        val result = client(events = listOf(ProviderGenerationEvent.Completed(GenerationResult.Success(image))))
            .generate(request(), FakeConfig()).toList().lastCompleted().result as GenerationResult.Success

        assertEquals(remoteUrl, result.image.remoteUrl)
    }

    @Test
    fun `unsafe provider request id is dropped`() = runTest {
        val image = IMAGE.copy(providerRequestId = "request\u0000id")

        val result = client(events = listOf(ProviderGenerationEvent.Completed(GenerationResult.Success(image))))
            .generate(request(), FakeConfig()).toList().lastCompleted().result as GenerationResult.Success

        assertNull(result.image.providerRequestId)
    }

    @Test
    fun `preview attributed to another provider is malformed`() = runTest {
        val wrongOwner = IMAGE.copy(providerId = ProviderId("another-provider"))

        val events = client(
            events = listOf(
                ProviderGenerationEvent.Preview(wrongOwner),
                ProviderGenerationEvent.Completed(GenerationResult.Success(IMAGE)),
            ),
        ).generate(request(), FakeConfig()).toList()

        assertEquals(ImageGenerationErrorCategory.MALFORMED_RESPONSE, events.lastCompleted().result.failureOrNull()?.error?.category)
        assertTrue(events.none { it is ImageGenerationEvent.Preview })
    }

    @Test
    fun `success attributed to another provider is malformed`() = runTest {
        val wrongOwner = IMAGE.copy(providerId = ProviderId("another-provider"))

        val events = client(events = listOf(ProviderGenerationEvent.Completed(GenerationResult.Success(wrongOwner))))
            .generate(request(), FakeConfig()).toList()

        assertEquals(ImageGenerationErrorCategory.MALFORMED_RESPONSE, events.lastCompleted().result.failureOrNull()?.error?.category)
    }

    @Test
    fun `unknown provider retry hint is downgraded to ambiguous`() {
        val error = ProviderFailureMapper.merge(
            capabilities = ProviderCapabilities.conservative(),
            providerAdvice = RetryAdvice.SAFE_TO_RETRY,
            phase = RequestPhase.IN_FLIGHT,
        )

        assertEquals(RetryAdvice.AMBIGUOUS, error)
    }

    @Test
    fun `declared rejected status preserves retry advice before dispatch`() {
        val error = ProviderFailureMapper.merge(
            capabilities = ProviderCapabilities(
                supportedInputMimeTypes = emptySet(),
                maxInputBytes = 0,
                safeRejectedHttpStatuses = setOf(429),
            ),
            providerAdvice = RetryAdvice.SAFE_TO_RETRY,
            phase = RequestPhase.IN_FLIGHT,
            definitelyUnprocessed = true,
            httpStatus = 429,
        )

        assertEquals(RetryAdvice.SAFE_TO_RETRY, error)
    }

    @Test
    fun `declared rejected status is ambiguous without an explicit unprocessed marker`() = runTest {
        val failure = ImageGenerationError(
            category = ImageGenerationErrorCategory.RATE_LIMITED,
            retryAdvice = RetryAdvice.SAFE_TO_RETRY,
            requestMayHaveBeenAccepted = false,
            httpStatus = 429,
            definitelyUnprocessed = false,
        )
        val capabilities = ProviderCapabilities(
            supportedInputMimeTypes = emptySet(),
            maxInputBytes = 0,
            safeRejectedHttpStatuses = setOf(429),
        )

        val result = client(
            events = listOf(
                ProviderGenerationEvent.Stage(GenerationStage.UPLOADING),
                ProviderGenerationEvent.Stage(GenerationStage.GENERATING),
                ProviderGenerationEvent.Completed(GenerationResult.Failure(failure)),
            ),
            capabilities = capabilities,
        ).generate(request(), FakeConfig()).toList().lastCompleted().result.failureOrNull()

        assertEquals(RetryAdvice.AMBIGUOUS, result?.error?.retryAdvice)
    }

    @Test
    fun `explicitly unprocessed declared rejection preserves safe retry`() = runTest {
        val failure = ImageGenerationError(
            category = ImageGenerationErrorCategory.RATE_LIMITED,
            retryAdvice = RetryAdvice.SAFE_TO_RETRY,
            requestMayHaveBeenAccepted = false,
            httpStatus = 429,
            definitelyUnprocessed = true,
        )
        val capabilities = ProviderCapabilities(
            supportedInputMimeTypes = emptySet(),
            maxInputBytes = 0,
            safeRejectedHttpStatuses = setOf(429),
        )

        val result = client(
            events = listOf(ProviderGenerationEvent.Completed(GenerationResult.Failure(failure))),
            capabilities = capabilities,
        ).generate(request(), FakeConfig()).toList().lastCompleted().result.failureOrNull()

        assertEquals(RetryAdvice.SAFE_TO_RETRY, result?.error?.retryAdvice)
    }

    @Test
    fun `provider supplied response body is not exposed as a safe message`() = runTest {
        val failure = ImageGenerationError(
            category = ImageGenerationErrorCategory.SERVER,
            retryAdvice = RetryAdvice.AMBIGUOUS,
            requestMayHaveBeenAccepted = false,
            safeMessage = "Authorization: Basic dXNlcjpwYXNz full response body: {\"detail\":\"secret\"}",
        )

        val result = client(events = listOf(ProviderGenerationEvent.Completed(GenerationResult.Failure(failure))))
            .generate(request(), FakeConfig()).toList().lastCompleted().result.failureOrNull()

        assertNull(result?.error?.safeMessage)
    }

    private fun client(
        events: List<ProviderGenerationEvent> = emptyList(),
        exception: Throwable? = null,
        capabilities: ProviderCapabilities = ProviderCapabilities.conservative(setOf(PNG), 1024),
    ): ImageGenerationClient = ImageGeneration.builder()
        .register(eventProvider(events = events, exception = exception, capabilities = capabilities))
        .build()

    private fun eventProvider(
        events: List<ProviderGenerationEvent>,
        exception: Throwable? = null,
        safeMetadataKeys: Set<String> = emptySet(),
        capabilities: ProviderCapabilities = ProviderCapabilities.conservative(setOf(PNG), 1024),
    ): ImageGenerationProvider<FakeConfig> = object : ImageGenerationProvider<FakeConfig> {
        override val descriptor = FakeProvider().descriptor.copy(
            capabilities = capabilities,
            safeMetadataKeys = safeMetadataKeys,
        )
        override val configType = FakeConfig::class

        override fun generate(request: GenerateImageRequest, config: FakeConfig): Flow<ProviderGenerationEvent> = flow {
            exception?.let { throw it }
            events.forEach { emit(it) }
        }
    }
}
