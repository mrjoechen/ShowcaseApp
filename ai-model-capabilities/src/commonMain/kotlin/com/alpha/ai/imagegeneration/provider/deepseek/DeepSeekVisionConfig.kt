package com.alpha.ai.imagegeneration.provider.deepseek

import com.alpha.ai.imagegeneration.ProviderId
import com.alpha.ai.imagegeneration.SecretValue
import com.alpha.ai.imagegeneration.provider.BuiltInProviderIds
import com.alpha.ai.imagegeneration.provider.BuiltInProviderProtocols
import com.alpha.ai.imagegeneration.provider.openai.OpenAiVisionConfig

/** Runtime settings for DeepSeek multimodal requests using its OpenAI-compatible protocol. */
class DeepSeekVisionConfig(
    apiToken: SecretValue,
    model: String,
    baseUrl: String = DEFAULT_BASE_URL,
    allowInsecureHttp: Boolean = false,
    protocolVersion: String = BuiltInProviderProtocols.OPENAI_CHAT_COMPLETIONS_V1,
) : OpenAiVisionConfig(
    apiToken = apiToken,
    model = model,
    baseUrl = baseUrl,
    allowInsecureHttp = allowInsecureHttp,
    protocolVersion = protocolVersion,
    providerId = BuiltInProviderIds.DEEPSEEK_VISION,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
    }
}
