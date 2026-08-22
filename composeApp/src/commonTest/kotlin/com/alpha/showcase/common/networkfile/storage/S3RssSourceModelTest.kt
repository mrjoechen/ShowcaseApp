package com.alpha.showcase.common.networkfile.storage

import com.alpha.showcase.common.networkfile.storage.remote.RSS
import com.alpha.showcase.common.networkfile.storage.remote.RssSource
import com.alpha.showcase.common.networkfile.storage.remote.S3
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_RSS
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_S3
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.repo.withEncryptedSecretKey
import com.alpha.showcase.common.utils.getIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.ic_amazon_s3
import showcaseapp.composeapp.generated.resources.ic_rss

class S3RssSourceModelTest {

    @Test
    fun sourceTypesResolveFromBothTypeIdAndRemoteConfiguration() {
        val s3 = S3Source(
            name = "Archive",
            endpoint = "s3.amazonaws.com",
            accessKey = "access",
            secretKey = "encrypted-secret",
            bucket = "photos",
            region = "us-east-1",
        )
        val rss = RssSource(name = "News", url = "https://example.com/feed.xml")

        assertEquals(S3, getType(TYPE_S3))
        assertEquals(RSS, getType(TYPE_RSS))
        assertEquals(TYPE_S3, s3.getType())
        assertEquals(TYPE_RSS, rss.getType())
        assertEquals(Res.drawable.ic_amazon_s3, s3.getIcon())
        assertEquals(Res.drawable.ic_rss, rss.getIcon())
    }

    @Test
    fun storageSourcesRoundTripS3AndRssPolymorphically() {
        val original = StorageSources(
            version = 1,
            versionName = "test",
            id = "source-set",
            sourceName = "default",
            timeStamp = 123L,
            sources = mutableListOf(
                S3Source(
                    name = "Archive",
                    endpoint = "objects.example.com",
                    accessKey = "access",
                    secretKey = "encrypted-secret",
                    bucket = "photos",
                    region = "eu-west-1",
                    prefix = "family/",
                    useSSL = false,
                ),
                RssSource(
                    name = "News",
                    url = "https://example.com/feed.xml",
                    refreshInterval = 900_000L,
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

        val decodedS3 = assertIs<S3Source>(decoded.sources[0])
        assertEquals("objects.example.com", decodedS3.endpoint)
        assertEquals("encrypted-secret", decodedS3.secretKey)
        assertEquals("family/", decodedS3.prefix)
        assertEquals(false, decodedS3.useSSL)

        val decodedRss = assertIs<RssSource>(decoded.sources[1])
        assertEquals("https://example.com/feed.xml", decodedRss.url)
        assertEquals(900_000L, decodedRss.refreshInterval)
    }

    @Test
    fun legacyRssConfigurationReceivesTheOneHourRefreshDefault() {
        val decoded = StorageSourceSerializer.sourceJson.decodeFromString<RssSource>(
            """{"name":"News","url":"https://example.com/feed.xml"}""",
        )

        assertEquals(3_600_000L, decoded.refreshInterval)
    }

    @Test
    fun sourcePickerCatalogExposesS3AndRss() {
        val types = SUPPORT_LIST.map { it.first }

        assertTrue(S3 in types)
        assertTrue(RSS in types)
    }

    @Test
    fun s3SecretNormalizationEncryptsLegacyPlaintextAndIsIdempotent() {
        val previousEncrypt = RConfig.encryptBlocking
        val previousDecrypt = RConfig.decryptBlocking
        try {
            RConfig.initEnCryptAndDecrypt(
                encrypt = { value -> if (value.startsWith("test-encrypted:")) value else "test-encrypted:$value" },
                decrypt = { value -> value.removePrefix("test-encrypted:") },
                encryptAsync = { value -> if (value.startsWith("test-encrypted:")) value else "test-encrypted:$value" },
                decryptAsync = { value -> value.removePrefix("test-encrypted:") },
            )
            val legacy = S3Source(
                name = "Archive",
                endpoint = "s3.amazonaws.com",
                accessKey = "access",
                secretKey = "plain-secret",
                bucket = "photos",
                region = "us-east-1",
            )

            val encrypted = legacy.withEncryptedSecretKey()
            val normalizedAgain = encrypted.withEncryptedSecretKey()

            assertTrue(encrypted.secretKey != "plain-secret")
            assertEquals("plain-secret", RConfig.decryptBlocking(encrypted.secretKey))
            assertEquals(encrypted.secretKey, normalizedAgain.secretKey)
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
