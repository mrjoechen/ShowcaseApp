package com.alpha.ai.imagegeneration

/** Result of a lightweight provider/configuration probe that does not submit image work. */
sealed interface ProviderConnectionTestResult {
    data class Available(
        val providerId: ProviderId,
        val capability: AiCapability,
    ) : ProviderConnectionTestResult

    data class Unavailable(
        val error: ImageGenerationError,
    ) : ProviderConnectionTestResult
}
