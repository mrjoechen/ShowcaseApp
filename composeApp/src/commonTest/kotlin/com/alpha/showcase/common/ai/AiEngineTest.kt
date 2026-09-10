package com.alpha.showcase.common.ai

import com.alpha.ai.imagegeneration.*
import com.alpha.showcase.common.storage.ObjectStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import okio.buffer

@OptIn(ExperimentalCoroutinesApi::class)
class AiEngineTest {
    @Test fun originalIsArchivedSeparatelyAndSurvivesRetryWithoutBeingUploaded() = runTest {
        val fixture = Fixture(this)
        fixture.saveProfile()
        val upload = byteArrayOf(1, 2)
        val original = byteArrayOf(9, 8, 7)
        fixture.client.generate = { request ->
            assertContentEquals(upload, request.source.openSource().buffer().use { it.readByteArray() })
            flowOf(fixture.client.success(request))
        }
        val id = fixture.engine.enqueue(EncodedAiImage(upload), fixture.profileId, "ghibli", AiOriginalImage(original, "png"), "Original.png")
        advanceUntilIdle()
        val task = fixture.engine.library.value.tasks.single()
        assertContentEquals(original, fixture.files.read(checkNotNull(task.originalFile)))
        assertEquals("Original.png", task.originalName)
        val retryId = fixture.engine.retry(id, fixture.profileId, false)
        advanceUntilIdle()
        val retried = fixture.engine.library.value.tasks.first { it.id == retryId }
        assertNotEquals(task.originalFile, retried.originalFile)
        assertContentEquals(original, fixture.files.read(checkNotNull(retried.originalFile)))
        fixture.engine.deleteTask(id)
        assertFalse(task.originalFile in fixture.files.data)
        assertContentEquals(original, fixture.files.read(checkNotNull(retried.originalFile)))
    }

    @Test fun failedArchiveCommitCleansOriginalAndUploadWithoutDispatching() = runTest {
        val fixture = Fixture(this)
        fixture.saveProfile()
        fixture.store.failWrites = true
        assertFailsWith<IllegalStateException> {
            fixture.engine.enqueue(EncodedAiImage(byteArrayOf(1)), fixture.profileId, "ghibli", AiOriginalImage(byteArrayOf(2), "png"))
        }
        advanceUntilIdle()
        assertTrue(fixture.files.data.isEmpty())
        assertEquals(0, fixture.client.calls)
    }
    @Test fun browserCannotInitializePersistOrDispatch() = runTest {
        val fixture = Fixture(this, enabled = false)
        assertFailsWith<IllegalStateException> { fixture.engine.initialize() }
        assertEquals(0, fixture.store.writes)
        assertEquals(0, fixture.client.calls)
    }

    @Test fun successfulTaskPersistsItsSourceAndResult() = runTest {
        val fixture = Fixture(this)
        fixture.saveProfile()
        val id = fixture.enqueue()
        runCurrent()
        val task = fixture.engine.library.value.tasks.single()
        assertEquals(id, task.id)
        assertEquals(AiTaskStatus.SUCCEEDED, task.status)
        assertContentEquals(byteArrayOf(1, 2), fixture.files.data[task.sourceFile])
        assertContentEquals(byteArrayOf(3, 4), fixture.files.data[task.resultFile])
        assertEquals(task, fixture.store.value!!.tasks.single())
    }

    @Test fun closingTheUiCannotCancelAppOwnedWork() = runTest {
        val fixture = Fixture(this)
        val finish = CompletableDeferred<Unit>()
        fixture.client.generate = { flow { finish.await(); emit(fixture.client.success(it)) } }
        fixture.saveProfile()
        fixture.enqueue()
        runCurrent()
        assertEquals(AiTaskStatus.RUNNING, fixture.engine.library.value.tasks.single().status)
        // The only owner is the app scope; there is no dialog job to keep alive.
        finish.complete(Unit)
        runCurrent()
        assertEquals(AiTaskStatus.SUCCEEDED, fixture.engine.library.value.tasks.single().status)
    }

