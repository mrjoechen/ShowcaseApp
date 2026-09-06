package com.alpha.ai.imagegeneration

import kotlin.jvm.JvmInline
import okio.Source

@JvmInline
value class ProviderId(val value: String) {
    init {
        require(value.matches(Regex("[a-z0-9][a-z0-9._-]{0,63}")))
    }
}

@JvmInline
value class OperationId(val value: String) {
    init {
        require(value.isNotBlank() && value.length <= 128)
    }
}

interface ImageSource {
    val mimeType: String
    val fileName: String
    val contentLength: Long?
    /** Opens a fresh source for each validation or retry. The caller closes it. */
    fun openSource(): Source
}

data class GenerateImageRequest(
    val operationId: OperationId,
    val source: ImageSource,
    val prompt: String,
    val providerOptions: ProviderRequestOptions? = null,
    val idempotencyKey: String? = null,
)

data class GeneratedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    val providerId: ProviderId,
    val model: String?,
    val providerRequestId: String? = null,
    val remoteUrl: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

sealed interface ImageGenerationEvent {
    val operationId: OperationId

    data class Stage(
        override val operationId: OperationId,
        val value: GenerationStage,
    ) : ImageGenerationEvent

    data class Preview(
        override val operationId: OperationId,
        val image: GeneratedImage,
    ) : ImageGenerationEvent

    data class Completed(
        override val operationId: OperationId,
        val result: GenerationResult,
    ) : ImageGenerationEvent
}

sealed interface ProviderGenerationEvent {
    data class Stage(val value: GenerationStage) : ProviderGenerationEvent
    data class Preview(val image: GeneratedImage) : ProviderGenerationEvent
    data class Completed(val result: GenerationResult) : ProviderGenerationEvent
}

enum class GenerationStage {
    VALIDATING_CONFIGURATION,
    PREPARING_INPUT,
    UPLOADING,
    GENERATING,
    DOWNLOADING_RESULT,
}

sealed interface GenerationResult {
    data class Success(val image: GeneratedImage) : GenerationResult
    data class Failure(val error: ImageGenerationError) : GenerationResult
    data class ProviderCancelled(val message: String?) : GenerationResult
}

enum class ImageGenerationErrorCategory {
    CONFIGURATION,
    AUTHENTICATION,
    QUOTA_EXCEEDED,
    RATE_LIMITED,
    CONTENT_POLICY,
    INVALID_REQUEST,
    NETWORK,
    TIMEOUT,
    SERVER,
    MALFORMED_RESPONSE,
    UNSUPPORTED_PROVIDER_PROTOCOL,
    CANCELLED,
}

data class ImageGenerationError(
    val category: ImageGenerationErrorCategory,
    val retryAdvice: RetryAdvice,
    val requestMayHaveBeenAccepted: Boolean,
    val httpStatus: Int? = null,
    val retryAfterMillis: Long? = null,
    val providerRequestId: String? = null,
    val definitelyUnprocessed: Boolean = false,
    val safeMessage: String? = null,
    val diagnosticCode: String? = null,
)

enum class RetryAdvice { SAFE_TO_RETRY, AMBIGUOUS, DO_NOT_RETRY }
