package com.alpha.ai.imagegeneration.provider.deepseek

import com.alpha.ai.imagegeneration.ProviderId
import com.alpha.ai.imagegeneration.internal.http.HttpTransport
import com.alpha.ai.imagegeneration.provider.BuiltInProviderIds
import com.alpha.ai.imagegeneration.provider.openai.OpenAiVisionProvider

/** DeepSeek multimodal adapter. DeepSeek exposes an OpenAI-compatible chat protocol. */
class DeepSeekVisionProvider : OpenAiVisionProvider {
    constructor() : super(
        com.alpha.ai.imagegeneration.internal.http.defaultHttpTransport(),
        BuiltInProviderIds.DEEPSEEK_VISION,
        "DeepSeek",
        supportedInputMimeTypes = setOf("image/png", "image/jpeg", "image/gif", "image/webp"),
        maxInputBytes = 32L * 1024 * 1024,
    )

    internal constructor(transport: HttpTransport, @Suppress("UNUSED_PARAMETER") testOnly: Unit = Unit) : super(
        transport,
        BuiltInProviderIds.DEEPSEEK_VISION,
        "DeepSeek",
        supportedInputMimeTypes = setOf("image/png", "image/jpeg", "image/gif", "image/webp"),
        maxInputBytes = 32L * 1024 * 1024,
    )
}
