package com.alpha.ai.imagegeneration.provider.gemini

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig,
)

@Serializable
internal data class GeminiContent(
    val role: String = "",
    val parts: List<GeminiPart>,
)

@Serializable
internal data class GeminiPart(
    val text: String? = null,
    @SerialName("inlineData") val inlineData: GeminiInlineData? = null,
    @SerialName("inline_data") val snakeInlineData: GeminiInlineData? = null,
)

@Serializable
internal data class GeminiInlineData(
    @SerialName("mimeType") val mimeType: String? = null,
    @SerialName("mime_type") val snakeMimeType: String? = null,
    val data: String? = null,
)

@Serializable
internal data class GeminiGenerationConfig(
    val responseModalities: List<String>? = null,
    val responseMimeType: String? = null,
    val responseSchema: JsonObject? = null,
)

@Serializable
internal data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val promptFeedback: GeminiPromptFeedback? = null,
)

@Serializable
internal data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)

@Serializable
internal data class GeminiPromptFeedback(
    val blockReason: String? = null,
)
