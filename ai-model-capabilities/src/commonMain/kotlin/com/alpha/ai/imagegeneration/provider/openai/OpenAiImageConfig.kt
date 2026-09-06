package com.alpha.ai.imagegeneration.provider.openai

import com.alpha.ai.imagegeneration.ProviderId
import com.alpha.ai.imagegeneration.ProviderRuntimeConfig
import com.alpha.ai.imagegeneration.SecretValue

/** In-memory credentials and wire settings for the OpenAI Images API. */
class OpenAiImageConfig(
    override val apiToken: SecretValue,
    override val model: String,
    override val baseUrl: String = DEFAULT_BASE_URL,
    override val allowInsecureHttp: Boolean = false,
    override val protocolVersion: String = PROTOCOL_VERSION,
) : ProviderRuntimeConfig {
    init {
        require(model.isNotBlank()) { "OpenAI model must not be blank" }
    }

    override val providerId: ProviderId = PROVIDER_ID

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val PROTOCOL_VERSION = "openai-images-v1"
        val PROVIDER_ID = ProviderId("openai")
    }
}
