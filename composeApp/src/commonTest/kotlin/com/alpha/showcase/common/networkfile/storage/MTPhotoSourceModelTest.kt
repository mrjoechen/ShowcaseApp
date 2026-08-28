package com.alpha.showcase.common.networkfile.storage

import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO
import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO_AUTH_TYPE_API_KEY
import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO_AUTH_TYPE_PASSWORD
import com.alpha.showcase.common.networkfile.storage.remote.MTPhotoSource
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_MTPHOTO
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
import com.alpha.showcase.common.repo.withEncryptedCredentials
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MTPhotoSourceModelTest {

    @Test
    fun sourceTypeResolvesFromIdAndRemoteConfiguration() {
        val source = apiKeySource(apiKey = "encrypted-api-key")

        assertEquals(MTPHOTO, getType(TYPE_MTPHOTO))
        assertEquals(TYPE_MTPHOTO, source.getType())
        assertEquals("MTPhoto", MTPHOTO.typeName)
    }

    @Test
    fun storageSourcesRoundTripMtPhotoPolymorphically() {
        val original = StorageSources(
            version = 1,
            versionName = "test",
            id = "source-set",
            sourceName = "default",
            timeStamp = 123L,
            sources = mutableListOf(
                MTPhotoSource(
                    name = "Family",
                    url = "https://photos.example.com",
                    authType = MTPHOTO_AUTH_TYPE_PASSWORD,
                    user = "joe",
                    pass = "encrypted-password",
                    albumId = 7,
                    albumName = "Summer",
                ),
            ),
        )

        val encoded = StorageSourceSerializer.sourceJson.encodeToString(
            StorageSources.serializer(),
            original,
        )
        val decoded = StorageSourceSerializer.sourceJson.decodeFromString(
            StorageSources.serializer(),
            encoded,
        )

        val source = assertIs<MTPhotoSource>(decoded.sources.single())
        assertEquals("https://photos.example.com", source.url)
        assertEquals(MTPHOTO_AUTH_TYPE_PASSWORD, source.authType)
        assertEquals("joe", source.user)
        assertEquals("encrypted-password", source.pass)
        assertEquals(7, source.albumId)
        assertEquals("Summer", source.albumName)
    }

    @Test
    fun apiKeyNormalizationEncryptsLegacyPlaintextAndIsIdempotent() = withTestEncryption {
        val legacy = apiKeySource(apiKey = "plain-api-key")

        val encrypted = legacy.withEncryptedCredentials()
        val normalizedAgain = encrypted.withEncryptedCredentials()

        assertTrue(encrypted.apiKey != "plain-api-key")
        assertEquals("plain-api-key", RConfig.decryptAsync(encrypted.apiKey.orEmpty()))
        assertEquals(encrypted, normalizedAgain)
        assertEquals(null, encrypted.pass)
    }

    @Test
    fun passwordNormalizationEncryptsLegacyPlaintextAndIsIdempotent() = withTestEncryption {
        val legacy = MTPhotoSource(
            name = "Family",
            url = "https://photos.example.com",
            authType = MTPHOTO_AUTH_TYPE_PASSWORD,
            user = "joe",
            pass = "plain-password",
            albumId = 7,
            albumName = "Summer",
        )

        val encrypted = legacy.withEncryptedCredentials()
        val normalizedAgain = encrypted.withEncryptedCredentials()

        assertTrue(encrypted.pass != "plain-password")
        assertEquals("plain-password", RConfig.decryptAsync(encrypted.pass.orEmpty()))
        assertEquals(encrypted, normalizedAgain)
        assertEquals(null, encrypted.apiKey)
    }

    private fun apiKeySource(apiKey: String) = MTPhotoSource(
        name = "Family",
        url = "https://photos.example.com",
        authType = MTPHOTO_AUTH_TYPE_API_KEY,
        apiKey = apiKey,
        albumId = 7,
        albumName = "Summer",
    )

    private fun withTestEncryption(block: suspend () -> Unit) = kotlinx.coroutines.test.runTest {
        val previousEncrypt = RConfig.encryptBlocking
        val previousDecrypt = RConfig.decryptBlocking
        try {
            RConfig.initEnCryptAndDecrypt(
                encrypt = { value -> if (value.startsWith("test-encrypted:")) value else "test-encrypted:$value" },
                decrypt = { value -> value.removePrefix("test-encrypted:") },
                encryptAsync = { value -> if (value.startsWith("test-encrypted:")) value else "test-encrypted:$value" },
                decryptAsync = { value -> value.removePrefix("test-encrypted:") },
            )
            block()
        } finally {
            RConfig.initEnCryptAndDecrypt(
                previousEncrypt,
                previousDecrypt,
                encryptAsync = { previousEncrypt(it) },
                decryptAsync = { previousDecrypt(it) },
            )
        }
    }
}
