package com.alpha.ai.imagegeneration

/** Connection fields required to discover models before a model has been selected. */
data class ProviderModelCatalogRequest(
    val providerId: ProviderId,
    val capability: AiCapability,
    val baseUrl: String,
    val protocolVersion: String,
    val apiToken: SecretValue,
    val allowInsecureHttp: Boolean = false,
)

data class ProviderModel(
    val id: String,
    val displayName: String? = null,
)

/** Provider-neutral model discovery result. Unsupported is distinct from a failed request. */
sealed interface ProviderModelCatalogResult {
    data class Available(
        val providerId: ProviderId,
        val capability: AiCapability,
        val models: List<ProviderModel>,
    ) : ProviderModelCatalogResult

    data object Unsupported : ProviderModelCatalogResult

    data class Unavailable(
        val error: ImageGenerationError,
    ) : ProviderModelCatalogResult
}
