package com.alpha.showcase.common.ai

import com.alpha.ai.imagegeneration.AiModel
import com.alpha.ai.imagegeneration.provider.registerBuiltIns
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.*

class AiLibraryStoreTest {
    @Test fun explicitRegenerationCanRestoreIdenticalContentFromAStaleInstance() = runTest {
        FakeFileSystem().use { fs ->
            val path = "/app/files/ai-library-v1.json".toPath()
            val old = AiSummaryContent("old", "old", listOf("old"))
            val newer = AiSummaryContent("new", "new", listOf("new"))
            FileAiLibraryStore(fs, path).set(AiLibrary(summaries = mapOf("photo" to old)))
            val unusedFiles = object : AiFiles {
                override suspend fun write(name: String, bytes: ByteArray): Unit = error("unused")
                override suspend fun read(name: String): ByteArray = error("unused")
                override suspend fun delete(name: String): Unit = error("unused")
                override fun imageModel(name: String): Any = error("unused")
            }
            fun engine() = AiEngine(FileAiLibraryStore(fs, path), unusedFiles,
                AiModel.builder().registerBuiltIns().build(), this, { it }, { it })
            val first = engine()
            val second = engine()
            first.initialize()
            second.initialize()
            second.saveSummary("photo", newer)
            first.saveSummary("photo", old)
            val stored = FileAiLibraryStore(fs, path).get()!!
            assertEquals(old, stored.summaries["photo"])
            assertTrue(newer in stored.summaryHistory["photo"].orEmpty())
        }
    }

    @Test fun genericStoreDeletionCannotErasePaidSummaries() = runTest {
        FakeFileSystem().use { fs ->
            val store = FileAiLibraryStore(fs, "/app/files/ai-library-v1.json".toPath())
            val library = AiLibrary(summaries = mapOf("photo" to AiSummaryContent("saved", "saved", listOf("tag"))))
            store.set(library)
            assertFailsWith<IllegalStateException> { store.delete() }
            assertEquals(library, store.get())
        }
    }

    @Test fun staleWriterCannotRemoveOtherWritersSummariesOrRollBackRegeneration() = runTest {
        FakeFileSystem().use { fs ->
            val path = "/app/files/ai-library-v1.json".toPath()
            val first = FileAiLibraryStore(fs, path)
            val second = FileAiLibraryStore(fs, path)
            val old = AiSummaryContent("old", "old", listOf("old"))
            val newer = AiSummaryContent("new", "new", listOf("new"))
            val other = AiSummaryContent("other", "other", listOf("other"))
            first.set(AiLibrary(summaries = mapOf("photo" to old)))
            val stale = second.get()!!
            first.set(first.get()!!.copy(summaries = mapOf("photo" to newer, "other" to other)))
            second.set(stale.copy(facePrivacyEnabled = false))
            val stored = first.get()!!
            assertEquals(mapOf("photo" to newer, "other" to other), stored.summaries)
            assertTrue(old in stored.summaryHistory["photo"].orEmpty())
        }
    }

    @Test fun metadataSurvivesCacheEvictionAndReopeningTheStore() = runTest {
        FakeFileSystem().use { fs ->
            val path = "/app/files/ai-library-v1.json".toPath()
            val profile = AiProfile("test", name = "Test", providerId = "openai", model = "gpt-image-1", baseUrl = "https://api.example", encryptedToken = "encrypted")
            val expected = AiLibrary(profiles = listOf(profile), generationProfileId = profile.id,
                summaries = mapOf("photo-key" to AiSummaryContent("summary", "narration", listOf("photo"))))
            FileAiLibraryStore(fs, path).set(expected)
            fs.createDirectories("/app/cache".toPath())
            fs.write("/app/cache/image.jpg".toPath()) { writeUtf8("cached") }
            fs.deleteRecursively("/app/cache".toPath())
            assertEquals(expected, FileAiLibraryStore(fs, path).get())
            assertFalse(fs.exists("/app/files/ai-library-v1.json.tmp".toPath()))
        }
    }
}
