package com.alpha.showcase.common.repo

import com.alpha.showcase.common.Startup
import com.alpha.showcase.common.networkfile.storage.remote.Ftp
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.storage.remote.TMDBSource
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.networkfile.storage.StorageSources
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.security.initializeConfigEncryption
import com.alpha.showcase.common.utils.encodePassAsync
import kotlinx.browser.localStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.w3c.dom.get
import org.w3c.dom.set
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SourceListRepoWebTest {

    @BeforeTest
    fun setUp() {
        localStorage.removeItem(SOURCES_KEY)
        localStorage.removeItem(CONFIG_KEY)
        initializeConfigEncryption()
    }

    @AfterTest
    fun tearDown() {
        localStorage.removeItem(SOURCES_KEY)
        localStorage.removeItem(CONFIG_KEY)
    }

    @Test
    fun freshWebInstallStartsWithoutATokenlessProviderSource() = runTest {
        assertTrue(SourceListRepo().getSources().sources.isEmpty())
    }

    @Test
    fun sourcesRoundTripUsesNonBlockingCrypto() = runTest {
        val repository = SourceListRepo()
        val expected = repository.getSources()

        repository.setSources(expected)

        assertTrue(localStorage[SOURCES_KEY].orEmpty().startsWith("scenc:v2:"))
        val restored = repository.getSources()
        assertEquals(expected.id, restored.id)
        assertEquals(expected.sources.map { it.name }, restored.sources.map { it.name })
    }

    @Test
    fun savingSourceEncryptsSensitiveFieldsWithoutBlocking() = runTest {
        val repository = SourceListRepo()
        val source = ftpSource(name = "web-ftp", password = "plain-password")

        assertTrue(repository.saveSource(source))

        val saved = repository.getSources().sources
            .filterIsInstance<Ftp>()
            .single { it.name == source.name }
        assertTrue(saved.passwd.startsWith("scenc:v2:"))
        assertEquals("plain-password", RConfig.decryptAsync(saved.passwd))
    }

    @Test
    fun savingPexelsSourceEncryptsItsConfiguredApiKey() = runTest {
        val repository = SourceListRepo()
        val source = PexelsSource(
            name = "web-pexels",
            photoType = PEXELS_FEED_PHOTOS,
            extra = mapOf(PEXELS_API_KEY_KEY to "plain-api-key"),
        )

        assertTrue(repository.saveSource(source))

        val saved = repository.getSources().sources
            .filterIsInstance<PexelsSource>()
            .single { it.name == source.name }
        val storedApiKey = saved.extra.getValue(PEXELS_API_KEY_KEY)
        assertTrue(storedApiKey.startsWith("scenc:v2:"))
        assertEquals("plain-api-key", RConfig.decryptAsync(storedApiKey))
    }

    @Test
    fun savingUnsplashSourceEncryptsItsConfiguredApiKey() = runTest {
        val repository = SourceListRepo()
        val source = UnSplashSource(
            name = "web-unsplash",
            photoType = FEED_PHOTOS,
            apiKey = "plain-api-key",
        )

        assertTrue(repository.saveSource(source))

        val saved = repository.getSources().sources
            .filterIsInstance<UnSplashSource>()
            .single { it.name == source.name }
        val storedApiKey = requireNotNull(saved.apiKey)
        assertTrue(storedApiKey.startsWith("scenc:v2:"))
        assertEquals("plain-api-key", RConfig.decryptAsync(storedApiKey))
    }

    @Test
    fun savingTmdbSourceEncryptsItsConfiguredApiToken() = runTest {
        val repository = SourceListRepo()
        val source = TMDBSource(
            name = "web-tmdb",
            contentType = POPULAR_MOVIES,
            language = Language.ENGLISH_US.value,
            region = Region.US.value,
            imageType = ImageType.POSTER.value,
            apiToken = "plain-api-token",
        )

        assertTrue(repository.saveSource(source))

        val saved = repository.getSources().sources
            .filterIsInstance<TMDBSource>()
            .single { it.name == source.name }
        val storedApiToken = requireNotNull(saved.apiToken)
        assertTrue(storedApiToken.startsWith("scenc:v2:"))
        assertEquals("plain-api-token", RConfig.decryptAsync(storedApiToken))
    }

    @Test
    fun plaintextSourcesAreMigratedToCurrentCiphertext() = runTest {
        val repository = SourceListRepo()
        val expected = repository.getSources()
        localStorage[SOURCES_KEY] = StorageSourceSerializer.sourceJson.encodeToString(
            StorageSources.serializer(),
            expected,
        )

        val restored = repository.getSources()

        assertEquals(expected.id, restored.id)
        assertTrue(localStorage[SOURCES_KEY].orEmpty().startsWith("scenc:v2:"))
    }

    @Test
    fun conflictingEditDoesNotDeleteExistingSources() = runTest {
        val repository = SourceListRepo()
        val first = ftpSource(name = "first", password = "first-password")
        val second = ftpSource(name = "second", password = "second-password")
        assertTrue(repository.saveSource(first))
        assertTrue(repository.saveSource(second))

        val replaced = repository.replaceSource(
            previous = first,
            replacement = ftpSource(name = second.name, password = "replacement-password"),
        )

        assertFalse(replaced)
        val savedNames = repository.getSources().sources.map { it.name }
        assertTrue(first.name in savedNames)
        assertTrue(second.name in savedNames)
    }

    @Test
    fun concurrentSourceMutationsDoNotLoseUpdates() = runTest {
        val repository = SourceListRepo()
        val first = ftpSource(name = "concurrent-first", password = "first-password")
        val second = ftpSource(name = "concurrent-second", password = "second-password")

        val results = listOf(
            async { repository.saveSource(first) },
            async { SourceListRepo().saveSource(second) },
        ).awaitAll()

        assertTrue(results.all { it })
        val savedNames = repository.getSources().sources.map { it.name }
        assertTrue(first.name in savedNames)
        assertTrue(second.name in savedNames)
    }

    @Test
    fun nestedLegacySecretIsReEncryptedInsteadOfWrapped() = runTest {
        val repository = SourceListRepo()
        val legacyCiphertext = "legacy-password"
            .encodePassAsync(legacyKeyMaterial())
            .replaceFirst("scenc:v2:", "scenc:v1:")
        val sources = repository.getSources().copy(
            sources = mutableListOf(ftpSource("legacy-ftp", legacyCiphertext)),
        )
        val rawJson = StorageSourceSerializer.sourceJson.encodeToString(
            StorageSources.serializer(),
            sources,
        )
        localStorage[SOURCES_KEY] = RConfig.encryptAsync(rawJson)

        val restored = repository.getSources().sources.filterIsInstance<Ftp>().single()

        assertTrue(restored.passwd.startsWith("scenc:v2:"))
        assertEquals("legacy-password", RConfig.decryptAsync(restored.passwd))
    }

    @Test
    fun unreadableStoredSourcesAreNotSilentlyReplacedWithDefaults() = runTest {
        val unreadable = "scenc:v2:not-valid-ciphertext"
        localStorage[SOURCES_KEY] = unreadable

        val failure = runCatching { SourceListRepo().getSources() }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(unreadable, localStorage[SOURCES_KEY])
    }

    @Test
    fun corruptedConfigKeyIsNotSilentlyRotated() {
        val corrupted = "not-a-valid-key"
        localStorage[CONFIG_KEY] = corrupted

        val failure = Startup.run().exceptionOrNull()

        assertNotNull(failure)
        assertEquals(corrupted, localStorage[CONFIG_KEY])
    }

    private fun ftpSource(name: String, password: String) = Ftp(
        host = "example.com",
        user = "user",
        passwd = password,
        name = name,
    )

    private fun legacyKeyMaterial(): ByteArray {
        val seed = "1234567890123456:0123456789abcdef".encodeToByteArray()
        return ByteArray(32) { index ->
            val a = seed[index % seed.size].toInt()
            val b = seed[(index * 7 + 3) % seed.size].toInt()
            ((a xor b xor index) and 0xFF).toByte()
        }
    }

    private companion object {
        const val SOURCES_KEY = "sources"
        const val CONFIG_KEY = "showcase_config_key_v2"
    }
}
