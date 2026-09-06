package com.alpha.ai.imagegeneration.provider.gemini

import com.alpha.ai.imagegeneration.AiCapability
import com.alpha.ai.imagegeneration.CancellationSemantics
import com.alpha.ai.imagegeneration.GenerateImageRequest
import com.alpha.ai.imagegeneration.GeneratedImage
import com.alpha.ai.imagegeneration.GenerationResult
import com.alpha.ai.imagegeneration.GenerationStage
import com.alpha.ai.imagegeneration.IdempotencySupport
import com.alpha.ai.imagegeneration.ImageGenerationError
import com.alpha.ai.imagegeneration.ImageGenerationErrorCategory
import com.alpha.ai.imagegeneration.ImageGenerationProvider
import com.alpha.ai.imagegeneration.ProviderCapabilities
import com.alpha.ai.imagegeneration.ProviderConnectionTestResult
import com.alpha.ai.imagegeneration.ProviderDescriptor
import com.alpha.ai.imagegeneration.ProviderGenerationEvent
import com.alpha.ai.imagegeneration.ProviderModelCatalogRequest
import com.alpha.ai.imagegeneration.ProviderModelCatalogResult
import com.alpha.ai.imagegeneration.RequestPhase
import com.alpha.ai.imagegeneration.RetryAdvice
import com.alpha.ai.imagegeneration.internal.http.BaseUrlValidator
import com.alpha.ai.imagegeneration.internal.http.HttpRequest
import com.alpha.ai.imagegeneration.internal.http.HttpResponse
import com.alpha.ai.imagegeneration.internal.http.HttpTransport
import com.alpha.ai.imagegeneration.internal.http.ImageBytesValidator
import com.alpha.ai.imagegeneration.internal.http.ImageGenerationTransportException
import com.alpha.ai.imagegeneration.internal.http.ResponseLimits
import com.alpha.ai.imagegeneration.internal.http.defaultHttpTransport
import com.alpha.ai.imagegeneration.internal.http.hasJsonContentType
import com.alpha.ai.imagegeneration.internal.http.parseRetryAfter
import com.alpha.ai.imagegeneration.internal.http.readImageSource
import com.alpha.ai.imagegeneration.internal.http.toProviderUrlBuilder
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okio.IOException

