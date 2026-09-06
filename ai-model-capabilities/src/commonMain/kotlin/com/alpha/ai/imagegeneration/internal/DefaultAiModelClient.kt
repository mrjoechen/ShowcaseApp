package com.alpha.ai.imagegeneration.internal

import com.alpha.ai.imagegeneration.AiCapability
import com.alpha.ai.imagegeneration.AiProviderAdapter
import com.alpha.ai.imagegeneration.AiRequestLogEvent
import com.alpha.ai.imagegeneration.AiRequestLogger
import com.alpha.ai.imagegeneration.AiRequestOutcome
import com.alpha.ai.imagegeneration.GenerateImageRequest
import com.alpha.ai.imagegeneration.GeneratedImage
import com.alpha.ai.imagegeneration.GenerationResult
import com.alpha.ai.imagegeneration.GenerationStage
import com.alpha.ai.imagegeneration.ImageGenerationClient
import com.alpha.ai.imagegeneration.ImageGenerationError
import com.alpha.ai.imagegeneration.ImageGenerationErrorCategory
import com.alpha.ai.imagegeneration.ImageGenerationEvent
import com.alpha.ai.imagegeneration.ImageSource
import com.alpha.ai.imagegeneration.ImageUnderstandingEvent
import com.alpha.ai.imagegeneration.ImageUnderstandingRequest
import com.alpha.ai.imagegeneration.ImageUnderstandingResult
import com.alpha.ai.imagegeneration.OperationId
import com.alpha.ai.imagegeneration.ProviderConnectionTestResult
import com.alpha.ai.imagegeneration.ProviderDescriptor
import com.alpha.ai.imagegeneration.ProviderGenerationEvent
import com.alpha.ai.imagegeneration.ProviderId
import com.alpha.ai.imagegeneration.ProviderModel
import com.alpha.ai.imagegeneration.ProviderModelCatalogRequest
import com.alpha.ai.imagegeneration.ProviderModelCatalogResult
import com.alpha.ai.imagegeneration.ProviderRuntimeConfig
import com.alpha.ai.imagegeneration.ProviderUnderstandingEvent
import com.alpha.ai.imagegeneration.RequestPhase
import com.alpha.ai.imagegeneration.RetryAdvice
import com.alpha.ai.imagegeneration.internal.http.BaseUrlValidator
import com.alpha.ai.imagegeneration.internal.http.ImageBytesValidator
import com.alpha.ai.imagegeneration.internal.http.ImageGenerationTransportException
import com.alpha.ai.imagegeneration.internal.http.ResponseLimits
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import okio.use

