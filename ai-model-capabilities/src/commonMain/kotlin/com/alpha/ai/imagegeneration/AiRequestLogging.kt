package com.alpha.ai.imagegeneration

/**
 * Optional structured request logging seam.
 *
 * Events intentionally omit credentials, base URLs, prompts, file names, response payloads, and
 * provider request IDs. Applications may bridge this to Timber, SLF4J, telemetry, or a no-op.
 */
fun interface AiRequestLogger {
    fun log(event: AiRequestLogEvent)

    companion object {
        val NONE = AiRequestLogger { }
    }
}

sealed interface AiRequestLogEvent {
    val capability: AiCapability
    val providerId: ProviderId
    val model: String?
    /** Stable one-way fingerprint used to correlate events without exposing caller identifiers. */
    val operationFingerprint: String
    val elapsedMillis: Long

    data class Started(
        override val capability: AiCapability,
        override val providerId: ProviderId,
        override val model: String?,
        override val operationFingerprint: String,
        val inputMimeType: String,
        val inputBytes: Long?,
        override val elapsedMillis: Long = 0L,
    ) : AiRequestLogEvent

    data class Stage(
        override val capability: AiCapability,
        override val providerId: ProviderId,
        override val model: String?,
        override val operationFingerprint: String,
        val stage: GenerationStage,
        override val elapsedMillis: Long,
    ) : AiRequestLogEvent

    data class Completed(
        override val capability: AiCapability,
        override val providerId: ProviderId,
        override val model: String?,
        override val operationFingerprint: String,
        val outcome: AiRequestOutcome,
        val errorCategory: ImageGenerationErrorCategory? = null,
        override val elapsedMillis: Long,
    ) : AiRequestLogEvent
}

enum class AiRequestOutcome { SUCCESS, FAILURE, CANCELLED }
