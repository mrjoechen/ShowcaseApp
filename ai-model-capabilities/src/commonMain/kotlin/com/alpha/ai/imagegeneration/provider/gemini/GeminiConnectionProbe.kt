package com.alpha.ai.imagegeneration.provider.gemini

import com.alpha.ai.imagegeneration.AiCapability
import com.alpha.ai.imagegeneration.ImageGenerationError
import com.alpha.ai.imagegeneration.ImageGenerationErrorCategory
import com.alpha.ai.imagegeneration.ProviderConnectionTestResult
import com.alpha.ai.imagegeneration.ProviderRuntimeConfig
import com.alpha.ai.imagegeneration.RetryAdvice
import com.alpha.ai.imagegeneration.internal.http.BaseUrlValidator
import com.alpha.ai.imagegeneration.internal.http.HttpRequest
import com.alpha.ai.imagegeneration.internal.http.HttpTransport
import com.alpha.ai.imagegeneration.internal.http.ImageGenerationTransportException
import com.alpha.ai.imagegeneration.internal.http.probeConnection
import com.alpha.ai.imagegeneration.internal.http.toProviderUrlBuilder

internal suspend fun probeGeminiConnection(
    transport: HttpTransport,
    capability: AiCapability,
    config: ProviderRuntimeConfig,
): ProviderConnectionTestResult {
    val model = config.model?.takeIf(String::isNotBlank) ?: return configurationFailure()
    val request = try {
        val endpoint = BaseUrlValidator.validate(config.baseUrl, config.allowInsecureHttp)
            .toProviderUrlBuilder()
            .addPathSegment("models")
            .addPathSegment(model)
            .build()
        config.apiToken.use { token ->
            HttpRequest.get(
                endpoint,
                headers = mapOf("x-goog-api-key" to token.concatToString()),
            )
        }
    } catch (_: ImageGenerationTransportException) {
        return configurationFailure()
    }
    return transport.probeConnection(request, config.providerId, capability)
}

private fun configurationFailure() = ProviderConnectionTestResult.Unavailable(
    ImageGenerationError(
        category = ImageGenerationErrorCategory.CONFIGURATION,
        retryAdvice = RetryAdvice.DO_NOT_RETRY,
        requestMayHaveBeenAccepted = false,
        definitelyUnprocessed = true,
    ),
)
