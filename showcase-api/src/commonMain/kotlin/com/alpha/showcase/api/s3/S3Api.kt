@file:OptIn(ExperimentalTime::class)

package com.alpha.showcase.api.s3

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class S3Connection(
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
    val region: String,
    val prefix: String = "",
    val useSSL: Boolean = true,
)

data class S3HttpRequest(
    val url: String,
    val headers: Map<String, String>,
)

object S3RequestFactory {
    suspend fun listObjects(
        connection: S3Connection,
        recursive: Boolean,
        continuationToken: String? = null,
        amzDate: String = currentAwsDate(),
    ): S3HttpRequest {
        val address = resolveAddress(connection)
        val query = buildList {
            add("list-type" to "2")
            add("max-keys" to "1000")
            connection.prefix.normalizedPrefix().takeIf { it.isNotEmpty() }?.let { add("prefix" to it) }
            if (!recursive) add("delimiter" to "/")
            continuationToken?.takeIf { it.isNotBlank() }?.let { add("continuation-token" to it) }
        }
        val signed = AwsV4Signer.signHeaders(
            method = "GET",
            host = address.host,
            canonicalUri = address.bucketRoot,
            queryParameters = query,
            accessKey = connection.accessKey,
            secretKey = connection.secretKey,
            region = connection.region.ifBlank { "us-east-1" },
            amzDate = amzDate,
        )
        val canonicalQuery = AwsV4Signer.canonicalizeQuery(query)
        return S3HttpRequest(
            url = "${address.scheme}://${address.host}${AwsV4Signer.encodePath(address.bucketRoot)}?$canonicalQuery",
            headers = mapOf(
                "Authorization" to signed.authorization,
                "x-amz-content-sha256" to signed.payloadHash,
                "x-amz-date" to signed.amzDate,
            ),
        )
    }

    suspend fun presignObjectUrl(
        connection: S3Connection,
        key: String,
        amzDate: String = currentAwsDate(),
        expiresSeconds: Int = 3_600,
    ): String {
        val address = resolveAddress(connection)
        val objectPath = address.bucketRoot.trimEnd('/') + "/" + key.trimStart('/')
        return AwsV4Signer.presignUrl(
            scheme = address.scheme,
            host = address.host,
            canonicalUri = objectPath,
            accessKey = connection.accessKey,
            secretKey = connection.secretKey,
            region = connection.region.ifBlank { "us-east-1" },
            amzDate = amzDate,
            expiresSeconds = expiresSeconds,
        )
    }

    private fun resolveAddress(connection: S3Connection): S3Address {
        val endpoint = connection.endpoint.trim()
        val explicitScheme = endpoint.substringBefore("://", missingDelimiterValue = "")
            .lowercase()
            .takeIf { it == "http" || it == "https" }
        val scheme = explicitScheme ?: if (connection.useSSL) "https" else "http"
        val authority = if (explicitScheme != null) endpoint.substringAfter("://") else endpoint
        val host = authority.trim().trimEnd('/')
        require(host.isNotBlank() && !host.contains('/')) {
            "S3 endpoint must contain only a host and optional port"
        }
        val endpointHost = host.substringBefore(':').lowercase()
        val virtualHosted = isAwsEndpoint(endpointHost) &&
            isDnsCompatibleBucket(connection.bucket) &&
            !(scheme == "https" && connection.bucket.contains('.'))
        return if (virtualHosted) {
            S3Address(scheme = scheme, host = "${connection.bucket}.$host", bucketRoot = "/")
        } else {
            S3Address(
                scheme = scheme,
                host = host,
                bucketRoot = "/${connection.bucket.trim('/')}/",
            )
        }
    }

    private fun isAwsEndpoint(host: String): Boolean =
        host == "s3.amazonaws.com" ||
            (host.startsWith("s3.") && host.endsWith(".amazonaws.com")) ||
            (host.startsWith("s3.") && host.endsWith(".amazonaws.com.cn"))

    private fun isDnsCompatibleBucket(bucket: String): Boolean =
        bucket.length in 3..63 &&
            bucket.firstOrNull()?.isLetterOrDigit() == true &&
            bucket.lastOrNull()?.isLetterOrDigit() == true &&
            bucket.all { it.isLowerCase() || it.isDigit() || it == '-' || it == '.' }

    private data class S3Address(
        val scheme: String,
        val host: String,
        val bucketRoot: String,
    )

    private fun String.normalizedPrefix(): String = trim().trimStart('/').let { prefix ->
        if (prefix.isNotEmpty() && !prefix.endsWith('/')) "$prefix/" else prefix
    }

    private fun currentAwsDate(): String {
        val iso = Clock.System.now().toString()
        val date = iso.substring(0, 10).replace("-", "")
        val time = iso.substring(11, 19).replace(":", "")
        return "${date}T${time}Z"
    }
}

class S3Api(
    private val client: HttpClient = HttpClient { expectSuccess = true },
) {
    suspend fun listObjectsPage(
        connection: S3Connection,
        recursive: Boolean,
        continuationToken: String? = null,
    ): S3ListPage {
        val request = S3RequestFactory.listObjects(connection, recursive, continuationToken)
        val response = client.get(request.url) {
            headers {
                request.headers.forEach { (name, value) -> append(name, value) }
            }
        }
        return S3ListParser.parse(response.bodyAsText())
    }

    suspend fun presignObjectUrl(connection: S3Connection, key: String): String =
        S3RequestFactory.presignObjectUrl(connection, key)
}
