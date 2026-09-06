package com.alpha.ai.imagegeneration.provider

import com.alpha.ai.imagegeneration.ImageGeneration
import com.alpha.ai.imagegeneration.ProviderId
import com.alpha.ai.imagegeneration.internal.http.defaultHttpTransport
import com.alpha.ai.imagegeneration.provider.deepseek.DeepSeekVisionProvider
import com.alpha.ai.imagegeneration.provider.gemini.GeminiNanoBananaProvider
import com.alpha.ai.imagegeneration.provider.gemini.GeminiVisionProvider
import com.alpha.ai.imagegeneration.provider.openai.OpenAiImageProvider
import com.alpha.ai.imagegeneration.provider.openai.OpenAiVisionProvider

object BuiltInProviderIds {
    val OPENAI = ProviderId("openai")
    val OPENAI_VISION = ProviderId("openai-vision")
    val GEMINI_NANO_BANANA = ProviderId("gemini-nano-banana")
    val GEMINI_VISION = ProviderId("gemini-vision")
    val DEEPSEEK_VISION = ProviderId("deepseek-vision")
}

object BuiltInProviderProtocols {
    const val OPENAI_IMAGES_V1 = "openai-images-v1"
    const val OPENAI_CHAT_COMPLETIONS_V1 = "openai-chat-completions-v1"
    const val GEMINI_GENERATE_CONTENT_V1BETA = "gemini-generate-content-v1beta"
}

/** Stable catalog used by configuration UIs without exposing adapter classes. */
object BuiltInProviderCatalog {
    val ids: List<ProviderId> = listOf(
        BuiltInProviderIds.OPENAI,
        BuiltInProviderIds.OPENAI_VISION,
        BuiltInProviderIds.GEMINI_NANO_BANANA,
        BuiltInProviderIds.GEMINI_VISION,
        BuiltInProviderIds.DEEPSEEK_VISION,
    )
}

fun ImageGeneration.Builder.registerBuiltIns(): ImageGeneration.Builder {
    requireNoRegisteredProviders(BuiltInProviderCatalog.ids)
    val sharedTransport = defaultHttpTransport()
    return register(OpenAiImageProvider(sharedTransport))
        .register(GeminiNanoBananaProvider(sharedTransport))
        .register(OpenAiVisionProvider(sharedTransport))
        .register(GeminiVisionProvider(sharedTransport))
        .register(DeepSeekVisionProvider(sharedTransport))
}
