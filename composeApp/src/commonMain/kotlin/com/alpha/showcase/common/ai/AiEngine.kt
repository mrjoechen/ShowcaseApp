package com.alpha.showcase.common.ai

import com.alpha.ai.imagegeneration.*
import com.alpha.facedetection.FaceInspector
import com.alpha.facedetection.createFaceInspector
import com.alpha.showcase.common.storage.ObjectStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Durable client-side queue. Only failures proven not accepted are retried automatically. */
internal class AiEngine(
    private val store: ObjectStore<AiLibrary>,
    internal val files: AiFiles,
    internal val client: AiModelClient,
    internal val scope: CoroutineScope,
    private val encrypt: suspend (String) -> String,
    private val decrypt: suspend (String) -> String,
    private val enabled: Boolean = true,
    private val workAvailable: () -> Unit = {},
    private val newId: () -> String = { Uuid.random().toString() },
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    faceInspectorFactory: () -> FaceInspector = ::createFaceInspector,
) {
    val summaries = AiSummaryManager(this, faceInspectorFactory)
    private val mutex = Mutex()
    private val execution = Semaphore(1)
    private var initialized = false
    private val jobs = mutableMapOf<String, Job>()
    private val mutableLibrary = MutableStateFlow(AiLibrary())
    val library: StateFlow<AiLibrary> = mutableLibrary.asStateFlow()

    suspend fun initialize() {
        check(enabled) { "AI is disabled on this platform" }
        mutex.withLock {
            if (initialized) return
            val saved = store.get() ?: AiLibrary()
            val recovered = saved.copy(tasks = saved.tasks.map { it.recordChange(it.copy(status = it.status.afterRestart()), now()) })
            store.set(recovered)
            mutableLibrary.value = recovered
            initialized = true
            summaries.refreshPrivacy()
            recovered.tasks.filter { it.status == AiTaskStatus.QUEUED }.forEach { schedule(it.id) }
        }
    }

    private suspend fun update(transform: (AiLibrary) -> AiLibrary) = mutex.withLock {
        val next = transform(mutableLibrary.value)
        store.set(next)
        mutableLibrary.value = next
        summaries.refreshPrivacy()
    }

    internal suspend fun saveProfile(draft: AiProfile, replacementToken: String) {
        initialize()
        require(draft.name.isNotBlank() && draft.model.isNotBlank())
        require(isValidAiBaseUrl(draft.baseUrl, draft.allowInsecureHttp))
        require(client.providerDescriptor(ProviderId(draft.providerId)) != null)
        val old = library.value.activeProfiles.firstOrNull { it.id == draft.id }
        val encrypted = if (replacementToken.isNotBlank()) encrypt(replacementToken.trim()) else {
            require(old != null && canReuseAiCredential(old.providerId, old.baseUrl, draft.providerId, draft.baseUrl))
            old.encryptedToken
        }
        require(encrypted.isNotBlank())
        val profile = draft.copy(
            id = old?.id ?: newId(), revision = (old?.revision ?: 0) + 1,
            encryptedToken = encrypted, archived = false,
            name = draft.name.trim(), model = draft.model.trim(), baseUrl = draft.baseUrl.trim().trimEnd('/'),
        )
        update { state -> state.copy(
            profiles = state.profiles.map { if (it.id == profile.id) it.copy(archived = true) else it } + profile,
            generationProfileId = if (aiProviderCapability(profile.providerId) == AiCapability.IMAGE_TO_IMAGE)
                profile.id else state.generationProfileId.takeUnless { it == profile.id },
            understandingProfileId = if (aiProviderCapability(profile.providerId) == AiCapability.IMAGE_UNDERSTANDING)
                profile.id else state.understandingProfileId.takeUnless { it == profile.id },
        ) }
    }

    suspend fun archiveProfile(id: String) {
        initialize()
        update { it.copy(profiles = it.profiles.map { profile -> if (profile.id == id) profile.copy(archived = true) else profile },
            generationProfileId = it.generationProfileId.takeUnless { selected -> selected == id },
            understandingProfileId = it.understandingProfileId.takeUnless { selected -> selected == id }) }
    }

    suspend fun selectProfile(id: String) {
        initialize()
        val profile = library.value.activeProfiles.first { it.id == id }
        update {
            if (aiProviderCapability(profile.providerId) == AiCapability.IMAGE_TO_IMAGE) it.copy(generationProfileId = id)
            else it.copy(understandingProfileId = id)
        }
    }

    suspend fun selectStyle(key: String) {
        initialize()
        require(LocalAiStyleCatalog.styles().any { it.key == key })
        update { it.copy(styleKey = key) }
    }

    suspend fun setFacePrivacyEnabled(enabled: Boolean) {
        initialize()
        update { it.copy(facePrivacyEnabled = enabled) }
    }

    suspend fun <T> withCredential(profile: AiProfile, block: suspend (SecretValue) -> T): T {
        check(enabled)
        val token = SecretValue(decrypt(profile.encryptedToken))
        return try { block(token) } finally { token.close() }
    }

    suspend fun listModels(profile: AiProfile, tokenInput: String): ProviderModelCatalogResult =
        withDraftCredential(profile, tokenInput) { token -> client.listModels(ProviderModelCatalogRequest(
            providerId = ProviderId(profile.providerId), capability = aiProviderCapability(profile.providerId),
            baseUrl = profile.baseUrl, protocolVersion = aiProviderProtocol(profile.providerId), apiToken = token,
            allowInsecureHttp = profile.allowInsecureHttp,
        )) }

    suspend fun testConnection(profile: AiProfile, tokenInput: String): ProviderConnectionTestResult =
        withDraftCredential(profile, tokenInput) { token -> client.testConnection(aiProviderCapability(profile.providerId), profile.runtimeConfig(token)) }

    private suspend fun <T> withDraftCredential(profile: AiProfile, input: String, block: suspend (SecretValue) -> T): T {
        initialize()
        require(isValidAiBaseUrl(profile.baseUrl, profile.allowInsecureHttp))
        if (input.isNotBlank()) {
            val token = SecretValue(input.trim())
            return try { block(token) } finally { token.close() }
        }
        val stored = library.value.activeProfiles.first { it.id == profile.id }
        require(canReuseAiCredential(stored.providerId, stored.baseUrl, profile.providerId, profile.baseUrl))
        return withCredential(stored, block)
    }

    suspend fun enqueue(image: EncodedAiImage, profileId: String, styleKey: String, original: AiOriginalImage? = null, originalName: String? = null): String {
        initialize()
        val profile = library.value.activeProfiles.first { it.id == profileId }
        require(aiProviderCapability(profile.providerId) == AiCapability.IMAGE_TO_IMAGE)
        val style = LocalAiStyleCatalog.styles().first { it.key == styleKey }
        val id = newId()
        val source = "$id-source.jpg"
        val originalFile = original?.let { "$id-original.${it.extension}" }
        files.write(source, image.bytes)
        val task = AiTask(id, profile.id, profile.revision, style.key, style.prompt, now(), source, image.mimeType,
            originalFile = originalFile, originalName = originalName)
        try {
            if (original != null && originalFile != null) files.write(originalFile, original.bytes)
            update { it.copy(tasks = listOf(task) + it.tasks, generationProfileId = profileId, styleKey = styleKey) }
        } catch (e: Exception) {
            withContext(NonCancellable) {
                files.delete(source)
                originalFile?.let { files.delete(it) }
            }
            throw e
        }
        schedule(id)
        // Scheduling failure must not report the already-persisted task as an enqueue failure.
        runCatching { workAvailable() }
        return id
    }

    private fun schedule(id: String) {
        if (jobs[id]?.isActive == true) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try { execution.withPermit { execute(id) } }
            finally { jobs.remove(id) }
        }
        jobs[id] = job
        job.start()
    }

    private suspend fun changeTask(id: String, transform: (AiTask) -> AiTask) = update { state ->
        state.copy(tasks = state.tasks.map { task ->
            if (task.id == id && !task.status.terminal) task.recordChange(transform(task), now()) else task
        })
    }

    private suspend fun execute(id: String) {
        var submitted = false
        var uncommittedOutput: String? = null
        try {
            while (true) {
                val task = library.value.tasks.firstOrNull { it.id == id } ?: return
                if (task.status.terminal) return
                val profile = library.value.profile(task) ?: run {
                    changeTask(id) { it.copy(status = AiTaskStatus.NEEDS_USER_ACTION, errorCategory = "CONFIGURATION") }
                    return
                }
                val bytes = files.read(task.sourceFile)
                var terminal: GenerationResult? = null
                withCredential(profile) { token ->
                    changeTask(id) { it.copy(status = AiTaskStatus.RUNNING, attempt = it.attempt + 1, stage = "PREPARING_INPUT", errorCategory = null) }
                    submitted = true
                    client.generateImage(GenerateImageRequest(OperationId(id), ByteArrayImageSource(bytes, task.sourceMimeType), task.prompt), profile.runtimeConfig(token))
                        .collect { event -> when (event) {
                            is ImageGenerationEvent.Stage -> changeTask(id) { it.copy(stage = event.value.name) }
                            is ImageGenerationEvent.Completed -> terminal = event.result
                            is ImageGenerationEvent.Preview -> Unit
                        } }
                }
                when (val result = terminal) {
                    is GenerationResult.Success -> {
                        changeTask(id) { it.copy(stage = "PERSISTING_RESULT") }
                        val extension = when (result.image.mimeType) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
                        val output = "$id-result.$extension"
                        uncommittedOutput = output
                        files.write(output, result.image.bytes)
                        currentCoroutineContext().ensureActive()
                        changeTask(id) { it.copy(status = AiTaskStatus.SUCCEEDED, resultFile = output, errorCategory = null) }
                        if (library.value.tasks.any { it.id == id && it.resultFile == output }) uncommittedOutput = null
                        return
                    }
                    is GenerationResult.Failure -> {
                        val attempt = library.value.tasks.first { it.id == id }.attempt
                        val error = result.error
                        if (shouldAutomaticallyRetry(error, attempt)) {
                            changeTask(id) { it.copy(status = AiTaskStatus.RETRY_WAIT, errorCategory = error.category.name) }
                            delay((error.retryAfterMillis ?: attempt * 2000L).coerceIn(1000, 60_000))
                        } else {
                            changeTask(id) { it.copy(status = failureStatus(error), errorCategory = error.category.name) }
                            return
                        }
                    }
                    is GenerationResult.ProviderCancelled -> {
                        changeTask(id) { it.copy(status = AiTaskStatus.CANCELLED) }
                        return
                    }
                    null -> {
                        changeTask(id) { it.copy(status = AiTaskStatus.RESULT_UNKNOWN, errorCategory = "MALFORMED_RESPONSE") }
                        return
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { changeTask(id) { it.copy(status = if (submitted) AiTaskStatus.RESULT_UNKNOWN else AiTaskStatus.FAILED) } }
            throw cancelled
        } catch (_: Exception) {
            changeTask(id) { it.copy(status = if (submitted) AiTaskStatus.RESULT_UNKNOWN else AiTaskStatus.NEEDS_USER_ACTION,
                errorCategory = if (submitted) "NETWORK" else "LOCAL_STORAGE") }
        } finally {
            uncommittedOutput?.let { output -> withContext(NonCancellable) { files.delete(output) } }
        }
    }

    suspend fun cancel(id: String) {
        initialize()
        var cancelled = false
        update { it.copy(tasks = it.tasks.map { task ->
            if (task.id == id && !task.status.terminal) {
                cancelled = true
                task.recordChange(task.copy(status = AiTaskStatus.CANCELLED), now())
            } else task
        }) }
        if (cancelled) jobs[id]?.cancel()
    }

    suspend fun retry(id: String, profileId: String, confirmedAmbiguousCost: Boolean): String {
        initialize()
        val task = library.value.tasks.first { it.id == id }
        require(task.status.terminal)
        require(task.status != AiTaskStatus.RESULT_UNKNOWN || confirmedAmbiguousCost)
        val original = task.originalFile?.let {
            try { AiOriginalImage(files.read(it), it.substringAfterLast('.')) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        }
        return enqueue(EncodedAiImage(files.read(task.sourceFile), task.sourceMimeType), profileId, task.styleKey, original, task.originalName)
    }

    suspend fun deleteTask(id: String) {
        initialize()
        val task = library.value.tasks.firstOrNull { it.id == id } ?: return
        require(task.status.terminal)
        jobs[id]?.cancelAndJoin()
        update { it.copy(tasks = it.tasks.filterNot { task -> task.id == id }) }
        withContext(NonCancellable) {
            files.delete(task.sourceFile)
            task.resultFile?.let { files.delete(it) }
            task.originalFile?.let { files.delete(it) }
        }
    }

    internal suspend fun saveSummary(key: String, content: AiSummaryContent) {
        update { it.copy(summaries = (it.summaries + (key to content)).entries.toList().takeLast(500)
            .associate { entry -> entry.key to entry.value }) }
    }
}