internal class DefaultAiModelClient(
    private val providers: Map<ProviderId, AiProviderAdapter<*>>,
    private val requestLogger: AiRequestLogger = AiRequestLogger.NONE,
    private val nanoTime: () -> Long = { MONOTONIC_ORIGIN.elapsedNow().inWholeNanoseconds },
) : ImageGenerationClient {
    override fun providerDescriptor(providerId: ProviderId) = providers[providerId]?.descriptor

    override fun providerDescriptors(): List<ProviderDescriptor> = providers.values.map { it.descriptor }

    override suspend fun testConnection(
        capability: AiCapability,
        config: ProviderRuntimeConfig,
    ): ProviderConnectionTestResult {
        val provider = providers[config.providerId]
            ?: return connectionFailure(ImageGenerationErrorCategory.CONFIGURATION)
        if (!provider.configType.isInstance(config)) {
            return connectionFailure(ImageGenerationErrorCategory.CONFIGURATION)
        }
        if (capability !in provider.descriptor.capabilities.supportedCapabilities) {
            return connectionFailure(ImageGenerationErrorCategory.UNSUPPORTED_PROVIDER_PROTOCOL)
        }
        if (config.protocolVersion !in provider.descriptor.supportedProtocolVersions) {
            return connectionFailure(ImageGenerationErrorCategory.UNSUPPORTED_PROVIDER_PROTOCOL)
        }
        try {
            BaseUrlValidator.validate(config.baseUrl, config.allowInsecureHttp)
        } catch (_: ImageGenerationTransportException) {
            return connectionFailure(ImageGenerationErrorCategory.CONFIGURATION)
        }

        @Suppress("UNCHECKED_CAST")
        val typedProvider = provider as AiProviderAdapter<ProviderRuntimeConfig>
        val result = try {
            typedProvider.testConnection(capability, config)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            connectionFailure(ImageGenerationErrorCategory.SERVER)
        }
        return when (result) {
            is ProviderConnectionTestResult.Available -> {
                if (result.providerId == provider.descriptor.id && result.capability == capability) result
                else connectionFailure(ImageGenerationErrorCategory.MALFORMED_RESPONSE)
            }
            is ProviderConnectionTestResult.Unavailable -> result.copy(
                error = result.error.copy(
                    requestMayHaveBeenAccepted = false,
                    providerRequestId = null,
                    safeMessage = null,
                    diagnosticCode = null,
                ),
            )
        }
    }

    override suspend fun listModels(
        request: ProviderModelCatalogRequest,
    ): ProviderModelCatalogResult {
        val provider = providers[request.providerId] ?: return modelCatalogFailure(
            ImageGenerationErrorCategory.CONFIGURATION,
        )
        if (request.capability !in provider.descriptor.capabilities.supportedCapabilities ||
            request.protocolVersion !in provider.descriptor.supportedProtocolVersions
        ) {
            return modelCatalogFailure(ImageGenerationErrorCategory.UNSUPPORTED_PROVIDER_PROTOCOL)
        }
        try {
            BaseUrlValidator.validate(request.baseUrl, request.allowInsecureHttp)
        } catch (_: ImageGenerationTransportException) {
            return modelCatalogFailure(ImageGenerationErrorCategory.CONFIGURATION)
        }

        val result = try {
            provider.listModels(request)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            modelCatalogFailure(ImageGenerationErrorCategory.SERVER)
        }
        return when (result) {
            is ProviderModelCatalogResult.Available -> {
                if (result.providerId != provider.descriptor.id || result.capability != request.capability) {
                    modelCatalogFailure(ImageGenerationErrorCategory.MALFORMED_RESPONSE)
                } else {
                    result.copy(models = sanitizeModels(result.models))
                }
            }
            ProviderModelCatalogResult.Unsupported -> result
            is ProviderModelCatalogResult.Unavailable -> result.copy(
                error = result.error.copy(
                    requestMayHaveBeenAccepted = false,
                    providerRequestId = null,
                    safeMessage = null,
                    diagnosticCode = null,
                ),
            )
        }
    }

    override fun generate(
        request: GenerateImageRequest,
        config: ProviderRuntimeConfig,
    ): Flow<ImageGenerationEvent> {
        val events = generateInternal(request, config)
        return if (requestLogger === AiRequestLogger.NONE) events else events.withRequestLogging(
            capability = AiCapability.IMAGE_TO_IMAGE,
            operationId = request.operationId,
            source = request.source,
            config = config,
        )
    }

    private fun sanitizeModels(models: List<ProviderModel>): List<ProviderModel> = models
        .asSequence()
        .mapNotNull { model ->
            val id = model.id.trim()
            if (id.isEmpty() || id.length > MAX_MODEL_ID_LENGTH || id.any(Char::isISOControl)) null
            else ProviderModel(
                id = id,
                displayName = model.displayName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && it.length <= MAX_MODEL_ID_LENGTH && it.none(Char::isISOControl) },
            )
        }
        .distinctBy { it.id }
        .take(MAX_MODEL_COUNT)
        .toList()

    private fun generateInternal(
        request: GenerateImageRequest,
        config: ProviderRuntimeConfig,
    ): Flow<ImageGenerationEvent> = flow {
        emit(ImageGenerationEvent.Stage(request.operationId, GenerationStage.VALIDATING_CONFIGURATION))

        val provider = providers[config.providerId]
        if (provider == null) {
            emit(completed(request.operationId, ImageGenerationErrorCategory.CONFIGURATION, "Unknown provider"))
            return@flow
        }
        if (request.prompt.isBlank()) {
            emit(completed(request.operationId, ImageGenerationErrorCategory.INVALID_REQUEST, "Prompt must not be blank"))
            return@flow
        }
        if (request.providerOptions?.providerId != null && request.providerOptions.providerId != config.providerId) {
            emit(completed(request.operationId, ImageGenerationErrorCategory.INVALID_REQUEST, "Provider options do not match provider"))
            return@flow
        }
        if (!provider.configType.isInstance(config)) {
            emit(completed(request.operationId, ImageGenerationErrorCategory.CONFIGURATION, "Provider configuration type does not match"))
            return@flow
        }
        if (AiCapability.IMAGE_TO_IMAGE !in provider.descriptor.capabilities.supportedCapabilities) {
            emit(completed(request.operationId, ImageGenerationErrorCategory.UNSUPPORTED_PROVIDER_PROTOCOL, "Provider does not support image generation"))
            return@flow
        }
        if (config.protocolVersion !in provider.descriptor.supportedProtocolVersions) {
            emit(completed(request.operationId, ImageGenerationErrorCategory.UNSUPPORTED_PROVIDER_PROTOCOL, "Unsupported provider protocol"))
            return@flow
        }
        try {
            BaseUrlValidator.validate(config.baseUrl, config.allowInsecureHttp)
        } catch (_: ImageGenerationTransportException) {
            emit(completed(request.operationId, ImageGenerationErrorCategory.CONFIGURATION, "Invalid provider URL"))
            return@flow
        }
        val capabilities = provider.descriptor.capabilities
        if (capabilities.supportedInputMimeTypes.isNotEmpty() &&
            request.source.mimeType !in capabilities.supportedInputMimeTypes
        ) {
            emit(completed(request.operationId, ImageGenerationErrorCategory.INVALID_REQUEST, "Unsupported input MIME type"))
            return@flow
        }
        if (capabilities.maxInputBytes > 0 && request.source.contentLength != null &&
            request.source.contentLength!! > capabilities.maxInputBytes
        ) {
            emit(completed(request.operationId, ImageGenerationErrorCategory.INVALID_REQUEST, "Input exceeds provider limit"))
            return@flow
        }
        if (capabilities.maxInputBytes > 0) {
            val sourceFits = try {
                sourceFitsLimit(request.source, capabilities.maxInputBytes)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emit(completed(request.operationId, ImageGenerationErrorCategory.INVALID_REQUEST, "Unable to read input source"))
                return@flow
            }
            if (!sourceFits) {
                emit(completed(request.operationId, ImageGenerationErrorCategory.INVALID_REQUEST, "Input exceeds provider limit"))
                return@flow
            }
        }

        @Suppress("UNCHECKED_CAST")
        val typedProvider = provider as AiProviderAdapter<ProviderRuntimeConfig>
        flow {
            emitAll(typedProvider.generateImage(request, config))
        }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(
                    ProviderGenerationEvent.Completed(
                        GenerationResult.Failure(
                            ImageGenerationError(
                                category = ImageGenerationErrorCategory.SERVER,
                                retryAdvice = RetryAdvice.AMBIGUOUS,
                                requestMayHaveBeenAccepted = false,
                            ),
                        ),
                    ),
                )
            }
            .mapToClientEvents(
                operationId = request.operationId,
                descriptor = provider.descriptor,
                config = config,
                callerHasIdempotencyKey = !request.idempotencyKey.isNullOrBlank(),
            )
            .collect { emit(it) }
    }

    override fun understandImage(
        request: ImageUnderstandingRequest,
        config: ProviderRuntimeConfig,
    ): Flow<ImageUnderstandingEvent> {
        val events = understandImageInternal(request, config)
        return if (requestLogger === AiRequestLogger.NONE) events else events.withUnderstandingRequestLogging(
            capability = AiCapability.IMAGE_UNDERSTANDING,
            operationId = request.operationId,
            source = request.source,
            config = config,
        )
    }

    private fun understandImageInternal(
        request: ImageUnderstandingRequest,
        config: ProviderRuntimeConfig,
    ): Flow<ImageUnderstandingEvent> = flow {
        emit(ImageUnderstandingEvent.Stage(request.operationId, GenerationStage.VALIDATING_CONFIGURATION))

        val provider = providers[config.providerId]
        if (provider == null) {
            emit(understandingCompleted(request.operationId, ImageGenerationErrorCategory.CONFIGURATION, "Unknown provider"))
            return@flow
        }
        if (request.prompt.isBlank()) {
            emit(understandingCompleted(request.operationId, ImageGenerationErrorCategory.INVALID_REQUEST, "Prompt must not be blank"))
            return@flow
        }
        if (request.providerOptions?.providerId != null && request.providerOptions.providerId != config.providerId) {
            emit(understandingCompleted(request.operationId, ImageGenerationErrorCategory.INVALID_REQUEST, "Provider options do not match provider"))
            return@flow
        }
        if (!provider.configType.isInstance(config)) {
            emit(understandingCompleted(request.operationId, ImageGenerationErrorCategory.CONFIGURATION, "Provider configuration type does not match"))
            return@flow
        }
        if (AiCapability.IMAGE_UNDERSTANDING !in provider.descriptor.capabilities.supportedCapabilities) {
            emit(understandingCompleted(request.operationId, ImageGenerationErrorCategory.UNSUPPORTED_PROVIDER_PROTOCOL, "Provider does not support image understanding"))
            return@flow
        }
        if (config.protocolVersion !in provider.descriptor.supportedProtocolVersions) {
            emit(understandingCompleted(request.operationId, ImageGenerationErrorCategory.UNSUPPORTED_PROVIDER_PROTOCOL, "Unsupported provider protocol"))
            return@flow
        }
        try {
            BaseUrlValidator.validate(config.baseUrl, config.allowInsecureHttp)
        } catch (_: ImageGenerationTransportException) {
            emit(understandingCompleted(request.operationId, ImageGenerationErrorCategory.CONFIGURATION, "Invalid provider URL"))
            return@flow
        }
        val capabilities = provider.descriptor.capabilities
        if (capabilities.supportedInputMimeTypes.isNotEmpty() && request.source.mimeType !in capabilities.supportedInputMimeTypes) {
            emit(understandingCompleted(request.operationId, ImageGenerationErrorCategory.INVALID_REQUEST, "Unsupported input MIME type"))
            return@flow
        }
        if (capabilities.maxInputBytes > 0 && request.source.contentLength != null && request.source.contentLength!! > capabilities.maxInputBytes) {
            emit(understandingCompleted(request.operationId, ImageGenerationErrorCategory.INVALID_REQUEST, "Input exceeds provider limit"))
            return@flow
        }
        if (capabilities.maxInputBytes > 0) {
            val sourceFits = try {
                sourceFitsLimit(request.source, capabilities.maxInputBytes)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emit(understandingCompleted(request.operationId, ImageGenerationErrorCategory.INVALID_REQUEST, "Unable to read input source"))
                return@flow
            }
            if (!sourceFits) {
                emit(understandingCompleted(request.operationId, ImageGenerationErrorCategory.INVALID_REQUEST, "Input exceeds provider limit"))
                return@flow
            }
        }

        @Suppress("UNCHECKED_CAST")
        val typedProvider = provider as AiProviderAdapter<ProviderRuntimeConfig>
        flow {
            emitAll(typedProvider.understandImage(request, config))
        }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(
                    ProviderUnderstandingEvent.Completed(
                        ImageUnderstandingResult.Failure(
                            ImageGenerationError(
                                category = ImageGenerationErrorCategory.SERVER,
                                retryAdvice = RetryAdvice.AMBIGUOUS,
                                requestMayHaveBeenAccepted = false,
                            ),
                        ),
                    ),
                )
            }
            .mapToClientUnderstandingEvents(
                operationId = request.operationId,
                descriptor = provider.descriptor,
                config = config,
                callerHasIdempotencyKey = !request.idempotencyKey.isNullOrBlank(),
            )
            .collect { emit(it) }
    }

    private fun Flow<ImageGenerationEvent>.withRequestLogging(
        capability: AiCapability,
        operationId: OperationId,
        source: ImageSource,
        config: ProviderRuntimeConfig,
    ): Flow<ImageGenerationEvent> = flow {
        val logContext = startRequestLog(capability, operationId, source, config)
        var terminalLogged = false
        try {
            collect { event ->
                when (event) {
                    is ImageGenerationEvent.Stage -> logContext.stage(event.value)
                    is ImageGenerationEvent.Completed -> {
                        logContext.complete(event.result.toLogTerminal())
                        terminalLogged = true
                    }
                    is ImageGenerationEvent.Preview -> Unit
                }
                emit(event)
            }
            if (!terminalLogged) logContext.complete(LogTerminal.failure(ImageGenerationErrorCategory.MALFORMED_RESPONSE))
        } catch (cancelled: CancellationException) {
            if (!terminalLogged) logContext.complete(LogTerminal.cancelled())
            throw cancelled
        } catch (failure: Throwable) {
            if (!terminalLogged) logContext.complete(LogTerminal.failure(ImageGenerationErrorCategory.SERVER))
            throw failure
        }
    }

    private fun Flow<ImageUnderstandingEvent>.withUnderstandingRequestLogging(
        capability: AiCapability,
        operationId: OperationId,
        source: ImageSource,
        config: ProviderRuntimeConfig,
    ): Flow<ImageUnderstandingEvent> = flow {
        val logContext = startRequestLog(capability, operationId, source, config)
        var terminalLogged = false
        try {
            collect { event ->
                when (event) {
                    is ImageUnderstandingEvent.Stage -> logContext.stage(event.value)
                    is ImageUnderstandingEvent.Completed -> {
                        logContext.complete(event.result.toLogTerminal())
                        terminalLogged = true
                    }
                }
                emit(event)
            }
            if (!terminalLogged) logContext.complete(LogTerminal.failure(ImageGenerationErrorCategory.MALFORMED_RESPONSE))
        } catch (cancelled: CancellationException) {
            if (!terminalLogged) logContext.complete(LogTerminal.cancelled())
            throw cancelled
        } catch (failure: Throwable) {
            if (!terminalLogged) logContext.complete(LogTerminal.failure(ImageGenerationErrorCategory.SERVER))
            throw failure
        }
    }

    private fun startRequestLog(
        capability: AiCapability,
        operationId: OperationId,
        source: ImageSource,
        config: ProviderRuntimeConfig,
    ): RequestLogContext {
        val context = RequestLogContext(
            capability = capability,
            providerId = config.providerId,
            model = safeModel(config),
            operationFingerprint = operationFingerprint(operationId),
            startedNanos = nanoTime(),
        )
        safeLog(
            AiRequestLogEvent.Started(
                capability = capability,
                providerId = config.providerId,
                model = context.model,
                operationFingerprint = context.operationFingerprint,
                inputMimeType = source.mimeType.takeIf(SAFE_MIME_TYPE::matches) ?: "unknown",
                inputBytes = source.contentLength?.takeIf { it >= 0L },
            ),
        )
        return context
    }

    private inner class RequestLogContext(
        val capability: AiCapability,
        val providerId: ProviderId,
        val model: String?,
        val operationFingerprint: String,
        val startedNanos: Long,
    ) {
        fun stage(stage: GenerationStage) = safeLog(
            AiRequestLogEvent.Stage(
                capability = capability,
                providerId = providerId,
                model = model,
                operationFingerprint = operationFingerprint,
                stage = stage,
                elapsedMillis = elapsedMillis(),
            ),
        )

        fun complete(terminal: LogTerminal) = safeLog(
            AiRequestLogEvent.Completed(
                capability = capability,
                providerId = providerId,
                model = model,
                operationFingerprint = operationFingerprint,
                outcome = terminal.outcome,
                errorCategory = terminal.errorCategory,
                elapsedMillis = elapsedMillis(),
            ),
        )

        private fun elapsedMillis(): Long =
            ((nanoTime() - startedNanos).coerceAtLeast(0L) / NANOS_PER_MILLISECOND)
    }

    private fun safeLog(event: AiRequestLogEvent) {
        try {
            requestLogger.log(event)
        } catch (_: Exception) {
            // Diagnostics must never change provider request behavior.
        }
    }

    private fun safeModel(config: ProviderRuntimeConfig): String? {
        val model = config.model?.takeIf(SAFE_MODEL::matches) ?: return null
        val containsCredential = config.apiToken.use { credential -> model.contains(credential) }
        return model.takeUnless { containsCredential }
    }

    private fun String.contains(value: CharArray): Boolean {
        if (value.isEmpty() || value.size > length) return false
        for (start in 0..length - value.size) {
            var matches = true
            for (offset in value.indices) {
                if (this[start + offset] != value[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return true
        }
        return false
    }

    private fun operationFingerprint(operationId: OperationId): String =
        operationId.value.encodeUtf8().sha256().substring(0, OPERATION_FINGERPRINT_BYTES).hex()

    private fun GenerationResult.toLogTerminal(): LogTerminal = when (this) {
        is GenerationResult.Success -> LogTerminal.success()
        is GenerationResult.Failure -> LogTerminal.failure(error.category)
        is GenerationResult.ProviderCancelled -> LogTerminal.cancelled()
    }

    private fun ImageUnderstandingResult.toLogTerminal(): LogTerminal = when (this) {
        is ImageUnderstandingResult.Success -> LogTerminal.success()
        is ImageUnderstandingResult.Failure -> LogTerminal.failure(error.category)
        is ImageUnderstandingResult.ProviderCancelled -> LogTerminal.cancelled()
    }

    private data class LogTerminal(
        val outcome: AiRequestOutcome,
        val errorCategory: ImageGenerationErrorCategory?,
    ) {
        companion object {
            fun success() = LogTerminal(AiRequestOutcome.SUCCESS, null)
            fun failure(category: ImageGenerationErrorCategory) = LogTerminal(AiRequestOutcome.FAILURE, category)
            fun cancelled() = LogTerminal(AiRequestOutcome.CANCELLED, ImageGenerationErrorCategory.CANCELLED)
        }
    }

    private suspend fun sourceFitsLimit(source: ImageSource, limit: Long): Boolean {
        return source.openSource().use { input ->
            val buffer = Buffer()
            var total = 0L
            var fits = true
            while (true) {
                currentCoroutineContext().ensureActive()
                val remaining = limit - total
                val count = input.read(buffer, minOf(8192L, if (remaining == Long.MAX_VALUE) remaining else remaining + 1))
                buffer.clear()
                if (count < 0) break
                total += count
                if (total > limit) {
                    fits = false
                    break
                }
            }
            fits
        }
    }

    private fun connectionFailure(category: ImageGenerationErrorCategory) =
        ProviderConnectionTestResult.Unavailable(
            ImageGenerationError(
                category = category,
                retryAdvice = RetryAdvice.DO_NOT_RETRY,
                requestMayHaveBeenAccepted = false,
            ),
        )

    private fun modelCatalogFailure(category: ImageGenerationErrorCategory) =
        ProviderModelCatalogResult.Unavailable(
            ImageGenerationError(
                category = category,
                retryAdvice = RetryAdvice.DO_NOT_RETRY,
                requestMayHaveBeenAccepted = false,
                definitelyUnprocessed = true,
            ),
        )

    private fun completed(
        operationId: OperationId,
        category: ImageGenerationErrorCategory,
        message: String,
    ) = ImageGenerationEvent.Completed(
        operationId,
        GenerationResult.Failure(
            ImageGenerationError(
                category = category,
                retryAdvice = RetryAdvice.DO_NOT_RETRY,
                requestMayHaveBeenAccepted = false,
                safeMessage = message,
            ),
        ),
    )

    private fun understandingCompleted(
        operationId: OperationId,
        category: ImageGenerationErrorCategory,
        message: String,
    ) = ImageUnderstandingEvent.Completed(
        operationId,
        ImageUnderstandingResult.Failure(
            ImageGenerationError(
                category = category,
                retryAdvice = RetryAdvice.DO_NOT_RETRY,
                requestMayHaveBeenAccepted = false,
                safeMessage = message,
            ),
        ),
    )

    private fun Flow<ProviderUnderstandingEvent>.mapToClientUnderstandingEvents(
        operationId: OperationId,
        descriptor: ProviderDescriptor,
        config: ProviderRuntimeConfig,
        callerHasIdempotencyKey: Boolean,
    ): Flow<ImageUnderstandingEvent> = flow {
        val secrets = config.apiToken.use { listOf(it.concatToString()) }
        var lastStage = GenerationStage.VALIDATING_CONFIGURATION
        var phase = RequestPhase.BEFORE_DISPATCH
        var terminalEmitted = false
        val terminalAbort = ProviderCollectionComplete()

        try {
            collect { event ->
                when (event) {
                    is ProviderUnderstandingEvent.Stage -> {
                        val reliableBoundary = descriptor.capabilities.reliableDispatchBoundary
                        val generatingWithoutPersistedFence = reliableBoundary &&
                            event.value == GenerationStage.GENERATING &&
                            lastStage.ordinal < GenerationStage.UPLOADING.ordinal
                        val postStartStageWithoutGenerating = reliableBoundary &&
                            event.value.ordinal > GenerationStage.GENERATING.ordinal &&
                            phase != RequestPhase.IN_FLIGHT
                        val violatesOrder = event.value.ordinal <= lastStage.ordinal ||
                            generatingWithoutPersistedFence || postStartStageWithoutGenerating
                        if (violatesOrder) {
                            emit(understandingMalformedTerminal(operationId, "Provider emitted an invalid stage sequence", secrets))
                            terminalEmitted = true
                        } else {
                            lastStage = event.value
                            if (event.value == GenerationStage.GENERATING) phase = RequestPhase.IN_FLIGHT
                            emit(ImageUnderstandingEvent.Stage(operationId, event.value))
                        }
                    }

                    is ProviderUnderstandingEvent.Completed -> {
                        val successWithoutDispatchFence = descriptor.capabilities.reliableDispatchBoundary &&
                            event.result is ImageUnderstandingResult.Success && phase != RequestPhase.IN_FLIGHT
                        if (successWithoutDispatchFence) {
                            emit(understandingMalformedTerminal(operationId, "Provider completed without a dispatch stage", secrets))
                        } else {
                            emit(ImageUnderstandingEvent.Completed(
                                operationId,
                                sanitizeUnderstandingResult(event.result, descriptor, secrets, phase, callerHasIdempotencyKey),
                            ))
                        }
                        terminalEmitted = true
                    }
                }
                if (terminalEmitted) throw terminalAbort
            }
        } catch (error: ProviderCollectionComplete) {
            if (error !== terminalAbort) throw error
        }
        if (!terminalEmitted) emit(understandingMalformedTerminal(operationId, "Provider completed without a terminal result", secrets))
    }

    private fun understandingMalformedTerminal(
        operationId: OperationId,
        message: String,
        secrets: List<String>,
    ) = ImageUnderstandingEvent.Completed(
        operationId,
        ImageUnderstandingResult.Failure(
            ImageGenerationError(
                category = ImageGenerationErrorCategory.MALFORMED_RESPONSE,
                retryAdvice = RetryAdvice.DO_NOT_RETRY,
                requestMayHaveBeenAccepted = false,
                safeMessage = SensitiveDataRedactor.redactAndBound(message, secrets, MaxMessageLength),
            ),
        ),
    )

    private fun sanitizeUnderstandingResult(
        result: ImageUnderstandingResult,
        descriptor: ProviderDescriptor,
        secrets: List<String>,
        phase: RequestPhase,
        callerHasIdempotencyKey: Boolean,
    ): ImageUnderstandingResult = when (result) {
        is ImageUnderstandingResult.Success -> {
            if (result.output.providerId != descriptor.id) {
                ImageUnderstandingResult.Failure(ImageGenerationError(
                    category = ImageGenerationErrorCategory.MALFORMED_RESPONSE,
                    retryAdvice = RetryAdvice.DO_NOT_RETRY,
                    requestMayHaveBeenAccepted = phase == RequestPhase.IN_FLIGHT,
                ))
            } else {
                ImageUnderstandingResult.Success(result.output.copy(
                    data = sanitizeJson(result.output.data, secrets) as JsonObject,
                    model = SensitiveDataRedactor.redactAndBound(result.output.model, secrets, MaxModelLength),
                    providerRequestId = safeProviderRequestId(result.output.providerRequestId, secrets),
                    metadata = result.output.metadata
                        .filterKeys { it in descriptor.safeMetadataKeys }
                        .mapValues { (_, value) -> SensitiveDataRedactor.redact(value, secrets).take(MaxMetadataValueLength) },
                ))
            }
        }
        is ImageUnderstandingResult.ProviderCancelled -> result.copy(message = null)
        is ImageUnderstandingResult.Failure -> result.copy(error = result.error.copy(
            retryAdvice = ProviderFailureMapper.merge(
                capabilities = descriptor.capabilities,
                providerAdvice = result.error.retryAdvice,
                phase = phase,
                callerHasIdempotencyKey = callerHasIdempotencyKey,
                definitelyUnprocessed = result.error.definitelyUnprocessed,
                httpStatus = result.error.httpStatus,
            ),
            requestMayHaveBeenAccepted = if (result.error.definitelyUnprocessed) false
            else result.error.requestMayHaveBeenAccepted || phase == RequestPhase.IN_FLIGHT,
            providerRequestId = safeProviderRequestId(result.error.providerRequestId, secrets),
            safeMessage = null,
            diagnosticCode = SensitiveDataRedactor.redactAndBound(result.error.diagnosticCode, secrets, MaxDiagnosticCodeLength),
        ))
    }

    private fun sanitizeJson(element: JsonElement, secrets: List<String>): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (_, value) -> sanitizeJson(value, secrets) })
        is JsonArray -> JsonArray(element.map { sanitizeJson(it, secrets) })
        is JsonPrimitive -> if (element.isString) {
            kotlinx.serialization.json.JsonPrimitive(SensitiveDataRedactor.redact(element.content, secrets).take(MaxMetadataValueLength))
        } else element
    }


    private fun Flow<ProviderGenerationEvent>.mapToClientEvents(
        operationId: OperationId,
        descriptor: ProviderDescriptor,
        config: ProviderRuntimeConfig,
        callerHasIdempotencyKey: Boolean,
    ): Flow<ImageGenerationEvent> = flow {
        val secrets = config.apiToken.use { listOf(it.concatToString()) }
        var lastStage = GenerationStage.VALIDATING_CONFIGURATION
        var phase = RequestPhase.BEFORE_DISPATCH
        var terminalEmitted = false
        val terminalAbort = ProviderCollectionComplete()

        try {
            collect { event ->
                when (event) {
                    is ProviderGenerationEvent.Stage -> {
                        val reliableBoundary = descriptor.capabilities.reliableDispatchBoundary
                        val generatingWithoutPersistedFence = reliableBoundary &&
                            event.value == GenerationStage.GENERATING &&
                            lastStage.ordinal < GenerationStage.UPLOADING.ordinal
                        val postStartStageWithoutGenerating = reliableBoundary &&
                            event.value.ordinal > GenerationStage.GENERATING.ordinal &&
                            phase != RequestPhase.IN_FLIGHT
                        val violatesOrder = event.value.ordinal <= lastStage.ordinal ||
                            generatingWithoutPersistedFence || postStartStageWithoutGenerating
                        if (violatesOrder) {
                            emit(malformedTerminal(operationId, "Provider emitted an invalid stage sequence", secrets))
                            terminalEmitted = true
                        } else {
                            lastStage = event.value
                            if (event.value == GenerationStage.GENERATING) phase = RequestPhase.IN_FLIGHT
                            emit(ImageGenerationEvent.Stage(operationId, event.value))
                        }
                    }

                    is ProviderGenerationEvent.Preview -> {
                        try {
                            emit(
                                ImageGenerationEvent.Preview(
                                    operationId,
                                    sanitizeImage(event.image, descriptor, secrets, config.allowInsecureHttp),
                                ),
                            )
                        } catch (_: ImageGenerationTransportException) {
                            emit(malformedTerminal(operationId, "Provider returned an invalid image", secrets))
                            terminalEmitted = true
                        }
                    }

                    is ProviderGenerationEvent.Completed -> {
                        val successWithoutDispatchFence = descriptor.capabilities.reliableDispatchBoundary &&
                            event.result is GenerationResult.Success && phase != RequestPhase.IN_FLIGHT
                        if (successWithoutDispatchFence) {
                            emit(malformedTerminal(operationId, "Provider completed without a dispatch stage", secrets))
                        } else {
                            try {
                                sanitizeResult(
                                    result = event.result,
                                    descriptor = descriptor,
                                    secrets = secrets,
                                    phase = phase,
                                    callerHasIdempotencyKey = callerHasIdempotencyKey,
                                    allowInsecureHttp = config.allowInsecureHttp,
                                )
                            } catch (_: ImageGenerationTransportException) {
                                emit(malformedTerminal(operationId, "Provider returned an invalid image", secrets))
                                terminalEmitted = true
                                null
                            }
                                ?.let { result -> emit(ImageGenerationEvent.Completed(operationId, result)) }
                        }
                        terminalEmitted = true
                    }
                }
                if (terminalEmitted) throw terminalAbort
            }
        } catch (error: ProviderCollectionComplete) {
            if (error !== terminalAbort) throw error
        }
        if (!terminalEmitted) {
            emit(malformedTerminal(operationId, "Provider completed without a terminal result", secrets))
        }
    }

    private fun malformedTerminal(
        operationId: OperationId,
        message: String,
        secrets: List<String>,
    ) = ImageGenerationEvent.Completed(
        operationId,
        GenerationResult.Failure(
            ImageGenerationError(
                category = ImageGenerationErrorCategory.MALFORMED_RESPONSE,
                retryAdvice = RetryAdvice.DO_NOT_RETRY,
                requestMayHaveBeenAccepted = false,
                safeMessage = SensitiveDataRedactor.redactAndBound(message, secrets, MaxMessageLength),
            ),
        ),
    )

    private fun sanitizeResult(
        result: GenerationResult,
        descriptor: ProviderDescriptor,
        secrets: List<String>,
        phase: RequestPhase,
        callerHasIdempotencyKey: Boolean,
        allowInsecureHttp: Boolean,
    ): GenerationResult = when (result) {
        is GenerationResult.Success -> result.copy(
            image = sanitizeImage(result.image, descriptor, secrets, allowInsecureHttp),
        )
        is GenerationResult.ProviderCancelled -> result.copy(message = null)

        is GenerationResult.Failure -> result.copy(
            error = result.error.copy(
                retryAdvice = ProviderFailureMapper.merge(
                    capabilities = descriptor.capabilities,
                    providerAdvice = result.error.retryAdvice,
                    phase = phase,
                    callerHasIdempotencyKey = callerHasIdempotencyKey,
                    definitelyUnprocessed = result.error.definitelyUnprocessed,
                    httpStatus = result.error.httpStatus,
                ),
                requestMayHaveBeenAccepted = if (result.error.definitelyUnprocessed) {
                    false
                } else {
                    result.error.requestMayHaveBeenAccepted || phase == RequestPhase.IN_FLIGHT
                },
                httpStatus = result.error.httpStatus?.takeIf { it in 100..599 },
                retryAfterMillis = result.error.retryAfterMillis?.takeIf { it in 0..MaxRetryAfterMillis },
                providerRequestId = safeProviderRequestId(result.error.providerRequestId, secrets),
                safeMessage = null,
                diagnosticCode = SensitiveDataRedactor.redactAndBound(result.error.diagnosticCode, secrets, MaxDiagnosticCodeLength),
            ),
        )
    }

    private fun sanitizeImage(
        image: GeneratedImage,
        descriptor: ProviderDescriptor,
        secrets: List<String>,
        allowInsecureHttp: Boolean,
    ): GeneratedImage {
        if (image.providerId != descriptor.id) {
            throw ImageGenerationTransportException("Provider image ownership mismatch")
        }
        val validated = ImageBytesValidator.validate(image.bytes, image.mimeType, ResponseLimits().imageBytes)
        return image.copy(
            bytes = validated.bytes,
            mimeType = validated.mimeType,
            model = SensitiveDataRedactor.redactAndBound(image.model, secrets, MaxModelLength),
            providerRequestId = safeProviderRequestId(image.providerRequestId, secrets),
            remoteUrl = safeRemoteUrl(image.remoteUrl, secrets, allowInsecureHttp),
            metadata = image.metadata
                .filterKeys { it in descriptor.safeMetadataKeys }
                .mapValues { (_, value) -> SensitiveDataRedactor.redact(value, secrets).take(MaxMetadataValueLength) },
        )
    }

    private fun safeProviderRequestId(value: String?, secrets: List<String>): String? {
        if (value == null || value.length > MaxProviderRequestIdLength || value.any { it.code !in 0x20..0x7e }) return null
        return value.takeIf { SensitiveDataRedactor.redact(it, secrets) == it }
    }

    private fun safeRemoteUrl(value: String?, secrets: List<String>, allowInsecureHttp: Boolean): String? {
        if (value == null || value.length > MaxRemoteUrlLength) return null
        try {
            BaseUrlValidator.validateRetainedRemoteUrl(value, allowInsecureHttp)
        } catch (_: ImageGenerationTransportException) {
            return null
        }
        return value.takeIf { SensitiveDataRedactor.redact(it, secrets) == it }
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val OPERATION_FINGERPRINT_BYTES = 6
        val SAFE_MODEL = Regex("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")
        val SAFE_MIME_TYPE = Regex("[a-z0-9][a-z0-9.+-]{0,63}/[a-z0-9][a-z0-9.+-]{0,63}")
        const val MaxProviderRequestIdLength = 256
        const val MaxMessageLength = 1_024
        const val MaxDiagnosticCodeLength = 256
        const val MaxMetadataValueLength = 512
        const val MaxModelLength = 256
        const val MaxRemoteUrlLength = 2_048
        const val MaxRetryAfterMillis = 7L * 24 * 60 * 60 * 1_000
        const val MAX_MODEL_ID_LENGTH = 256
        const val MAX_MODEL_COUNT = 1_000
    }

    private class ProviderCollectionComplete : CancellationException("Provider collection complete")
}

private val MONOTONIC_ORIGIN = TimeSource.Monotonic.markNow()
