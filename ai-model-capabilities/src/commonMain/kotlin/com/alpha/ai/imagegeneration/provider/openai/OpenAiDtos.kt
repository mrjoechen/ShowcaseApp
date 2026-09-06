package com.alpha.ai.imagegeneration.provider.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenAiImageResponse(
    val data: List<OpenAiImageData> = emptyList(),
)

@Serializable
internal data class OpenAiImageData(
    @SerialName("b64_json") val base64Json: String? = null,
)