class GeminiNanoBananaProvider private constructor(
    private val transport: HttpTransport,
) : ImageGenerationProvider<GeminiImageConfig> {
    constructor() : this(defaultHttpTransport())

    internal constructor(transport: HttpTransport, @Suppress("UNUSED_PARAMETER") testOnly: Unit = Unit) : this(transport)

    override val descriptor = ProviderDescriptor(
        id = GeminiImageConfig.PROVIDER_ID,
        displayName = "Gemini Nano Banana",
        supportedProtocolVersions = setOf(GeminiImageConfig.PROTOCOL_VERSION),
        capabilities = ProviderCapabilities(
            supportedInputMimeTypes = SUPPORTED_INPUT_MIME_TYPES,
            maxInputBytes = MAX_INPUT_BYTES,
            idempotencySupport = IdempotencySupport.NONE,
            safeRetryPhases = setOf(RequestPhase.BEFORE_DISPATCH),
            safeRejectedHttpStatuses = setOf(429),
            reliableDispatchBoundary = true,
            cancellationSemantics = CancellationSemantics.BEST_EFFORT,
        ),
    )

    override val configType = GeminiImageConfig::class

    override suspend fun testConnection(
        capability: AiCapability,
        config: GeminiImageConfig,
    ): ProviderConnectionTestResult = probeGeminiConnection(transport, capability, config)

    override suspend fun listModels(
        request: ProviderModelCatalogRequest,
    ): ProviderModelCatalogResult = listGeminiModels(transport, request)

    override fun generate(
        request: GenerateImageRequest,
        config: GeminiImageConfig,
    ): Flow<ProviderGenerationEvent> = flow {
        emit(ProviderGenerationEvent.Stage(GenerationStage.PREPARING_INPUT))

        val sourceBytes = try {
            readSource(request)
        } catch (_: ImageGenerationTransportException) {
            emit(failure(ImageGenerationErrorCategory.INVALID_REQUEST, RetryAdvice.DO_NOT_RETRY, false))
            return@flow
        }

        emit(ProviderGenerationEvent.Stage(GenerationStage.UPLOADING))

        val endpoint: Url
        val httpRequest: HttpRequest
        try {
            endpoint = BaseUrlValidator.validate(config.baseUrl, config.allowInsecureHttp)
                .toProviderUrlBuilder()
                .addPathSegment("models")
                .addPathSegment("${config.model}:generateContent")
                .build()
            httpRequest = config.apiToken.use { token ->
                buildRequest(request, config, sourceBytes, endpoint, token.concatToString())
            }
        } catch (_: ImageGenerationTransportException) {
            emit(failure(ImageGenerationErrorCategory.CONFIGURATION, RetryAdvice.DO_NOT_RETRY, false))
            return@flow
        } catch (_: IOException) {
            emit(failure(ImageGenerationErrorCategory.NETWORK, RetryAdvice.SAFE_TO_RETRY, false))
            return@flow
        }

        val prepared = try {
            transport.prepare(httpRequest, ResponseLimits().jsonBytes)
        } catch (_: ImageGenerationTransportException) {
            emit(failure(ImageGenerationErrorCategory.NETWORK, RetryAdvice.SAFE_TO_RETRY, false))
            return@flow
        }

        // A synchronous start failure is still before the provider can receive the request.
        val running = try {
            prepared.start()
        } catch (_: ImageGenerationTransportException) {
            emit(failure(ImageGenerationErrorCategory.NETWORK, RetryAdvice.SAFE_TO_RETRY, false))
            return@flow
        }

        val response = try {
            emit(ProviderGenerationEvent.Stage(GenerationStage.GENERATING))
            running.await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: ImageGenerationTransportException) {
            emit(transportFailure(error.message))
            return@flow
        } finally {
            running.cancelIfActive()
        }

        emit(parseResponse(response, config))
    }

    private suspend fun readSource(request: GenerateImageRequest): ByteArray =
        readImageSource(request.source, SUPPORTED_INPUT_MIME_TYPES, MAX_INPUT_BYTES)

    private fun buildRequest(
        request: GenerateImageRequest,
        config: GeminiImageConfig,
        sourceBytes: ByteArray,
        endpoint: Url,
        token: String,
    ): HttpRequest {
        val body = JSON.encodeToString(
            GeminiGenerateContentRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(
                            GeminiPart(text = request.prompt),
                            GeminiPart(inlineData = GeminiInlineData(mimeType = request.source.mimeType, data = kotlin.io.encoding.Base64.encode(sourceBytes))),
                        ),
                    ),
                ),
                generationConfig = GeminiGenerationConfig(responseModalities = listOf("TEXT", "IMAGE")),
            ),
        ).encodeToByteArray()
        return HttpRequest.post(
            url = endpoint,
            headers = mapOf(
                "x-goog-api-key" to token,
                "Content-Type" to "application/json",
            ),
            body = body,
        )
    }

    private fun parseResponse(response: HttpResponse, config: GeminiImageConfig): ProviderGenerationEvent.Completed {
        val requestId = response.headers["x-request-id"] ?: response.headers["x-goog-request-id"]
        if (response.status == 401 || response.status == 403) {
            return failure(ImageGenerationErrorCategory.AUTHENTICATION, RetryAdvice.DO_NOT_RETRY, false, response.status, providerRequestId = requestId)
        }
        if (response.status == 429) {
            return failure(
                ImageGenerationErrorCategory.RATE_LIMITED,
                RetryAdvice.SAFE_TO_RETRY,
                false,
                response.status,
                parseRetryAfter(response.headers["retry-after"]),
                requestId,
                definitelyUnprocessed = true,
            )
        }
        if (response.status == 400) {
            return failure(ImageGenerationErrorCategory.INVALID_REQUEST, RetryAdvice.DO_NOT_RETRY, false, response.status, providerRequestId = requestId, definitelyUnprocessed = true)
        }
        if (response.status in 500..599) {
            return failure(ImageGenerationErrorCategory.SERVER, RetryAdvice.AMBIGUOUS, true, response.status, providerRequestId = requestId)
        }
        if (response.status !in 200..299) {
            return failure(ImageGenerationErrorCategory.SERVER, RetryAdvice.DO_NOT_RETRY, true, response.status, providerRequestId = requestId)
        }
        if (!response.hasJsonContentType()) {
            return failure(
                ImageGenerationErrorCategory.MALFORMED_RESPONSE,
                RetryAdvice.DO_NOT_RETRY,
                true,
                response.status,
                providerRequestId = requestId,
            )
        }

        return try {
            val root = JSON.parseToJsonElement(response.body.decodeToString()).jsonObject
            val parsed = JSON.decodeFromJsonElement<GeminiGenerateContentResponse>(root)
            if (!parsed.promptFeedback?.blockReason.isNullOrBlank() || parsed.candidates.any { it.finishReason?.isBlocked() == true }) {
                failure(ImageGenerationErrorCategory.CONTENT_POLICY, RetryAdvice.DO_NOT_RETRY, true, response.status, providerRequestId = requestId)
            } else {
                val image = parsed.candidates.asSequence()
                    .flatMap { candidate -> candidate.content?.parts.orEmpty().asSequence() }
                    .mapNotNull { part -> part.inlineData ?: part.snakeInlineData }
                    .mapNotNull { data -> decodeImage(data) }
                    .firstOrNull()
                    ?: throw ImageGenerationTransportException("Missing image result")
                ProviderGenerationEvent.Completed(GenerationResult.Success(GeneratedImage(image.bytes, image.mimeType, providerId = config.providerId, model = config.model, providerRequestId = requestId)))
            }
        } catch (_: Exception) {
            failure(ImageGenerationErrorCategory.MALFORMED_RESPONSE, RetryAdvice.DO_NOT_RETRY, true, response.status, providerRequestId = requestId)
        }
    }

    private fun decodeImage(data: GeminiInlineData): com.alpha.ai.imagegeneration.internal.http.ValidatedImage? {
        val base64 = data.data?.takeIf { it.isNotBlank() } ?: return null
        return try {
            ImageBytesValidator.decodeBase64(base64, data.mimeType ?: data.snakeMimeType, ResponseLimits().imageBytes)
        } catch (_: ImageGenerationTransportException) {
            null
        }
    }

    private fun String.isBlocked() = uppercase() in BLOCKED_FINISH_REASONS

    private fun transportFailure(message: String?) = failure(
        if (message?.contains("timeout", ignoreCase = true) == true) ImageGenerationErrorCategory.TIMEOUT else ImageGenerationErrorCategory.NETWORK,
        RetryAdvice.AMBIGUOUS,
        true,
    )

    private fun failure(
        category: ImageGenerationErrorCategory,
        advice: RetryAdvice,
        requestMayHaveBeenAccepted: Boolean,
        status: Int? = null,
        retryAfterMillis: Long? = null,
        providerRequestId: String? = null,
        definitelyUnprocessed: Boolean = false,
    ) = ProviderGenerationEvent.Completed(GenerationResult.Failure(ImageGenerationError(category, advice, requestMayHaveBeenAccepted, status, retryAfterMillis, providerRequestId, definitelyUnprocessed)))

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val SUPPORTED_INPUT_MIME_TYPES = setOf("image/png", "image/jpeg", "image/webp")
        val BLOCKED_FINISH_REASONS = setOf("SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "IMAGE_SAFETY")
        const val MAX_INPUT_BYTES = 12L * 1024 * 1024
    }
}
