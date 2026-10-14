package com.alpha.showcase.common.ai

import com.alpha.ai.imagegeneration.*
import com.alpha.ai.imagegeneration.provider.registerBuiltIns
import com.alpha.facedetection.FaceInspectionResult
import com.alpha.facedetection.FaceInspector
import com.alpha.showcase.common.storage.ObjectStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AiUploadConsentTest {
    private val profile = AiProfile("vision", name = "Vision", providerId = "openai-vision", model = "vision",
        baseUrl = "https://API.Example.test/v1", encryptedToken = "test")

    @Test fun notRequiredGrantsWithoutReadingTheStore() = runTest {
        val store = ConsentStore()
        val consent = AiUploadConsent({ false }, store)
        assertTrue(consent.isGranted(profile))
        assertFalse(consent.needsConsent(profile, null))
        assertEquals(0, store.reads)
    }

    @Test fun requiredHostIsDeniedUntilGranted() = runTest {
        val consent = AiUploadConsent({ true }, ConsentStore())
        assertFalse(consent.isGranted(profile))
        consent.grant(profile)
        assertTrue(consent.isGranted(profile))
        assertEquals(setOf("api.example.test"), consent.hosts.value)
    }

    @Test fun unloadedHostsCountAsNotConsented() {
        val consent = AiUploadConsent({ true }, ConsentStore(AiUploadConsentRecord(setOf("api.example.test"))))
        assertTrue(consent.needsConsent(profile, consent.hosts.value))
    }

    @Test fun grantPersistsAcrossInstances() = runTest {
        val store = ConsentStore()
        AiUploadConsent({ true }, store).grant(profile)
        val restarted = AiUploadConsent({ true }, store)
        assertTrue(restarted.isGranted(profile))
        assertEquals(setOf("api.example.test"), restarted.hosts.value)
    }

    @Test fun consentCoversTheHostRegardlessOfCasePathAndPort() = runTest {
        val consent = AiUploadConsent({ true }, ConsentStore())
        consent.grant(profile)
        assertTrue(consent.isGranted(profile.copy(baseUrl = "https://api.example.TEST:8443/other")))
    }

    @Test fun anotherHostNeedsItsOwnConsent() = runTest {
        val consent = AiUploadConsent({ true }, ConsentStore())
        consent.grant(profile)
        assertFalse(consent.isGranted(profile.copy(baseUrl = "https://vision.other.test/v1")))
    }

    @Test fun summaryRequestDoesNotUploadOrReadCredentialsWithoutConsent() = runTest {
        val f = Fixture(this, AiUploadConsent({ true }, ConsentStore()))
        f.engine.summaries.request(f.request, force = true)
        advanceUntilIdle()
        val state = f.engine.summaries.observe(f.request).first()
        assertEquals(0, f.client.calls)
        assertEquals(0, f.decryptions)
        assertFalse(state.generating)
        assertNull(state.content)
    }

    @Test fun summaryRequestUploadsOnceConsentIsGranted() = runTest {
        val consent = AiUploadConsent({ true }, ConsentStore())
        val f = Fixture(this, consent)
        consent.grant(f.profile)
        f.engine.summaries.request(f.request)
        advanceUntilIdle()
        assertEquals(1, f.client.calls)
        assertEquals("a landscape", f.engine.summaries.observe(f.request).first().content?.narration)
    }

    private class ConsentStore(var value: AiUploadConsentRecord? = null) : ObjectStore<AiUploadConsentRecord> {
        var reads = 0
        override suspend fun get(): AiUploadConsentRecord? { reads++; return value }
        override suspend fun set(value: AiUploadConsentRecord) { this.value = value }
        override suspend fun delete() { value = null }
    }

    private class Fixture(scope: CoroutineScope, consent: AiUploadConsent) {
        val client = Client()
        val profile = AiProfile("vision", name = "Vision", providerId = "openai-vision", model = "vision",
            baseUrl = "https://example.test/v1", encryptedToken = "test")
        var decryptions = 0
        val engine = AiEngine(LibraryStore(), UnusedFiles, client, scope, { it }, { decryptions++; it },
            faceInspectorFactory = { FaceInspector { FaceInspectionResult.NO_FACE } },
            summaryRepository = TestSummaryRepository(), uploadConsent = consent)
        val request = AiSummaryRequest("photo", EncodedAiImage(byteArrayOf(1, 2, 3)), profile, "en", identity = testImageIdentity())
    }

    private class Client : AiModelClient by AiModel.builder().registerBuiltIns().build() {
        var calls = 0
        override fun understandImage(request: ImageUnderstandingRequest, config: ProviderRuntimeConfig) = flow {
            calls++
            emit(ImageUnderstandingEvent.Completed(request.operationId, ImageUnderstandingResult.Success(ImageUnderstandingOutput(
                buildJsonObject { put("summary", "landscape"); put("narration", "a landscape"); put("tags", buildJsonArray { add("nature") }) },
                config.providerId, config.model))))
        }
    }

    private class LibraryStore : ObjectStore<AiLibrary> {
        var value: AiLibrary? = AiLibrary(facePrivacyEnabled = false)
        override suspend fun get() = value
        override suspend fun set(value: AiLibrary) { this.value = value }
        override suspend fun delete() { value = null }
    }

    private object UnusedFiles : AiFiles {
        override suspend fun write(name: String, bytes: ByteArray): Unit = error("unused")
        override suspend fun read(name: String): ByteArray = error("unused")
        override suspend fun delete(name: String) = Unit
        override fun imageModel(name: String): Any = error("unused")
    }
}
