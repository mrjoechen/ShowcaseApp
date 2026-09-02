package com.alpha.showcase.api

import com.alpha.showcase.api.s3.AwsV4Signer
import com.alpha.showcase.api.s3.S3ListParser
import com.alpha.showcase.api.s3.S3Connection
import com.alpha.showcase.api.s3.S3RequestFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class S3ApiTest {

    @Test
    fun headerSignatureMatchesTheAwsGetBucketLifecycleTestVector() = runTest {
        val signed = AwsV4Signer.signHeaders(
            method = "GET",
            host = "examplebucket.s3.amazonaws.com",
            canonicalUri = "/",
            queryParameters = listOf("lifecycle" to ""),
            accessKey = "AKIAIOSFODNN7EXAMPLE",
            secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "us-east-1",
            amzDate = "20130524T000000Z",
        )

        assertEquals(
            """GET
/
lifecycle=
host:examplebucket.s3.amazonaws.com
x-amz-content-sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
x-amz-date:20130524T000000Z

host;x-amz-content-sha256;x-amz-date
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855""",
            signed.canonicalRequest,
        )
        assertEquals(
            "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request," +
                "SignedHeaders=host;x-amz-content-sha256;x-amz-date," +
                "Signature=fea454ca298b7da1c68078a5d1bdbfbbe0d65c699e0f91ac7a200a0136783543",
            signed.authorization,
        )
    }

    @Test
    fun presignedUrlMatchesTheAwsGetObjectTestVector() = runTest {
        val url = AwsV4Signer.presignUrl(
            scheme = "https",
            host = "examplebucket.s3.amazonaws.com",
            canonicalUri = "/test.txt",
            accessKey = "AKIAIOSFODNN7EXAMPLE",
            secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "us-east-1",
            amzDate = "20130524T000000Z",
            expiresSeconds = 86_400,
        )

        assertEquals(
            "https://examplebucket.s3.amazonaws.com/test.txt" +
                "?X-Amz-Algorithm=AWS4-HMAC-SHA256" +
                "&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request" +
                "&X-Amz-Date=20130524T000000Z" +
                "&X-Amz-Expires=86400" +
                "&X-Amz-SignedHeaders=host" +
                "&X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404",
            url,
        )
    }

    @Test
    fun listObjectsResponseKeepsObjectsPrefixesAndContinuationState() {
        val page = S3ListParser.parse(
            """
            <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
              <IsTruncated>true</IsTruncated>
              <Contents>
                <Key>photos/cat &amp; dog.jpg</Key>
                <LastModified>2026-08-20T01:02:03.000Z</LastModified>
                <ETag>&quot;etag-1&quot;</ETag>
                <Size>123</Size>
              </Contents>
              <CommonPrefixes><Prefix>photos/holidays/</Prefix></CommonPrefixes>
              <NextContinuationToken>next-token</NextContinuationToken>
            </ListBucketResult>
            """.trimIndent(),
        )

        assertTrue(page.isTruncated)
        assertEquals("next-token", page.nextContinuationToken)
        assertEquals("photos/holidays/", page.commonPrefixes.single())
        assertEquals("photos/cat & dog.jpg", page.objects.single().key)
        assertEquals(123L, page.objects.single().size)
        assertEquals("etag-1", page.objects.single().etag)

        val finalPage = S3ListParser.parse("<ListBucketResult><IsTruncated>false</IsTruncated></ListBucketResult>")
        assertFalse(finalPage.isTruncated)
        assertEquals(emptyList(), finalPage.objects)
    }

    @Test
    fun requestFactoryUsesVirtualHostingForAwsAndPathStyleForCustomEndpoints() = runTest {
        val awsRequest = S3RequestFactory.listObjects(
            connection = S3Connection(
                endpoint = "s3.amazonaws.com",
                accessKey = "access",
                secretKey = "secret",
                bucket = "photos",
                region = "us-east-1",
                prefix = "family photos/",
                useSSL = true,
            ),
            recursive = false,
            continuationToken = "next/token",
            amzDate = "20260820T010203Z",
        )

        assertTrue(awsRequest.url.startsWith("https://photos.s3.amazonaws.com/?"))
        assertTrue(awsRequest.url.contains("continuation-token=next%2Ftoken"))
        assertTrue(awsRequest.url.contains("delimiter=%2F"))
        assertTrue(awsRequest.url.contains("prefix=family%20photos%2F"))
        assertEquals("20260820T010203Z", awsRequest.headers["x-amz-date"])
        assertTrue(awsRequest.headers.getValue("Authorization").contains("Credential=access/20260820/us-east-1/s3/aws4_request"))

        val customConnection = S3Connection(
            endpoint = "localhost:9000",
            accessKey = "access",
            secretKey = "secret",
            bucket = "photos",
            region = "us-east-1",
            useSSL = false,
        )
        val customRequest = S3RequestFactory.listObjects(
            connection = customConnection,
            recursive = true,
            amzDate = "20260820T010203Z",
        )
        val objectUrl = S3RequestFactory.presignObjectUrl(
            connection = customConnection,
            key = "family/cat photo.jpg",
            amzDate = "20260820T010203Z",
        )

        assertTrue(customRequest.url.startsWith("http://localhost:9000/photos/?"))
        assertTrue(objectUrl.startsWith("http://localhost:9000/photos/family/cat%20photo.jpg?"))
    }

    @Test
    fun requestFactoryRejectsABlankEndpoint() = runTest {
        val connection = S3Connection(
            endpoint = "  ",
            accessKey = "access",
            secretKey = "secret",
            bucket = "photos",
            region = "us-east-1",
        )

        assertFailsWith<IllegalArgumentException> {
            S3RequestFactory.listObjects(
                connection = connection,
                recursive = false,
                amzDate = "20260820T010203Z",
            )
        }
    }
}
