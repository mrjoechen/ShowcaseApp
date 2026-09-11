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
class AiFacePrivacyTest {
    @Test fun facesBlockEvenForcedRequestsWithoutReadingCredentials() = runTest {
        val f = Fixture(this, FaceInspectionResult.FACE_DETECTED)
        f.engine.setFacePrivacyEnabled(true)
        f.engine.summaries.request(f.request, force = true)
        advanceUntilIdle()
        val state = f.engine.summaries.observe(f.request).first()
        assertTrue(state.facePrivacyBlocked)
        assertFalse(state.generating)
        assertNull(state.content)
        assertEquals(0, f.client.calls)
        assertEquals(0, f.decryptions)
    }

    @Test fun onlyExplicitNoFacePermitsUpload() = runTest {
        val f = Fixture(this, FaceInspectionResult.NO_FACE)
        f.engine.setFacePrivacyEnabled(true)
        f.engine.summaries.request(f.request)
        advanceUntilIdle()
        assertEquals(1, f.client.calls)
        assertEquals("a landscape", f.engine.summaries.observe(f.request).first().content?.narration)
        assertContentEquals(f.request.image.bytes, f.inspected)
    }

    @Test fun indeterminateAndExceptionsNeverUpload() = runTest {
        for (throws in listOf(false, true)) {
            val f = Fixture(this, FaceInspectionResult.INDETERMINATE)
            f.inspectionFails = throws
            f.engine.setFacePrivacyEnabled(true)
            f.engine.summaries.request(f.request)
            advanceUntilIdle()
            assertTrue(f.engine.summaries.observe(f.request).first().facePrivacyUnavailable)
            assertEquals(0, f.client.calls)
        }
    }

    @Test fun disabledPrivacyDoesNotInitializeDetectorAndPersistsAcrossRestart() = runTest {
        val f = Fixture(this, FaceInspectionResult.FACE_DETECTED)
        f.engine.summaries.request(f.request)
        advanceUntilIdle()
        assertEquals(1, f.client.calls)
        assertEquals(0, f.detectorInitializations)
        f.engine.setFacePrivacyEnabled(true)
        val restarted = AiEngine(f.store, UnusedFiles, f.client, this, { it }, { it })
        restarted.initialize()
        assertTrue(restarted.library.value.facePrivacyEnabled)
        f.engine.setFacePrivacyEnabled(false)
        assertFalse(f.store.value!!.facePrivacyEnabled)
    }

    @Test fun cachedSummaryIsHiddenImmediatelyWhenPrivacyTurnsOn() = runTest {
        val f = Fixture(this, FaceInspectionResult.FACE_DETECTED)
        f.engine.summaries.request(f.request)
        advanceUntilIdle()
        assertNotNull(f.engine.summaries.observe(f.request).first().content)
        f.engine.setFacePrivacyEnabled(true)
        assertNull(f.engine.summaries.observe(f.request).first().content)
        f.engine.summaries.request(f.request)
        advanceUntilIdle()
        assertTrue(f.engine.summaries.observe(f.request).first().facePrivacyBlocked)
        assertEquals(1, f.client.calls)
        f.engine.setFacePrivacyEnabled(false)
        f.engine.summaries.request(f.request)
        advanceUntilIdle()
        assertNotNull(f.engine.summaries.observe(f.request).first().content)
        assertEquals(1, f.client.calls)
    }

