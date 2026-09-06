package com.alpha.ai.imagegeneration.provider.openai

import com.alpha.ai.imagegeneration.AiCapability
import com.alpha.ai.imagegeneration.CancellationSemantics
import com.alpha.ai.imagegeneration.GenerateImageRequest
import com.alpha.ai.imagegeneration.GeneratedImage
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
import com.alpha.ai.imagegeneration.internal.http.ImageEditMultipart
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
import kotlinx.serialization.json.Json
import okio.IOException

class OpenAiImageProvider private constructor(
    private val transport: HttpTransport,
) : ImageGenerationProvider<OpenAiImageConfig> {
    constructor() : this(defaultHttpTransport())

    internal constructor(transport: HttpTransport, @Suppress("UNUSED_PARAMETER") testOnly: Unit = Unit) : this(transport)

    override val descriptor = ProviderDescriptor(
        id = OpenAiImageConfig.PROVIDER_ID,
        displayName = "OpenAI Images",
        supportedProtocolVersions = setOf(OpenAiImageConfig.PROTOCOL_VERSION),
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

    override val configType = OpenAiImageConfig::class

    override suspend fun testConnection(
        capability: AiCapability,
        config: OpenAiImageConfig,
    ): ProviderConnectionTestResult = probeOpenAiConnection(transport, capability, config)

    override suspend fun listModels(
        request: ProviderModelCatalogRequest,
    ): ProviderModelCatalogResult = listOpenAiModels(transport, request)

    override fun generate(
        request: GenerateImageRequest,
        config: OpenAiImageConfig,
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
                .addPathSegment("images")
                .addPathSegment("edits")
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
        config: OpenAiImageConfig,
        sourceBytes: ByteArray,
        endpoint: Url,
        token: String,
    ): HttpRequest {
        val multipart = ImageEditMultipart.encode(
            model = config.model,
            prompt = request.prompt,
            fileName = request.source.fileName,
            mimeType = request.source.mimeType,
            bytes = sourceBytes,
        )
        return HttpRequest.post(
            url = endpoint,
            headers = mapOf(
                "Authorization" to "Bearer $token",
                "Content-Type" to multipart.contentType,
            ),
            body = multipart.bytes,
        )
    }

    private fun parseResponse(response: HttpResponse, config: OpenAiImageConfig): ProviderGenerationEvent.Completed {
        val requestId = response.headers["x-request-id"]
        if (response.status == 401 || response.status == 403) {
            return failure(
                category = ImageGenerationErrorCategory.AUTHENTICATION,
                advice = RetryAdvice.DO_NOT_RETRY,
                requestMayHaveBeenAccepted = false,
                status = response.status,
                providerRequestId = requestId,
            )
        }
        if (response.status == 429) {
            return failure(
                category = ImageGenerationErrorCategory.RATE_LIMITED,
                advice = RetryAdvice.SAFE_TO_RETRY,
                requestMayHaveBeenAccepted = false,
                status = response.status,
                retryAfterMillis = parseRetryAfter(response.headers["retry-after"]),
                providerRequestId = requestId,
                definitelyUnprocessed = true,
            )
        }
        if (response.status == 400) {
            return failure(
                category = ImageGenerationErrorCategory.INVALID_REQUEST,
                advice = RetryAdvice.DO_NOT_RETRY,
                requestMayHaveBeenAccepted = false,
                status = response.status,
                providerRequestId = requestId,
                definitelyUnprocessed = true,
            )
        }
        if (response.status in 500..599) {
            return failure(
                category = ImageGenerationErrorCategory.SERVER,
                advice = RetryAdvice.AMBIGUOUS,
                requestMayHaveBeenAccepted = true,
                status = response.status,
                providerRequestId = requestId,
            )
        }
        if (response.status !in 200..299) {
            return failure(
                category = ImageGenerationErrorCategory.SERVER,
                advice = RetryAdvice.DO_NOT_RETRY,
                requestMayHaveBeenAccepted = true,
                status = response.status,
                providerRequestId = requestId,
            )
        }
        if (!response.hasJsonContentType()) {
            return failure(
                category = ImageGenerationErrorCategory.MALFORMED_RESPONSE,
                advice = RetryAdvice.DO_NOT_RETRY,
                requestMayHaveBeenAccepted = true,
                status = response.status,
                providerRequestId = requestId,
            )
        }

        val decoded = try {
            val parsed = JSON.decodeFromString<OpenAiImageResponse>(response.body.decodeToString())
            if (parsed.data.size != 1) throw ImageGenerationTransportException("Unexpected image result count")
            val base64 = parsed.data.first().base64Json?.takeIf { it.isNotBlank() }
                ?: throw ImageGenerationTransportException("Missing image result")
            ImageBytesValidator.decodeBase64(base64, declaredMimeType = null, limitBytes = ResponseLimits().imageBytes)
        } catch (_: Exception) {
            return failure(
                category = ImageGenerationErrorCategory.MALFORMED_RESPONSE,
                advice = RetryAdvice.DO_NOT_RETRY,
                requestMayHaveBeenAccepted = true,
                status = response.status,
                providerRequestId = requestId,
            )
        }
        return ProviderGenerationEvent.Completed(
            com.alpha.ai.imagegeneration.GenerationResult.Success(
                GeneratedImage(
                    bytes = decoded.bytes,
                    mimeType = decoded.mimeType,
                    providerId = config.providerId,
                    model = config.model,
                    providerRequestId = requestId,
                ),
            ),
        )
    }

    private fun transportFailure(message: String?): ProviderGenerationEvent.Completed = failure(
        category = if (message?.contains("timeout", ignoreCase = true) == true) {
            ImageGenerationErrorCategory.TIMEOUT
        } else {
            ImageGenerationErrorCategory.NETWORK
        },
        advice = RetryAdvice.AMBIGUOUS,
        requestMayHaveBeenAccepted = true,
    )

    private fun failure(
        category: ImageGenerationErrorCategory,
        advice: RetryAdvice,
        requestMayHaveBeenAccepted: Boolean,
        status: Int? = null,
        retryAfterMillis: Long? = null,
        providerRequestId: String? = null,
        definitelyUnprocessed: Boolean = false,
    ) = ProviderGenerationEvent.Completed(
        com.alpha.ai.imagegeneration.GenerationResult.Failure(
            ImageGenerationError(
                category = category,
                retryAdvice = advice,
                requestMayHaveBeenAccepted = requestMayHaveBeenAccepted,
                httpStatus = status,
                retryAfterMillis = retryAfterMillis,
                providerRequestId = providerRequestId,
                definitelyUnprocessed = definitelyUnprocessed,
            ),
        ),
    )

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        val SUPPORTED_INPUT_MIME_TYPES = setOf("image/png", "image/jpeg", "image/webp")
        const val MAX_INPUT_BYTES = 12L * 1024 * 1024
    }
}
