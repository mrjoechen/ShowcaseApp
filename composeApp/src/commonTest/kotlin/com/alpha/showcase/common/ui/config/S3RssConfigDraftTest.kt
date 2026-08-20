package com.alpha.showcase.common.ui.config

import com.alpha.showcase.common.networkfile.storage.remote.S3_DEFAULT_REGION
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class S3RssConfigDraftTest {

    @Test
    fun s3EndpointAcceptsHostIpAndValidPortForms() {
        val validEndpoints = listOf(
            "s3.amazonaws.com",
            "objects.example.com:443",
            "http://minio.local:9000",
            "https://192.168.1.2/",
            "http://[::1]:9000",
            "http://[::ffff:192.168.1.2]:9000",
        )

        validEndpoints.forEach { endpoint ->
            assertTrue(isValidS3Endpoint(endpoint), "Expected valid S3 endpoint: $endpoint")
        }
    }

    @Test
    fun s3EndpointRejectsMalformedAuthorityAndExtraUrlComponents() {
        val invalidEndpoints = listOf(
            ":9000",
            "objects.example.com:",
            "objects.example.com:not-a-port",
            "objects.example.com:0",
            "objects.example.com:65536",
            "user@objects.example.com",
            "objects.example.com/path",
            "objects.example.com//",
            "https://objects.example.com//",
            "objects.example.com?region=us-east-1",
            "objects.example.com#fragment",
            "ftp://objects.example.com",
            "http://",
            "::1",
            "[1:2:3:4:5:6:7:8:]",
            "[:1:2:3:4:5:6:7:8]",
        )

        invalidEndpoints.forEach { endpoint ->
            assertFalse(isValidS3Endpoint(endpoint), "Expected invalid S3 endpoint: $endpoint")
        }
    }

    @Test
    fun s3DraftRejectsABlankEndpoint() {
        val source = S3ConfigDraft(
            name = "Archive",
            endpoint = "",
            accessKey = "access",
            secretKey = "plain-secret",
            bucket = "photos",
            region = "",
            prefix = "/family/2026",
            useSSL = true,
        ).toSource { secret -> "encrypted:$secret" }

        assertNull(source)
    }

    @Test
    fun s3DraftRejectsMultipleTrailingSlashes() {
        val source = S3ConfigDraft(
            name = "Archive",
            endpoint = "https://objects.example.com//",
            accessKey = "access",
            secretKey = "plain-secret",
            bucket = "photos",
            region = "",
            prefix = "",
            useSSL = true,
        ).toSource { secret -> "encrypted:$secret" }

        assertNull(source)
    }

    @Test
    fun s3DraftDefaultsRegionNormalizesPrefixAndEncryptsANewSecret() {
        val source = S3ConfigDraft(
            name = "Archive",
            endpoint = "objects.example.com",
            accessKey = "access",
            secretKey = "plain-secret",
            bucket = "photos",
            region = "",
            prefix = "/family/2026",
            useSSL = true,
        ).toSource { secret -> "encrypted:$secret" }

        requireNotNull(source)
        assertEquals("objects.example.com", source.endpoint)
        assertEquals(S3_DEFAULT_REGION, source.region)
        assertEquals("family/2026/", source.prefix)
        assertEquals("encrypted:plain-secret", source.secretKey)
    }

    @Test
    fun s3DraftKeepsTheExistingEncryptedSecretUntilTheUserChangesIt() {
        val source = S3ConfigDraft(
            name = "Archive",
            endpoint = "minio.local:9000",
            accessKey = "access",
            secretKey = "",
            existingEncryptedSecretKey = "scenc:v2:existing",
            secretKeyChanged = false,
            bucket = "photos",
            region = "us-east-1",
            prefix = "",
            useSSL = false,
        ).toSource { secret -> "encrypted:$secret" }

        requireNotNull(source)
        assertEquals("scenc:v2:existing", source.secretKey)
        assertEquals("minio.local:9000", source.endpoint)
        assertEquals(false, source.useSSL)
    }

    @Test
    fun invalidS3AndRssDraftsAreRejected() {
        assertNull(
            S3ConfigDraft(
                name = "Archive",
                endpoint = "https://objects.example.com/path",
                accessKey = "access",
                secretKey = "secret",
                bucket = "photos",
                region = "us-east-1",
                prefix = "",
                useSSL = true,
            ).toSource { it },
        )
        assertNull(RssConfigDraft("News", "ftp://example.com/feed.xml").toSource())
        assertNull(RssConfigDraft("", "https://example.com/feed.xml").toSource())
    }

    @Test
    fun rssDraftBuildsAnHttpFeedSource() {
        val source = RssConfigDraft(
            name = "News",
            url = " https://example.com/feed.xml ",
        ).toSource()

        requireNotNull(source)
        assertEquals("News", source.name)
        assertEquals("https://example.com/feed.xml", source.url)
    }
}
