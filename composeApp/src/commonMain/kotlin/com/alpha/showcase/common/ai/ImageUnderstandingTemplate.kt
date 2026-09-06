package com.alpha.showcase.common.ai

import kotlin.jvm.JvmInline
import com.alpha.ai.imagegeneration.ImageSource
import com.alpha.ai.imagegeneration.ImageUnderstandingRequest
import com.alpha.ai.imagegeneration.ImageUnderstandingSchema
import com.alpha.ai.imagegeneration.OperationId
import com.alpha.ai.imagegeneration.ProviderRequestOptions

/** Validated application locale passed to provider-neutral understanding templates. */
@JvmInline
value class ImageUnderstandingOutputLanguage(val languageTag: String) {
    init {
        require(languageTag.matches(LANGUAGE_TAG_PATTERN)) { "Output language must be a BCP-47 language tag" }
    }

    private companion object {
        val LANGUAGE_TAG_PATTERN = Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8}){0,3}")
    }
}

/** Application-owned prompt and output contract for one image-understanding use case. */
data class ImageUnderstandingTemplate(
    val id: String,
    val version: Int,
    val name: String,
    val description: String,
    val prompt: String,
    val responseSchema: ImageUnderstandingSchema,
    val order: Int,
) {
    init {
        require(id.matches(ID_PATTERN)) { "Template id is invalid" }
        require(version > 0) { "Template version must be positive" }
        require(name.isNotBlank()) { "Template name must not be blank" }
        require(description.isNotBlank()) { "Template description must not be blank" }
        require(prompt.isNotBlank()) { "Template prompt must not be blank" }
        require(order >= 0) { "Template order must not be negative" }
    }

    /** Creates the provider-neutral module request while keeping the template contract intact. */
    fun createRequest(
        operationId: OperationId,
        source: ImageSource,
        providerOptions: ProviderRequestOptions? = null,
        idempotencyKey: String? = null,
        outputLanguage: ImageUnderstandingOutputLanguage? = null,
    ): ImageUnderstandingRequest = ImageUnderstandingRequest(
        operationId = operationId,
        source = source,
        prompt = outputLanguage?.let { language ->
            prompt + "\n\n" + """
                Output language requirement:
                Write every generated natural-language value in BCP-47 language ${language.languageTag}.
                This includes summaries, captions, narration, labels, tags, and explanations. Keep text copied verbatim from the image in its original language.
                The output language requirement overrides the language used by examples in this prompt or schema descriptions.
            """.trimIndent()
        } ?: prompt,
        responseSchema = responseSchema,
        providerOptions = providerOptions,
        idempotencyKey = idempotencyKey,
    )

    private companion object {
        val ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{0,127}")
    }
}
