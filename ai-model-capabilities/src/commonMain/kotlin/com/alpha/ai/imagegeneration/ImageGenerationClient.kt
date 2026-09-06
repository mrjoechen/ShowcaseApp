package com.alpha.ai.imagegeneration

import kotlinx.coroutines.flow.Flow

/** Provider-neutral entry point for all image model capabilities. */
interface AiModelClient {
    fun providerDescriptor(providerId: ProviderId): ProviderDescriptor?

    /** All adapters registered in this client, in registration order. */
    fun providerDescriptors(): List<ProviderDescriptor> = emptyList()

    /**
     * Performs a lightweight provider-owned connectivity/authentication check.
     * It must not submit an image generation or image understanding request.
     */
    suspend fun testConnection(
        capability: AiCapability,
        config: ProviderRuntimeConfig,
    ): ProviderConnectionTestResult = ProviderConnectionTestResult.Unavailable(
        ImageGenerationError(
            category = ImageGenerationErrorCategory.UNSUPPORTED_PROVIDER_PROTOCOL,
            retryAdvice = RetryAdvice.DO_NOT_RETRY,
            requestMayHaveBeenAccepted = false,
        ),
    )

    /** Lists models when the selected provider exposes a model-catalog endpoint. */
    suspend fun listModels(
        request: ProviderModelCatalogRequest,
    ): ProviderModelCatalogResult = ProviderModelCatalogResult.Unsupported

    fun generateImage(
        request: GenerateImageRequest,
        config: ProviderRuntimeConfig,
    ): Flow<ImageGenerationEvent>

    fun understandImage(
        request: ImageUnderstandingRequest,
        config: ProviderRuntimeConfig,
    ): Flow<ImageUnderstandingEvent>
}

/**
 * Source-compatible facade for existing image-generation callers.
 * New callers should depend on [AiModelClient] and use both capability methods.
 */
interface ImageGenerationClient : AiModelClient {
    fun generate(
        request: GenerateImageRequest,
        config: ProviderRuntimeConfig,
    ): Flow<ImageGenerationEvent>

    override fun generateImage(
        request: GenerateImageRequest,
        config: ProviderRuntimeConfig,
    ): Flow<ImageGenerationEvent> = generate(request, config)

    override fun understandImage(
        request: ImageUnderstandingRequest,
        config: ProviderRuntimeConfig,
    ): Flow<ImageUnderstandingEvent> = kotlinx.coroutines.flow.flow {
        emit(ImageUnderstandingEvent.Completed(
            request.operationId,
            ImageUnderstandingResult.Failure(ImageGenerationError(
                category = ImageGenerationErrorCategory.UNSUPPORTED_PROVIDER_PROTOCOL,
                retryAdvice = RetryAdvice.DO_NOT_RETRY,
                requestMayHaveBeenAccepted = false,
                safeMessage = "Client does not support image understanding",
            )),
        ))
    }
}