    @Test fun ambiguousErrorsNeverRetryAndRequireExplicitConfirmation() = runTest {
        val fixture = Fixture(this)
        fixture.client.generate = { flowOf(ImageGenerationEvent.Completed(it.operationId, GenerationResult.Failure(
            ImageGenerationError(ImageGenerationErrorCategory.TIMEOUT, RetryAdvice.AMBIGUOUS, requestMayHaveBeenAccepted = true)))) }
        fixture.saveProfile()
        val id = fixture.enqueue()
        advanceUntilIdle()
        assertEquals(1, fixture.client.calls)
        assertEquals(AiTaskStatus.RESULT_UNKNOWN, fixture.engine.library.value.tasks.single().status)
        assertFailsWith<IllegalArgumentException> { fixture.engine.retry(id, fixture.profileId, false) }
        fixture.engine.retry(id, fixture.profileId, true)
        advanceUntilIdle()
        assertEquals(2, fixture.client.calls)
        assertEquals(2, fixture.engine.library.value.tasks.size)
    }

    @Test fun provenUnprocessedFailuresRetryWithBoundedAttempts() = runTest {
        val fixture = Fixture(this)
        fixture.client.generate = { flowOf(ImageGenerationEvent.Completed(it.operationId, GenerationResult.Failure(
            ImageGenerationError(ImageGenerationErrorCategory.NETWORK, RetryAdvice.SAFE_TO_RETRY, requestMayHaveBeenAccepted = false)))) }
        fixture.saveProfile()
        fixture.enqueue()
        advanceUntilIdle()
        assertEquals(3, fixture.client.calls)
        assertEquals(AiTaskStatus.FAILED, fixture.engine.library.value.tasks.single().status)
    }

    @Test fun cancellationPreventsLateResultsAndQueuedDispatch() = runTest {
        val fixture = Fixture(this)
        fixture.client.generate = { flow { awaitCancellation() } }
        fixture.saveProfile()
        val running = fixture.enqueue()
        val queued = fixture.enqueue()
        runCurrent()
        fixture.engine.cancel(queued)
        fixture.engine.cancel(running)
        runCurrent()
        assertEquals(1, fixture.client.calls)
        assertTrue(fixture.engine.library.value.tasks.all { it.status == AiTaskStatus.CANCELLED && it.resultFile == null })
    }

    @Test fun restartMarksAcceptedWorkUnknownButResumesQueuedWork() = runTest {
        val fixture = Fixture(this)
        fixture.saveProfile()
        val profile = fixture.engine.library.value.activeProfiles.single()
        fixture.store.value = fixture.store.value!!.copy(tasks = listOf(
            task("interrupted", profile).copy(status = AiTaskStatus.RUNNING), task("queued", profile),
        ))
        fixture.files.data["queued-source.jpg"] = byteArrayOf(1)
        val restarted = fixture.newEngine()
        restarted.initialize()
        advanceUntilIdle()
        assertEquals(AiTaskStatus.RESULT_UNKNOWN, restarted.library.value.tasks.first().status)
        assertEquals(AiTaskStatus.SUCCEEDED, restarted.library.value.tasks.last().status)
        assertEquals(1, fixture.client.calls)
    }

    @Test fun editingOrArchivingProfileKeepsFrozenQueuedRevision() = runTest {
        val fixture = Fixture(this)
        fixture.saveProfile()
        val profile = fixture.engine.library.value.activeProfiles.single()
        fixture.enqueue()
        fixture.engine.saveProfile(profile.copy(model = "replacement-model"), "new-secret")
        fixture.engine.archiveProfile(profile.id)
        advanceUntilIdle()
        assertEquals("model-one", fixture.client.models.single())
        assertEquals(AiTaskStatus.SUCCEEDED, fixture.engine.library.value.tasks.single().status)
        assertTrue(fixture.engine.library.value.activeProfiles.isEmpty())
        assertTrue(fixture.store.value.toString().contains("secret").not())
    }

    @Test fun changedProviderOriginCannotReuseSavedToken() = runTest {
        val fixture = Fixture(this)
        fixture.saveProfile()
        val profile = fixture.engine.library.value.activeProfiles.single()
        assertFailsWith<IllegalArgumentException> { fixture.engine.saveProfile(profile.copy(baseUrl = "https://other.example/v1"), "") }
        assertEquals(1, fixture.engine.library.value.profiles.size)
    }

