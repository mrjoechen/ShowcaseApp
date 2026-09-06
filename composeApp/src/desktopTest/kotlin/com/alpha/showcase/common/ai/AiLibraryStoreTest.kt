package com.alpha.showcase.common.ai

import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.*

class AiLibraryStoreTest {
    @Test fun metadataSurvivesCacheEvictionAndReopeningTheStore() = runTest {
        FakeFileSystem().use { fs ->
            val path = "/app/files/ai-library-v1.json".toPath()
            val profile = AiProfile("test", name = "Test", providerId = "openai", model = "gpt-image-1", baseUrl = "https://api.example", encryptedToken = "encrypted")
            val expected = AiLibrary(profiles = listOf(profile), generationProfileId = profile.id)
            FileAiLibraryStore(fs, path).set(expected)
            fs.createDirectories("/app/cache".toPath())
            fs.write("/app/cache/image.jpg".toPath()) { writeUtf8("cached") }
            fs.deleteRecursively("/app/cache".toPath())
            assertEquals(expected, FileAiLibraryStore(fs, path).get())
            assertFalse(fs.exists("/app/files/ai-library-v1.json.tmp".toPath()))
        }
    }
}
