package com.alpha.ai.imagegeneration

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/** Runtime request for structured understanding of one source image. */
data class ImageUnderstandingRequest(
    val operationId: OperationId,
    val source: ImageSource,
    /** Additional instructions such as classification, summary, or copywriting rules. */
    val prompt: String,
    val responseSchema: ImageUnderstandingSchema = ImageUnderstandingSchema.default(),
    val providerOptions: ProviderRequestOptions? = null,
    val idempotencyKey: String? = null,
) {
    init {
        require(prompt.isNotBlank()) { "Prompt must not be blank" }
    }
}

/**
 * Provider-neutral JSON object schema. Providers may enforce it natively and
 * adapters also include it in the prompt for providers that only support JSON mode.
 */
data class ImageUnderstandingSchema(
    val name: String = DEFAULT_NAME,
    val jsonSchema: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(name.matches(NAME_PATTERN)) { "Schema name is invalid" }
    }

    companion object {
        const val DEFAULT_NAME = "image_understanding"
        private val NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_-]{0,63}")

        /** A permissive object schema for callers that only need valid JSON. */
        fun default() = ImageUnderstandingSchema(
            jsonSchema = JsonObject(mapOf(
                "type" to kotlinx.serialization.json.JsonPrimitive("object"),
                "additionalProperties" to kotlinx.serialization.json.JsonPrimitive(true),
            )),
        )

        /** Builds the common flat-object shape used by summary/classification/copy fields. */
        fun objectSchema(
            name: String = DEFAULT_NAME,
            properties: Map<String, JsonElement>,
            required: Set<String> = properties.keys,
            additionalProperties: Boolean = false,
        ): ImageUnderstandingSchema {
            require(required.all { it in properties }) { "Required schema fields must be declared" }
            return ImageUnderstandingSchema(
                name = name,
                jsonSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        properties.forEach { (key, value) -> put(key, value) }
                    })
                    put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
                    put("additionalProperties", JsonPrimitive(additionalProperties))
                },
            )
        }
    }

    /** Stable text fallback used by APIs that do not expose JSON schema controls. */
    fun instruction(): String =
        "Return only one JSON object matching this JSON Schema named '$name': ${jsonSchema}. Do not wrap it in markdown."
}

data class ImageUnderstandingOutput(
    val data: JsonObject,
    val providerId: ProviderId,
    val model: String?,
    val providerRequestId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

sealed interface ImageUnderstandingEvent {
    val operationId: OperationId

    data class Stage(
        override val operationId: OperationId,
        val value: GenerationStage,
    ) : ImageUnderstandingEvent

    data class Completed(
        override val operationId: OperationId,
        val result: ImageUnderstandingResult,
    ) : ImageUnderstandingEvent
}

sealed interface ProviderUnderstandingEvent {
    data class Stage(val value: GenerationStage) : ProviderUnderstandingEvent
    data class Completed(val result: ImageUnderstandingResult) : ProviderUnderstandingEvent
}

sealed interface ImageUnderstandingResult {
    data class Success(val output: ImageUnderstandingOutput) : ImageUnderstandingResult
    data class Failure(val error: ImageGenerationError) : ImageUnderstandingResult
    data class ProviderCancelled(val message: String?) : ImageUnderstandingResult
}
