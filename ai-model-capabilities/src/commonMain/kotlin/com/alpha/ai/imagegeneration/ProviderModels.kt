package com.alpha.ai.imagegeneration

interface ProviderRuntimeConfig {
    val providerId: ProviderId
    val baseUrl: String
    val model: String?
    val protocolVersion: String
    val apiToken: SecretValue
    val allowInsecureHttp: Boolean
}

/** A capability exposed by a registered AI provider adapter. */
enum class AiCapability {
    /** Uses a source image and prompt to produce a new image. */
    IMAGE_TO_IMAGE,

    /** Uses a source image and constrained prompt to produce structured JSON. */
    IMAGE_UNDERSTANDING,
}

interface ProviderRequestOptions {
    val providerId: ProviderId
}

enum class IdempotencySupport { NONE, CALLER_KEY, PROVIDER_NATIVE }
enum class CancellationSemantics { BEST_EFFORT, PROVIDER_ABORT_SUPPORTED }
enum class RequestPhase { BEFORE_DISPATCH, IN_FLIGHT }

data class ProviderCapabilities(
    val supportedInputMimeTypes: Set<String>,
    val maxInputBytes: Long,
    val idempotencySupport: IdempotencySupport = IdempotencySupport.NONE,
    val safeRetryPhases: Set<RequestPhase> = emptySet(),
    val safeRejectedHttpStatuses: Set<Int> = emptySet(),
    val reliableDispatchBoundary: Boolean = false,
    val cancellationSemantics: CancellationSemantics = CancellationSemantics.BEST_EFFORT,
    val supportedCapabilities: Set<AiCapability> = setOf(AiCapability.IMAGE_TO_IMAGE),
) {
    companion object {
        fun conservative(
            supportedInputMimeTypes: Set<String> = emptySet(),
            maxInputBytes: Long = 0,
        ) = ProviderCapabilities(supportedInputMimeTypes, maxInputBytes)
    }
}

data class ProviderDescriptor(
    val id: ProviderId,
    val displayName: String,
    val supportedProtocolVersions: Set<String>,
    val capabilities: ProviderCapabilities,
    val safeMetadataKeys: Set<String> = emptySet(),
)
