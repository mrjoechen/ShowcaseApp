package com.alpha.ai.imagegeneration.provider.openai

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

internal suspend fun probeOpenAiConnection(
    transport: HttpTransport,
    capability: AiCapability,
    config: ProviderRuntimeConfig,
): ProviderConnectionTestResult {
    val request = try {
        val endpoint = BaseUrlValidator.validate(config.baseUrl, config.allowInsecureHttp)
            .toProviderUrlBuilder()
            .addPathSegment("models")
            .build()
        config.apiToken.use { token ->
            HttpRequest.get(
                endpoint,
                headers = mapOf("Authorization" to "Bearer ${token.concatToString()}"),
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
