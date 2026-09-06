package com.alpha.ai.imagegeneration.provider.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenAiChatCompletionRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    @SerialName("response_format") val responseFormat: OpenAiResponseFormat = OpenAiResponseFormat(),
)

@Serializable
internal data class OpenAiChatMessage(
    val role: String,
    val content: List<OpenAiMessageContent>,
)

@Serializable
internal data class OpenAiMessageContent(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: OpenAiImageUrl? = null,
)

@Serializable
internal data class OpenAiImageUrl(val url: String)

@Serializable
internal data class OpenAiResponseFormat(val type: String = "json_object")

@Serializable
internal data class OpenAiChatCompletionResponse(
    val choices: List<OpenAiChatChoice> = emptyList(),
)

@Serializable
internal data class OpenAiChatChoice(
    val message: OpenAiChatMessageResponse? = null,
    val finishReason: String? = null,
)

@Serializable
internal data class OpenAiChatMessageResponse(
    val content: String? = null,
)
