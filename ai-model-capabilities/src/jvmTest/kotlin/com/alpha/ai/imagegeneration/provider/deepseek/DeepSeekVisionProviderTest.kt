package com.alpha.ai.imagegeneration.provider.deepseek

import com.alpha.ai.imagegeneration.AiCapability
import com.alpha.ai.imagegeneration.ImageUnderstandingRequest
import com.alpha.ai.imagegeneration.OperationId
import com.alpha.ai.imagegeneration.SecretValue
import com.alpha.ai.imagegeneration.fixtureSource
import com.alpha.ai.imagegeneration.internal.http.HttpTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeepSeekVisionProviderTest {
    @Test
    fun `uses documented DeepSeek base URL and vision capability`() {
        val config = DeepSeekVisionConfig(SecretValue("token"), "deepseek-v4-flash-vision-exp")

        assertEquals("https://api.deepseek.com", config.baseUrl)
        assertEquals("deepseek-vision", config.providerId.value)
        assertEquals("openai-chat-completions-v1", config.protocolVersion)
        assertTrue(DeepSeekVisionProvider().descriptor.capabilities.supportedInputMimeTypes.contains("image/gif"))
        assertTrue(AiCapability.IMAGE_UNDERSTANDING in DeepSeekVisionProvider().descriptor.capabilities.supportedCapabilities)
    }
}