    @Test fun persistenceFailureDoesNotDispatchAndCleansSource() = runTest {
        val fixture = Fixture(this)
        fixture.saveProfile()
        fixture.store.failWrites = true
        assertFailsWith<IllegalStateException> { fixture.enqueue() }
        advanceUntilIdle()
        assertEquals(0, fixture.client.calls)
        assertTrue(fixture.files.data.isEmpty())
    }

    @Test fun profileCapabilityChangeClearsTheOppositeSelection() = runTest {
        val fixture = Fixture(this)
        fixture.saveProfile()
        val generation = fixture.engine.library.value.activeProfiles.single()
        fixture.engine.saveProfile(generation.copy(providerId = "openai-vision"), "vision-token")
        assertNull(fixture.engine.library.value.generationProfileId)
        assertEquals(generation.id, fixture.engine.library.value.understandingProfileId)
        val vision = fixture.engine.library.value.activeProfiles.single()
        fixture.engine.saveProfile(vision.copy(providerId = "openai"), "image-token")
        assertNull(fixture.engine.library.value.understandingProfileId)
        assertEquals(vision.id, fixture.engine.library.value.generationProfileId)
    }

    @Test fun lateCancelCannotOverwriteATerminalResult() = runTest {
        val fixture = Fixture(this)
        fixture.saveProfile()
        val id = fixture.enqueue()
        advanceUntilIdle()
        val result = fixture.engine.library.value.tasks.single()
        fixture.engine.cancel(id)
        assertEquals(result, fixture.engine.library.value.tasks.single())
        assertEquals(AiTaskStatus.SUCCEEDED, result.status)
    }

    private fun task(id: String, profile: AiProfile) = AiTask(id, profile.id, profile.revision, "ghibli", "frozen prompt", 1,
        "$id-source.jpg", "image/jpeg")

    private class Fixture(val scope: CoroutineScope, val enabled: Boolean = true) {
        val store = MemoryStore()
        val files = MemoryFiles()
        val client = FakeClient()
        var sequence = 0
        val engine = newEngine()
        val profileId get() = engine.library.value.activeProfiles.single().id
        fun newEngine() = AiEngine(store, files, client, scope, encrypt = { "encrypted:$it" }, decrypt = { it.removePrefix("encrypted:") },
            enabled = enabled, newId = { "id-${++sequence}" }, now = { 10 })
        suspend fun saveProfile() = engine.saveProfile(AiProfile("", name = "Test", providerId = "openai", model = "model-one",
            baseUrl = "https://api.example/v1", encryptedToken = ""), "test-secret")
        suspend fun enqueue() = engine.enqueue(EncodedAiImage(byteArrayOf(1, 2)), profileId, "ghibli")
    }

    private class MemoryStore : ObjectStore<AiLibrary> {
        var value: AiLibrary? = null
        var writes = 0
        var failWrites = false
        override suspend fun get() = value
        override suspend fun set(value: AiLibrary) { check(!failWrites); writes++; this.value = value }
        override suspend fun delete() { value = null }
    }
    private class MemoryFiles : AiFiles {
        val data = mutableMapOf<String, ByteArray>()
        override suspend fun write(name: String, bytes: ByteArray) { data[name] = bytes }
        override suspend fun read(name: String) = data[name] ?: error("Missing source")
        override suspend fun delete(name: String) { data.remove(name) }
        override fun imageModel(name: String): Any = name
    }
    private class FakeClient : AiModelClient {
        var calls = 0
        val models = mutableListOf<String?>()
        var generate: (GenerateImageRequest) -> Flow<ImageGenerationEvent> = { flowOf(success(it)) }
        fun success(request: GenerateImageRequest) = ImageGenerationEvent.Completed(request.operationId,
            GenerationResult.Success(GeneratedImage(byteArrayOf(3, 4), "image/jpeg", providerId = ProviderId("openai"), model = "model-one")))
        override fun providerDescriptor(providerId: ProviderId) = ProviderDescriptor(providerId, "Test", setOf("openai-images-v1"), ProviderCapabilities(setOf("image/jpeg"), 1000))
        override fun generateImage(request: GenerateImageRequest, config: ProviderRuntimeConfig): Flow<ImageGenerationEvent> {
            calls++; models += config.model
            return generate(request)
        }
        override fun understandImage(request: ImageUnderstandingRequest, config: ProviderRuntimeConfig): Flow<ImageUnderstandingEvent> = emptyFlow()
    }
}
