package com.alpha.ai.imagegeneration.provider.gemini

import com.alpha.ai.imagegeneration.ProviderId
import com.alpha.ai.imagegeneration.ProviderRuntimeConfig
import com.alpha.ai.imagegeneration.SecretValue
import com.alpha.ai.imagegeneration.provider.BuiltInProviderIds
import com.alpha.ai.imagegeneration.provider.BuiltInProviderProtocols

/** Runtime settings for Gemini multimodal JSON responses. */
class GeminiVisionConfig(
    override val apiToken: SecretValue,
    override val model: String,
    override val baseUrl: String = DEFAULT_BASE_URL,
    override val allowInsecureHttp: Boolean = false,
    override val protocolVersion: String = BuiltInProviderProtocols.GEMINI_GENERATE_CONTENT_V1BETA,
) : ProviderRuntimeConfig {
    init {
        require(model.isNotBlank()) { "Gemini model must not be blank" }
    }

    override val providerId: ProviderId = BuiltInProviderIds.GEMINI_VISION

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val PROTOCOL_VERSION = BuiltInProviderProtocols.GEMINI_GENERATE_CONTENT_V1BETA
        val PROVIDER_ID = BuiltInProviderIds.GEMINI_VISION
    }
}
