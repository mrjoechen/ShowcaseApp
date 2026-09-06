package com.alpha.showcase.common.ai

import com.alpha.ai.imagegeneration.*
import com.alpha.ai.imagegeneration.provider.BuiltInProviderProtocols
import com.alpha.ai.imagegeneration.provider.deepseek.DeepSeekVisionConfig
import com.alpha.ai.imagegeneration.provider.gemini.GeminiImageConfig
import com.alpha.ai.imagegeneration.provider.gemini.GeminiVisionConfig
import com.alpha.ai.imagegeneration.provider.openai.OpenAiImageConfig
import com.alpha.ai.imagegeneration.provider.openai.OpenAiVisionConfig

internal fun AiProfile.runtimeConfig(token: SecretValue): ProviderRuntimeConfig = when (providerId) {
    "openai" -> OpenAiImageConfig(token, model, baseUrl, allowInsecureHttp)
    "gemini-nano-banana" -> GeminiImageConfig(token, model, baseUrl, allowInsecureHttp)
    "openai-vision" -> OpenAiVisionConfig(token, model, baseUrl, allowInsecureHttp)
    "gemini-vision" -> GeminiVisionConfig(token, model, baseUrl, allowInsecureHttp)
    "deepseek-vision" -> DeepSeekVisionConfig(token, model, baseUrl, allowInsecureHttp)
    else -> error("Unsupported AI provider")
}

internal fun aiProviderCapability(providerId: String): AiCapability = when (providerId) {
    "openai", "gemini-nano-banana" -> AiCapability.IMAGE_TO_IMAGE
    "openai-vision", "gemini-vision", "deepseek-vision" -> AiCapability.IMAGE_UNDERSTANDING
    else -> error("Unsupported AI provider")
}

internal fun aiProviderProtocol(providerId: String): String = when (providerId) {
    "openai" -> BuiltInProviderProtocols.OPENAI_IMAGES_V1
    "openai-vision", "deepseek-vision" -> BuiltInProviderProtocols.OPENAI_CHAT_COMPLETIONS_V1
    "gemini-nano-banana", "gemini-vision" -> BuiltInProviderProtocols.GEMINI_GENERATE_CONTENT_V1BETA
    else -> error("Unsupported AI provider")
}

internal fun aiProviderBaseUrl(providerId: String): String = when (providerId) {
    "gemini-nano-banana", "gemini-vision" -> GeminiVisionConfig.DEFAULT_BASE_URL
    "deepseek-vision" -> DeepSeekVisionConfig.DEFAULT_BASE_URL
    else -> OpenAiVisionConfig.DEFAULT_BASE_URL
}

internal fun aiProviderModel(providerId: String): String = when (providerId) {
    "openai" -> "gpt-image-1"
    "gemini-nano-banana" -> "gemini-2.5-flash-image"
    "gemini-vision" -> "gemini-2.5-flash"
    "deepseek-vision" -> "deepseek-chat"
    else -> "gpt-4o-mini"
}

internal fun shouldAutomaticallyRetry(error: ImageGenerationError, attempt: Int): Boolean =
    attempt < 3 && error.retryAdvice == RetryAdvice.SAFE_TO_RETRY &&
        (!error.requestMayHaveBeenAccepted || error.definitelyUnprocessed) &&
        error.category in setOf(ImageGenerationErrorCategory.NETWORK, ImageGenerationErrorCategory.TIMEOUT,
            ImageGenerationErrorCategory.RATE_LIMITED, ImageGenerationErrorCategory.SERVER)

internal fun failureStatus(error: ImageGenerationError): AiTaskStatus = when {
    error.requestMayHaveBeenAccepted && !error.definitelyUnprocessed -> AiTaskStatus.RESULT_UNKNOWN
    error.category in setOf(ImageGenerationErrorCategory.CONFIGURATION, ImageGenerationErrorCategory.AUTHENTICATION,
        ImageGenerationErrorCategory.UNSUPPORTED_PROVIDER_PROTOCOL) -> AiTaskStatus.NEEDS_USER_ACTION
    else -> AiTaskStatus.FAILED
}
