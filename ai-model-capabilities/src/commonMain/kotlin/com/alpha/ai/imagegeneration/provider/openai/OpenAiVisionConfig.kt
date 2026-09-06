package com.alpha.ai.imagegeneration.provider.openai

import com.alpha.ai.imagegeneration.ProviderId
import com.alpha.ai.imagegeneration.ProviderRuntimeConfig
import com.alpha.ai.imagegeneration.SecretValue
import com.alpha.ai.imagegeneration.provider.BuiltInProviderIds
import com.alpha.ai.imagegeneration.provider.BuiltInProviderProtocols

/** Runtime settings for an OpenAI-compatible multimodal endpoint. */
open class OpenAiVisionConfig(
    override val apiToken: SecretValue,
    override val model: String,
    override val baseUrl: String = DEFAULT_BASE_URL,
    override val allowInsecureHttp: Boolean = false,
    override val protocolVersion: String = BuiltInProviderProtocols.OPENAI_CHAT_COMPLETIONS_V1,
    override val providerId: ProviderId = BuiltInProviderIds.OPENAI_VISION,
) : ProviderRuntimeConfig {
    init {
        require(model.isNotBlank()) { "Model must not be blank" }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val PROTOCOL_VERSION = BuiltInProviderProtocols.OPENAI_CHAT_COMPLETIONS_V1
        val PROVIDER_ID = BuiltInProviderIds.OPENAI_VISION
    }
}
