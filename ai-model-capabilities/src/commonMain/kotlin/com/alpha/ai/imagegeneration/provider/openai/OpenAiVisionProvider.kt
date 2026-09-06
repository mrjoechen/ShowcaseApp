package com.alpha.ai.imagegeneration.provider.openai

import com.alpha.ai.imagegeneration.AiCapability
import com.alpha.ai.imagegeneration.CancellationSemantics
import com.alpha.ai.imagegeneration.GenerationResult
import com.alpha.ai.imagegeneration.GenerationStage
import com.alpha.ai.imagegeneration.IdempotencySupport
import com.alpha.ai.imagegeneration.ImageGenerationError
import com.alpha.ai.imagegeneration.ImageGenerationErrorCategory
import com.alpha.ai.imagegeneration.ImageUnderstandingOutput
import com.alpha.ai.imagegeneration.ImageUnderstandingProvider
import com.alpha.ai.imagegeneration.ImageUnderstandingRequest
import com.alpha.ai.imagegeneration.ImageUnderstandingResult
import com.alpha.ai.imagegeneration.ProviderCapabilities
import com.alpha.ai.imagegeneration.ProviderConnectionTestResult
import com.alpha.ai.imagegeneration.ProviderDescriptor
import com.alpha.ai.imagegeneration.ProviderId
import com.alpha.ai.imagegeneration.ProviderModelCatalogRequest
import com.alpha.ai.imagegeneration.ProviderModelCatalogResult
import com.alpha.ai.imagegeneration.ProviderUnderstandingEvent
import com.alpha.ai.imagegeneration.RequestPhase
import com.alpha.ai.imagegeneration.RetryAdvice
import com.alpha.ai.imagegeneration.internal.http.BaseUrlValidator
import com.alpha.ai.imagegeneration.internal.http.HttpRequest
import com.alpha.ai.imagegeneration.internal.http.HttpResponse
import com.alpha.ai.imagegeneration.internal.http.HttpTransport
import com.alpha.ai.imagegeneration.internal.http.ImageGenerationTransportException
import com.alpha.ai.imagegeneration.internal.http.ResponseLimits
import com.alpha.ai.imagegeneration.internal.http.defaultHttpTransport
import com.alpha.ai.imagegeneration.internal.http.hasJsonContentType
import com.alpha.ai.imagegeneration.internal.http.parseRetryAfter
import com.alpha.ai.imagegeneration.internal.http.readImageSource
import com.alpha.ai.imagegeneration.internal.http.toProviderUrlBuilder
import com.alpha.ai.imagegeneration.provider.BuiltInProviderIds
import com.alpha.ai.imagegeneration.provider.BuiltInProviderProtocols
import io.ktor.http.Url
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okio.IOException

