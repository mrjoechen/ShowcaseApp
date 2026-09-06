package com.alpha.ai.imagegeneration

import com.alpha.ai.imagegeneration.provider.BuiltInProviderIds
import com.alpha.ai.imagegeneration.provider.BuiltInProviderCatalog
import com.alpha.ai.imagegeneration.provider.BuiltInProviderProtocols
import com.alpha.ai.imagegeneration.provider.registerBuiltIns
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class BuiltInProvidersTest {
    @Test
    fun `register built ins makes all convenience providers available while keeping custom registration open`() {
        val custom = FakeProvider()
        val client = ImageGeneration.builder().registerBuiltIns().register(custom).build()

        assertNotNull(client.providerDescriptor(BuiltInProviderIds.OPENAI))
        assertNotNull(client.providerDescriptor(BuiltInProviderIds.OPENAI_VISION))
        assertNotNull(client.providerDescriptor(BuiltInProviderIds.GEMINI_NANO_BANANA))
        assertNotNull(client.providerDescriptor(BuiltInProviderIds.GEMINI_VISION))
        assertNotNull(client.providerDescriptor(BuiltInProviderIds.DEEPSEEK_VISION))
        assertEquals(BuiltInProviderCatalog.ids.toSet(), setOf(
            BuiltInProviderIds.OPENAI,
            BuiltInProviderIds.OPENAI_VISION,
            BuiltInProviderIds.GEMINI_NANO_BANANA,
            BuiltInProviderIds.GEMINI_VISION,
            BuiltInProviderIds.DEEPSEEK_VISION,
        ))
        assertEquals(custom.descriptor, client.providerDescriptor(ProviderId("fake")))
        assertEquals("openai-images-v1", BuiltInProviderProtocols.OPENAI_IMAGES_V1)
        assertEquals("gemini-generate-content-v1beta", BuiltInProviderProtocols.GEMINI_GENERATE_CONTENT_V1BETA)
    }

    @Test
    fun `register built ins fails when caller already registered a built in id`() {
        assertFailsWith<IllegalArgumentException> {
            ImageGeneration.builder().register(object : ImageGenerationProvider<FakeConfig> {
                override val descriptor = FakeProvider().descriptor.copy(id = BuiltInProviderIds.OPENAI)
                override val configType = FakeConfig::class
                override fun generate(request: GenerateImageRequest, config: FakeConfig) = FakeProvider().generate(request, config)
            }).registerBuiltIns()
        }
    }
}
