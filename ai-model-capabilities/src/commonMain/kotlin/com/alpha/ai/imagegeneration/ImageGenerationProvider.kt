package com.alpha.ai.imagegeneration

import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Stable seam between the capability client and concrete model integrations.
 *
 * New model integrations should implement this adapter (usually by extending one
 * of the capability-specific interfaces below). Callers only depend on the
 * descriptor and capability methods, so adding a provider does not change the
 * upper-layer client contract.
 */
interface AiProviderAdapter<C : ProviderRuntimeConfig> {
    val descriptor: ProviderDescriptor
    val configType: KClass<C>

    /**
     * Provider-specific lightweight probe. Implementations should use a metadata or
     * endpoint-validation request and must not start billable model inference.
     */
    suspend fun testConnection(
        capability: AiCapability,
        config: C,
    ): ProviderConnectionTestResult = ProviderConnectionTestResult.Unavailable(
        ImageGenerationError(
            category = ImageGenerationErrorCategory.UNSUPPORTED_PROVIDER_PROTOCOL,
            retryAdvice = RetryAdvice.DO_NOT_RETRY,
            requestMayHaveBeenAccepted = false,
        ),
    )

    /**
     * Optional provider-owned model discovery. New adapters can add support without
     * changing any application caller or the capability client.
     */
    suspend fun listModels(
        request: ProviderModelCatalogRequest,
    ): ProviderModelCatalogResult = ProviderModelCatalogResult.Unsupported

    fun generateImage(
        request: GenerateImageRequest,
        config: C,
    ): Flow<ProviderGenerationEvent> = flow {
        emit(ProviderGenerationEvent.Completed(GenerationResult.Failure(
            ImageGenerationError(
                category = ImageGenerationErrorCategory.UNSUPPORTED_PROVIDER_PROTOCOL,
                retryAdvice = RetryAdvice.DO_NOT_RETRY,
                requestMayHaveBeenAccepted = false,
                safeMessage = "Provider does not support image generation",
            ),
        )))
    }

    fun understandImage(
        request: ImageUnderstandingRequest,
        config: C,
    ): Flow<ProviderUnderstandingEvent> = flow {
        emit(ProviderUnderstandingEvent.Completed(ImageUnderstandingResult.Failure(
            ImageGenerationError(
                category = ImageGenerationErrorCategory.UNSUPPORTED_PROVIDER_PROTOCOL,
                retryAdvice = RetryAdvice.DO_NOT_RETRY,
                requestMayHaveBeenAccepted = false,
                safeMessage = "Provider does not support image understanding",
            ),
        )))
    }
}

/** Compatibility seam for existing image-to-image adapters. */
interface ImageGenerationProvider<C : ProviderRuntimeConfig> : AiProviderAdapter<C> {
    fun generate(request: GenerateImageRequest, config: C): Flow<ProviderGenerationEvent>

    override fun generateImage(
        request: GenerateImageRequest,
        config: C,
    ): Flow<ProviderGenerationEvent> = generate(request, config)
}

/** Adapter seam for providers that return structured image understanding data. */
interface ImageUnderstandingProvider<C : ProviderRuntimeConfig> : AiProviderAdapter<C> {
    fun understand(request: ImageUnderstandingRequest, config: C): Flow<ProviderUnderstandingEvent>

    override fun understandImage(
        request: ImageUnderstandingRequest,
        config: C,
    ): Flow<ProviderUnderstandingEvent> = understand(request, config)
}
