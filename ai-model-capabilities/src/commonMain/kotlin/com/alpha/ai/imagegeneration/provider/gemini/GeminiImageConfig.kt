package com.alpha.ai.imagegeneration.provider.gemini

import com.alpha.ai.imagegeneration.ProviderId
import com.alpha.ai.imagegeneration.ProviderRuntimeConfig
import com.alpha.ai.imagegeneration.SecretValue

/** In-memory credentials and wire settings for Gemini generateContent image generation. */
class GeminiImageConfig(
    override val apiToken: SecretValue,
    override val model: String,
    override val baseUrl: String = DEFAULT_BASE_URL,
    override val allowInsecureHttp: Boolean = false,
    override val protocolVersion: String = PROTOCOL_VERSION,
) : ProviderRuntimeConfig {
    init {
        require(model.isNotBlank()) { "Gemini model must not be blank" }
    }

    override val providerId: ProviderId = PROVIDER_ID

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val PROTOCOL_VERSION = "gemini-generate-content-v1beta"
        val PROVIDER_ID = ProviderId("gemini-nano-banana")
    }
}