/** OpenAI-compatible multimodal Chat Completions adapter. */
open class OpenAiVisionProvider private constructor(
    private val transport: HttpTransport,
    private val descriptorId: ProviderId,
    private val displayName: String,
    private val supportedInputMimeTypes: Set<String>,
    private val maxInputBytes: Long,
) : ImageUnderstandingProvider<OpenAiVisionConfig> {
    constructor() : this(
        defaultHttpTransport(),
        BuiltInProviderIds.OPENAI_VISION,
        "OpenAI Compatible",
        DEFAULT_INPUT_MIME_TYPES,
        DEFAULT_MAX_INPUT_BYTES,
    )

    internal constructor(transport: HttpTransport) : this(
        transport,
        BuiltInProviderIds.OPENAI_VISION,
        "OpenAI Compatible",
        DEFAULT_INPUT_MIME_TYPES,
        DEFAULT_MAX_INPUT_BYTES,
    )

    internal constructor(
        transport: HttpTransport,
        descriptorId: ProviderId,
        displayName: String,
        supportedInputMimeTypes: Set<String> = DEFAULT_INPUT_MIME_TYPES,
        maxInputBytes: Long = DEFAULT_MAX_INPUT_BYTES,
        @Suppress("UNUSED_PARAMETER") testOnly: Unit = Unit,
    ) : this(transport, descriptorId, displayName, supportedInputMimeTypes, maxInputBytes)

    override val descriptor = ProviderDescriptor(
        id = descriptorId,
        displayName = displayName,
        supportedProtocolVersions = setOf(BuiltInProviderProtocols.OPENAI_CHAT_COMPLETIONS_V1),
        capabilities = ProviderCapabilities(
            supportedInputMimeTypes = supportedInputMimeTypes,
            maxInputBytes = maxInputBytes,
            idempotencySupport = IdempotencySupport.NONE,
            safeRetryPhases = setOf(RequestPhase.BEFORE_DISPATCH),
            safeRejectedHttpStatuses = setOf(429),
            reliableDispatchBoundary = true,
            cancellationSemantics = CancellationSemantics.BEST_EFFORT,
            supportedCapabilities = setOf(com.alpha.ai.imagegeneration.AiCapability.IMAGE_UNDERSTANDING),
        ),
    )

    override val configType = OpenAiVisionConfig::class

    override suspend fun testConnection(
        capability: AiCapability,
        config: OpenAiVisionConfig,
    ): ProviderConnectionTestResult = probeOpenAiConnection(transport, capability, config)

    override suspend fun listModels(
        request: ProviderModelCatalogRequest,
    ): ProviderModelCatalogResult = listOpenAiModels(transport, request)

    override fun understand(
        request: ImageUnderstandingRequest,
        config: OpenAiVisionConfig,
    ): Flow<ProviderUnderstandingEvent> = flow {
        emit(ProviderUnderstandingEvent.Stage(GenerationStage.PREPARING_INPUT))
        val sourceBytes = try {
            readSource(request)
        } catch (_: ImageGenerationTransportException) {
            emit(failure(ImageGenerationErrorCategory.INVALID_REQUEST, RetryAdvice.DO_NOT_RETRY, false))
            return@flow
        }

        emit(ProviderUnderstandingEvent.Stage(GenerationStage.UPLOADING))
        val endpoint: Url
        val httpRequest: HttpRequest
        try {
            endpoint = BaseUrlValidator.validate(config.baseUrl, config.allowInsecureHttp)
                .toProviderUrlBuilder()
                .addPathSegment("chat")
                .addPathSegment("completions")
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
        val running = try {
            prepared.start()
        } catch (_: ImageGenerationTransportException) {
            emit(failure(ImageGenerationErrorCategory.NETWORK, RetryAdvice.SAFE_TO_RETRY, false))
            return@flow
        }
        val response = try {
            emit(ProviderUnderstandingEvent.Stage(GenerationStage.GENERATING))
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

    private suspend fun readSource(request: ImageUnderstandingRequest): ByteArray =
        readImageSource(request.source, supportedInputMimeTypes, maxInputBytes)

    private fun buildRequest(
        request: ImageUnderstandingRequest,
        config: OpenAiVisionConfig,
        sourceBytes: ByteArray,
        endpoint: Url,
        token: String,
    ): HttpRequest {
        val constrainedPrompt = request.prompt + "\n\n" + request.responseSchema.instruction()
        val imageUrl = "data:${request.source.mimeType};base64,${Base64.encode(sourceBytes)}"
        val body = JSON.encodeToString(
            OpenAiChatCompletionRequest(
                model = config.model,
                messages = listOf(OpenAiChatMessage(
                    role = "user",
                    content = listOf(
                        OpenAiMessageContent(type = "text", text = constrainedPrompt),
                        OpenAiMessageContent(type = "image_url", imageUrl = OpenAiImageUrl(imageUrl)),
                    ),
                )),
            ),
        ).encodeToByteArray()
        return HttpRequest.post(
            url = endpoint,
            headers = mapOf(
                "Authorization" to "Bearer $token",
                "Content-Type" to "application/json",
            ),
            body = body,
        )
    }

    private fun parseResponse(
        response: HttpResponse,
        config: OpenAiVisionConfig,
    ): ProviderUnderstandingEvent.Completed {
        val requestId = response.headers["x-request-id"]
        if (response.status == 401 || response.status == 403) {
            return failure(ImageGenerationErrorCategory.AUTHENTICATION, RetryAdvice.DO_NOT_RETRY, false, response.status, providerRequestId = requestId)
        }
        if (response.status == 429) {
            return failure(ImageGenerationErrorCategory.RATE_LIMITED, RetryAdvice.SAFE_TO_RETRY, false, response.status,
                retryAfterMillis = parseRetryAfter(response.headers["retry-after"]), providerRequestId = requestId, definitelyUnprocessed = true)
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
            return failure(ImageGenerationErrorCategory.MALFORMED_RESPONSE, RetryAdvice.DO_NOT_RETRY, true, response.status, providerRequestId = requestId)
        }
        return try {
            val parsed = JSON.decodeFromString<OpenAiChatCompletionResponse>(response.body.decodeToString())
            val text = parsed.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            val data = JSON.parseToJsonElement(stripJsonFence(text)) as? JsonObject
                ?: throw ImageGenerationTransportException("Model response is not a JSON object")
            ProviderUnderstandingEvent.Completed(ImageUnderstandingResult.Success(
                ImageUnderstandingOutput(data, config.providerId, config.model, requestId),
            ))
        } catch (_: Exception) {
            failure(ImageGenerationErrorCategory.MALFORMED_RESPONSE, RetryAdvice.DO_NOT_RETRY, true, response.status, providerRequestId = requestId)
        }
    }

    private fun stripJsonFence(value: String): String {
        if (!value.startsWith("```")) return value
        return value.removePrefix("```").removePrefix("json").removeSuffix("```").trim()
    }

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
    ) = ProviderUnderstandingEvent.Completed(ImageUnderstandingResult.Failure(
        ImageGenerationError(category, advice, requestMayHaveBeenAccepted, status, retryAfterMillis, providerRequestId, definitelyUnprocessed),
    ))

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val DEFAULT_INPUT_MIME_TYPES = setOf("image/png", "image/jpeg", "image/webp")
        const val DEFAULT_MAX_INPUT_BYTES = 12L * 1024 * 1024
    }
}
