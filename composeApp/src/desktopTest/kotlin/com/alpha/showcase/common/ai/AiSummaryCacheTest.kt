package com.alpha.showcase.common.ai

import coil3.asImage
import com.alpha.ai.imagegeneration.*
import com.alpha.ai.imagegeneration.provider.registerBuiltIns
import com.alpha.facedetection.FaceInspectionResult
import com.alpha.facedetection.FaceInspector
import com.alpha.showcase.common.storage.ObjectStore
import com.alpha.showcase.common.ui.play.DataWithType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AiSummaryCacheTest {
    private val original = AiProfile("vision", name = "Vision", providerId = "openai-vision", model = "old-model",
        baseUrl = "https://api.example/v1", encryptedToken = "token")

    @Test fun identicalFilesAcrossSourcesLanguagesAndMissingProfilesReuseOnePaidResult() = runTest {
        val store = MemoryStore(AiLibrary(facePrivacyEnabled = false))
        val client = Client()
        val engine = engine(store, client)
        Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(32, 32); bitmap.erase(Color.BLUE)
            val image = bitmap.asImage()
            val identity = testImageIdentity()
            val first = engine.summaries.prepare("/folder/photo.png", image, original, "en", identity)
            engine.summaries.request(first)
            advanceUntilIdle()
            val another = engine.summaries.prepare("https://nas/photos/renamed.png", image, original.copy(model = "new"), "zh-CN", identity)
            assertEquals(first.key, another.key)
            engine.summaries.request(another)
            advanceUntilIdle()
            val importedOnly = engine.summaries.prepare("smb://host/photo.png", image, null, "ja", identity)
            engine.summaries.request(importedOnly)
            advanceUntilIdle()
            assertEquals(listOf("old-model"), client.models)
            assertEquals("old-model", engine.summaries.observe(importedOnly).value.content?.narration)

            val unverified = engine.summaries.prepare("/folder/unknown.png", image, original, "en")
            engine.summaries.request(unverified, force = true)
            advanceUntilIdle()
            assertTrue(engine.summaries.observe(unverified).value.failed)
            assertEquals(1, client.models.size)
        }
    }

    @Test fun savedSummarySurvivesProfileEditsSwitchingAndRestartUntilExplicitRegeneration() = runTest {
        val store = MemoryStore(AiLibrary(facePrivacyEnabled = false))
        val client = Client()
        val engine = engine(store, client)
        engine.saveProfile(original, "token")
        val firstProfile = engine.library.value.activeProfiles.single()
        Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(32, 32); bitmap.erase(Color.BLUE)
            val image = bitmap.asImage()
            val media = DataWithType("photo.jpg", "image/jpeg")
            val first = engine.summaries.prepare(media, image, firstProfile, "en", testImageIdentity())
            engine.summaries.request(first)
            advanceUntilIdle()
            assertEquals("old-model", engine.summaries.observe(first).value.content?.narration)

            engine.saveProfile(firstProfile.copy(model = "edited-model"), "new-token")
            engine.saveProfile(original.copy(id = "another", providerId = "gemini-vision", model = "new-model"), "token")
            val restarted = engine(store, client)
            restarted.initialize()
            for (profile in restarted.library.value.activeProfiles) {
                val request = restarted.summaries.prepare(media, image, profile, "en", testImageIdentity())
                restarted.summaries.request(request)
                advanceUntilIdle()
                assertEquals("old-model", restarted.summaries.observe(request).value.content?.narration)
            }
            assertEquals(listOf("old-model"), client.models)

            val selected = restarted.library.value.activeProfiles.last()
            val forced = restarted.summaries.prepare(media, image, selected, "en", testImageIdentity())
            restarted.summaries.request(forced, force = true)
            advanceUntilIdle()
            assertEquals("new-model", restarted.summaries.observe(forced).value.content?.narration)
            assertEquals(listOf("old-model", "new-model"), client.models)
            assertEquals(2, store.repository.versions.size)
            assertTrue(restarted.library.value.summaries.isEmpty())
        }
    }

    @Test fun legacySummaryFromArchivedProfileIsReusedAndMigratedForWrappedMedia() = runTest {
        Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(32, 32); bitmap.erase(Color.BLUE)
            val image = bitmap.asImage()
            val encoded = encodeAiImage(image, 512L * 1024, 768)
            // Frozen pre-migration format, including DataWithType's nested hash.
            fun legacyHash(identity: String) = listOf(identity, "vision", "1", "en", "slide-summary-v2", "full-frame-v1")
                .joinToString("\u0000").encodeUtf8().sha256().hex()
            val legacyKey = "${legacyHash(legacyHash("photo.jpg"))}:${encoded.bytes.toByteString().sha256().hex()}:true"
                .encodeUtf8().sha256().hex()
            val saved = AiSummaryContent("saved summary", "saved narration", listOf("blue"))
            val updated = original.copy(revision = 2, model = "edited-model")
            val store = MemoryStore(AiLibrary(profiles = listOf(original.copy(archived = true), updated),
                summaries = mapOf(legacyKey to saved), facePrivacyEnabled = false))
            val client = Client()
            val engine = engine(store, client)
            val media = DataWithType("photo.jpg", "image/jpeg")
            val request = engine.summaries.prepare(media, image, updated, "en", testImageIdentity())
            engine.summaries.request(request)
            advanceUntilIdle()
            assertEquals(saved, engine.summaries.observe(request).value.content)
            assertTrue(client.models.isEmpty())
            assertEquals(mapOf(legacyKey to saved), store.value.summaries)
            assertEquals(saved, store.repository.find(testImageIdentity()))
        }
    }

    private fun CoroutineScope.engine(store: MemoryStore, client: Client) = AiEngine(
        store, UnusedFiles, client, this, { it }, { it },
        faceInspectorFactory = { FaceInspector { FaceInspectionResult.NO_FACE } },
        summaryRepository = store.repository,
    )

    private class Client : AiModelClient by AiModel.builder().registerBuiltIns().build() {
        val models = mutableListOf<String>()
        override fun understandImage(request: ImageUnderstandingRequest, config: ProviderRuntimeConfig) = flow {
            models += config.model.orEmpty()
            emit(ImageUnderstandingEvent.Completed(request.operationId, ImageUnderstandingResult.Success(ImageUnderstandingOutput(
                buildJsonObject { put("summary", "blue picture"); put("narration", config.model); put("tags", buildJsonArray { add("blue") }) },
                config.providerId, config.model))))
        }
    }

    private class MemoryStore(var value: AiLibrary) : ObjectStore<AiLibrary> {
        val repository = TestSummaryRepository()
        override suspend fun get() = value
        override suspend fun set(value: AiLibrary) { this.value = value }
        override suspend fun delete() { value = AiLibrary() }
    }

    private object UnusedFiles : AiFiles {
        override suspend fun write(name: String, bytes: ByteArray): Unit = error("unused")
        override suspend fun read(name: String): ByteArray = error("unused")
        override suspend fun delete(name: String): Unit = error("unused")
        override fun imageModel(name: String): Any = error("unused")
    }
}
