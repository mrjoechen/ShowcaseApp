package com.alpha.ai.imagegeneration

import com.alpha.ai.imagegeneration.internal.DefaultAiModelClient
import kotlin.jvm.JvmStatic

object ImageGeneration {
    @JvmStatic
    fun builder(): Builder = Builder()

    class Builder {
        private val providers = LinkedHashMap<ProviderId, AiProviderAdapter<*>>()
        private var requestLogger: AiRequestLogger = AiRequestLogger.NONE

        fun <C : ProviderRuntimeConfig> register(provider: AiProviderAdapter<C>): Builder {
            require(provider.descriptor.id !in providers) {
                "Provider already registered: ${provider.descriptor.id.value}"
            }
            providers[provider.descriptor.id] = provider
            return this
        }

        /** Installs a structured logger. The module remains silent when this is not called. */
        fun requestLogger(logger: AiRequestLogger): Builder {
            requestLogger = logger
            return this
        }

        internal fun requireNoRegisteredProviders(providerIds: Iterable<ProviderId>) {
            val duplicate = providerIds.firstOrNull { it in providers }
            require(duplicate == null) { "Provider already registered: ${duplicate?.value}" }
        }

        fun build(): ImageGenerationClient = DefaultAiModelClient(
            providers = providers.toMap(),
            requestLogger = requestLogger,
        )
    }
}

/** Capability-oriented facade for new integrations; [ImageGeneration] remains compatible. */
object AiModel {
    @JvmStatic
    fun builder(): ImageGeneration.Builder = ImageGeneration.builder()
}