    @Test fun enablingDuringCredentialReadPreventsDispatch() = runTest {
        val f = Fixture(this, FaceInspectionResult.FACE_DETECTED)
        val credentialReady = CompletableDeferred<Unit>()
        f.beforeDecrypt = { credentialReady.await() }
        f.engine.summaries.request(f.request)
        runCurrent()
        assertEquals(1, f.decryptions)
        f.engine.setFacePrivacyEnabled(true)
        credentialReady.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, f.client.calls)
        assertTrue(f.engine.summaries.observe(f.request).first().facePrivacyBlocked)
    }

    @Test fun unshareableFrameIsIndeterminateWithoutInitializingDetector() = runTest {
        val f = Fixture(this, FaceInspectionResult.NO_FACE)
        f.engine.setFacePrivacyEnabled(true)
        val animated = AiSummaryRequest("animated", f.request.image, f.profile, "en", privacyEligible = false)
        f.engine.summaries.request(animated)
        advanceUntilIdle()
        assertTrue(f.engine.summaries.observe(animated).first().facePrivacyUnavailable)
        assertEquals(0, f.detectorInitializations)
        assertEquals(0, f.client.calls)
    }

    @Test fun facesAreDetectedWithoutAConfiguredProvider() = runTest {
        val f = Fixture(this, FaceInspectionResult.FACE_DETECTED)
        f.engine.setFacePrivacyEnabled(true)
        val request = AiSummaryRequest("no-profile", f.request.image, null, "en")
        f.engine.summaries.request(request)
        advanceUntilIdle()
        assertTrue(f.engine.summaries.observe(request).first().facePrivacyBlocked)
        assertEquals(0, f.client.calls)
    }

    @Test fun checkingIsSilentAndDisablingDuringInspectionResumesUpload() = runTest {
        val f = Fixture(this, FaceInspectionResult.FACE_DETECTED)
        val finish = CompletableDeferred<Unit>()
        f.beforeInspect = { finish.await() }
        f.engine.setFacePrivacyEnabled(true)
        f.engine.summaries.request(f.request)
        runCurrent()
        assertTrue(f.engine.summaries.observe(f.request).value.facePrivacyPending)
        assertFalse(f.engine.summaries.observe(f.request).value.generating)
        f.engine.setFacePrivacyEnabled(false)
        finish.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, f.client.calls)
        assertNotNull(f.engine.summaries.observe(f.request).value.content)
    }

    @Test fun enablingWhileQueuedBlocksSecondUploadAndDiscardsLateFirstResult() = runTest {
        val f = Fixture(this, FaceInspectionResult.FACE_DETECTED)
        val finish = CompletableDeferred<Unit>()
        f.client.beforeResult = { finish.await() }
        f.engine.summaries.request(f.request)
        runCurrent()
        assertEquals(1, f.client.calls)
        val queued = AiSummaryRequest("second-photo", EncodedAiImage(byteArrayOf(4)), f.profile, "en")
        f.engine.summaries.request(queued)
        runCurrent()
        f.engine.setFacePrivacyEnabled(true)
        assertNull(f.engine.summaries.observe(f.request).value.content)
        finish.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, f.client.calls)
        assertTrue(f.engine.summaries.observe(f.request).value.facePrivacyBlocked)
        assertTrue(f.engine.summaries.observe(queued).value.facePrivacyBlocked)
        assertTrue(f.engine.library.value.summaries.isEmpty())
    }

    @Test fun unavailableInspectionCanBeRetried() = runTest {
        val f = Fixture(this, FaceInspectionResult.NO_FACE)
        f.inspectionFails = true
        f.engine.setFacePrivacyEnabled(true)
        f.engine.summaries.request(f.request)
        advanceUntilIdle()
        assertTrue(f.engine.summaries.observe(f.request).value.facePrivacyUnavailable)
        f.inspectionFails = false
        f.engine.summaries.request(f.request, force = true)
        advanceUntilIdle()
        assertEquals(1, f.client.calls)
        assertNotNull(f.engine.summaries.observe(f.request).value.content)
    }

    @Test fun enablingDuringFailingRemoteWorkStillFinishesLocalInspection() = runTest {
        val f = Fixture(this, FaceInspectionResult.FACE_DETECTED)
        val finish = CompletableDeferred<Unit>()
        f.client.beforeResult = { finish.await(); error("provider unavailable") }
        f.engine.summaries.request(f.request)
        runCurrent()
        f.engine.setFacePrivacyEnabled(true)
        finish.complete(Unit)
        advanceUntilIdle()
        assertTrue(f.engine.summaries.observe(f.request).value.facePrivacyBlocked)
        assertFalse(f.engine.summaries.observe(f.request).value.facePrivacyPending)
    }

    @Test fun localDetectionDoesNotWaitForAnUnrelatedRemoteRequest() = runTest {
        val f = Fixture(this, FaceInspectionResult.FACE_DETECTED)
        val finish = CompletableDeferred<Unit>()
        f.client.beforeResult = { finish.await() }
        f.engine.summaries.request(f.request)
        runCurrent()
        f.engine.setFacePrivacyEnabled(true)
        val next = AiSummaryRequest("next-without-provider", f.request.image, null, "en")
        f.engine.summaries.request(next)
        runCurrent()
        assertTrue(f.engine.summaries.observe(next).value.facePrivacyBlocked)
        finish.complete(Unit)
        advanceUntilIdle()
    }

    @Test fun enablingPrivacyCancelsRunningSummaryAsSoonAsFaceIsDetected() = runTest {
        val f = Fixture(this, FaceInspectionResult.FACE_DETECTED)
        var cancelled = false
        f.client.beforeResult = {
            try { awaitCancellation() } finally { cancelled = true }
        }
        f.engine.summaries.request(f.request)
        runCurrent()
        assertEquals(1, f.client.calls)
        f.engine.setFacePrivacyEnabled(true)
        runCurrent()
        assertTrue(cancelled)
        assertTrue(f.engine.summaries.observe(f.request).value.facePrivacyBlocked)
        assertTrue(f.engine.library.value.summaries.isEmpty())
    }

    @Test fun pendingInspectionPreventsCredentialAccessAndSummaryDispatch() = runTest {
        val f = Fixture(this, FaceInspectionResult.FACE_DETECTED)
        val finish = CompletableDeferred<Unit>()
        f.beforeInspect = { finish.await() }
        f.engine.setFacePrivacyEnabled(true)
        f.engine.summaries.request(f.request, force = true)
        runCurrent()
        assertTrue(f.engine.summaries.observe(f.request).value.facePrivacyPending)
        assertEquals(0, f.decryptions)
        assertEquals(0, f.client.calls)
        finish.complete(Unit)
        advanceUntilIdle()
        assertTrue(f.engine.summaries.observe(f.request).value.facePrivacyBlocked)
        assertEquals(0, f.decryptions)
        assertEquals(0, f.client.calls)
        assertTrue(f.engine.library.value.summaries.isEmpty())
    }

    private class Fixture(scope: CoroutineScope, val result: FaceInspectionResult) {
        val store = MemoryStore()
        val client = Client()
        val profile = AiProfile("vision", name = "Vision", providerId = "openai-vision", model = "vision",
            baseUrl = "https://example.test/v1", encryptedToken = "test")
        var detectorInitializations = 0
        var decryptions = 0
        var inspectionFails = false
        var inspected: ByteArray? = null
        var beforeDecrypt: suspend () -> Unit = {}
        var beforeInspect: suspend () -> Unit = {}
        val engine = AiEngine(store, UnusedFiles, client, scope, { it }, {
            decryptions++; beforeDecrypt(); it
        }, faceInspectorFactory = {
            detectorInitializations++
            FaceInspector { bytes ->
                inspected = bytes
                beforeInspect()
                if (inspectionFails) error("native unavailable")
                result
            }
        })
        val request = AiSummaryRequest("photo", EncodedAiImage(byteArrayOf(1, 2, 3)), profile, "en")
    }

    private class Client : AiModelClient by AiModel.builder().registerBuiltIns().build() {
        var calls = 0
        var beforeResult: suspend () -> Unit = {}
        override fun understandImage(request: ImageUnderstandingRequest, config: ProviderRuntimeConfig) = flow {
            calls++
            beforeResult()
            emit(ImageUnderstandingEvent.Completed(request.operationId, ImageUnderstandingResult.Success(ImageUnderstandingOutput(
                buildJsonObject { put("summary", "landscape"); put("narration", "a landscape"); put("tags", buildJsonArray { add("nature") }) },
                config.providerId, config.model))))
        }
    }
    private class MemoryStore : ObjectStore<AiLibrary> {
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
