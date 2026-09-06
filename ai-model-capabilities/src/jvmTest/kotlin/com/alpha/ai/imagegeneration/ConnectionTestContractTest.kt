package com.alpha.ai.imagegeneration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class ConnectionTestContractTest {
    @Test
    fun `client discovers adapters and delegates connection checks without upper layer switches`() = runTest {
        var calls = 0
        val adapter = object : AiProviderAdapter<TestConfig> {
            override val descriptor = ProviderDescriptor(
                id = TEST_PROVIDER,
                displayName = "Test vision",
                supportedProtocolVersions = setOf(TEST_PROTOCOL),
                capabilities = ProviderCapabilities(
                    supportedInputMimeTypes = setOf("image/png"),
                    maxInputBytes = 1024,
                    supportedCapabilities = setOf(AiCapability.IMAGE_UNDERSTANDING),
                ),
            )
            override val configType = TestConfig::class

            override suspend fun testConnection(
                capability: AiCapability,
                config: TestConfig,
            ): ProviderConnectionTestResult {
                calls += 1
                return ProviderConnectionTestResult.Available(config.providerId, capability)
            }
        }
        val client = AiModel.builder().register(adapter).build()
        val config = TestConfig()

        try {
            assertEquals(listOf(TEST_PROVIDER), client.providerDescriptors().map { it.id })
            assertIs<ProviderConnectionTestResult.Available>(
                client.testConnection(AiCapability.IMAGE_UNDERSTANDING, config),
            )
            assertEquals(1, calls)
        } finally {
            config.apiToken.close()
        }
    }

    @Test
    fun `client rejects unsupported capability before invoking adapter`() = runTest {
        var called = false
        val adapter = object : AiProviderAdapter<TestConfig> {
            override val descriptor = ProviderDescriptor(
                id = TEST_PROVIDER,
                displayName = "Test vision",
                supportedProtocolVersions = setOf(TEST_PROTOCOL),
                capabilities = ProviderCapabilities(
                    supportedInputMimeTypes = setOf("image/png"),
                    maxInputBytes = 1024,
                    supportedCapabilities = setOf(AiCapability.IMAGE_UNDERSTANDING),
                ),
            )
            override val configType = TestConfig::class
            override suspend fun testConnection(
                capability: AiCapability,
                config: TestConfig,
            ): ProviderConnectionTestResult {
                called = true
                return ProviderConnectionTestResult.Available(config.providerId, capability)
            }
        }
        val config = TestConfig()

        try {
            val result = AiModel.builder().register(adapter).build()
                .testConnection(AiCapability.IMAGE_TO_IMAGE, config)

            assertEquals(false, called)
            assertEquals(
                ImageGenerationErrorCategory.UNSUPPORTED_PROVIDER_PROTOCOL,
                assertIs<ProviderConnectionTestResult.Unavailable>(result).error.category,
            )
        } finally {
            config.apiToken.close()
        }
    }

    @Test
    fun `client delegates model discovery through the adapter seam`() = runTest {
        val adapter = object : AiProviderAdapter<TestConfig> {
            override val descriptor = ProviderDescriptor(
                id = TEST_PROVIDER,
                displayName = "Test multimodal",
                supportedProtocolVersions = setOf(TEST_PROTOCOL),
                capabilities = ProviderCapabilities(
                    supportedInputMimeTypes = setOf("image/png"),
                    maxInputBytes = 1024,
                    supportedCapabilities = setOf(AiCapability.IMAGE_UNDERSTANDING),
                ),
            )
            override val configType = TestConfig::class

            override suspend fun listModels(request: ProviderModelCatalogRequest) =
                ProviderModelCatalogResult.Available(
                    providerId = request.providerId,
                    capability = request.capability,
                    models = listOf(
                        ProviderModel(" model-b "),
                        ProviderModel("model-a", " Model A "),
                        ProviderModel("model-a"),
                    ),
                )
        }
        val request = ProviderModelCatalogRequest(
            providerId = TEST_PROVIDER,
            capability = AiCapability.IMAGE_UNDERSTANDING,
            baseUrl = "https://api.example.test/v1",
            protocolVersion = TEST_PROTOCOL,
            apiToken = SecretValue("token"),
        )

        try {
            val result = AiModel.builder().register(adapter).build().listModels(request)
            val available = assertIs<ProviderModelCatalogResult.Available>(result)
            assertEquals(listOf("model-b", "model-a"), available.models.map { it.id })
            assertEquals("Model A", available.models.last().displayName)
        } finally {
            request.apiToken.close()
        }
    }

    private class TestConfig : ProviderRuntimeConfig {
        override val providerId = TEST_PROVIDER
        override val baseUrl = "https://api.example.test/v1"
        override val model = "vision-model"
        override val protocolVersion = TEST_PROTOCOL
        override val apiToken = SecretValue("token")
        override val allowInsecureHttp = false
    }

    private companion object {
        val TEST_PROVIDER = ProviderId("test-vision")
        const val TEST_PROTOCOL = "test-vision-v1"
    }
}
