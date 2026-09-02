package com.alpha.showcase.api.s3

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256

data class AwsSignedHeaders(
    val canonicalRequest: String,
    val authorization: String,
    val amzDate: String,
    val payloadHash: String,
)

object AwsV4Signer {
    const val EMPTY_PAYLOAD_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val SERVICE = "s3"
    private const val TERMINATOR = "aws4_request"
    private const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"
    private const val HEX = "0123456789abcdef"

    private val sha256 by lazy {
        CryptographyProvider.Default.get(SHA256).hasher()
    }
    private val hmac by lazy {
        CryptographyProvider.Default.get(HMAC)
    }

    suspend fun signHeaders(
        method: String,
        host: String,
        canonicalUri: String,
        queryParameters: List<Pair<String, String>> = emptyList(),
        accessKey: String,
        secretKey: String,
        region: String,
        amzDate: String,
    ): AwsSignedHeaders {
        val dateStamp = amzDate.take(8)
        require(dateStamp.length == 8) { "AWS date must use yyyyMMdd'T'HHmmss'Z'" }
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
        val canonicalHeaders = buildString {
            append("host:").append(host.trim()).append('\n')
            append("x-amz-content-sha256:").append(EMPTY_PAYLOAD_HASH).append('\n')
            append("x-amz-date:").append(amzDate).append('\n')
        }
        val canonicalRequest = buildString {
            append(method.uppercase()).append('\n')
            append(encodePath(canonicalUri)).append('\n')
            append(canonicalizeQuery(queryParameters)).append('\n')
            append(canonicalHeaders).append('\n')
            append(signedHeaders).append('\n')
            append(EMPTY_PAYLOAD_HASH)
        }
        val scope = credentialScope(dateStamp, region)
        val signature = calculateSignature(
            secretKey = secretKey,
            dateStamp = dateStamp,
            region = region,
            stringToSign = stringToSign(amzDate, scope, canonicalRequest),
        )
        val authorization = "$ALGORITHM Credential=$accessKey/$scope," +
            "SignedHeaders=$signedHeaders,Signature=$signature"
        return AwsSignedHeaders(
            canonicalRequest = canonicalRequest,
            authorization = authorization,
            amzDate = amzDate,
            payloadHash = EMPTY_PAYLOAD_HASH,
        )
    }

    suspend fun presignUrl(
        scheme: String,
        host: String,
        canonicalUri: String,
        queryParameters: List<Pair<String, String>> = emptyList(),
        accessKey: String,
        secretKey: String,
        region: String,
        amzDate: String,
        expiresSeconds: Int,
    ): String {
        require(expiresSeconds in 1..604_800) { "S3 presigned URLs must expire within seven days" }
        val dateStamp = amzDate.take(8)
        require(dateStamp.length == 8) { "AWS date must use yyyyMMdd'T'HHmmss'Z'" }
        val scope = credentialScope(dateStamp, region)
        val signingParameters = queryParameters + listOf(
            "X-Amz-Algorithm" to ALGORITHM,
            "X-Amz-Credential" to "$accessKey/$scope",
            "X-Amz-Date" to amzDate,
            "X-Amz-Expires" to expiresSeconds.toString(),
            "X-Amz-SignedHeaders" to "host",
        )
        val canonicalQuery = canonicalizeQuery(signingParameters)
        val encodedPath = encodePath(canonicalUri)
        val canonicalRequest = buildString {
            append("GET\n")
            append(encodedPath).append('\n')
            append(canonicalQuery).append('\n')
            append("host:").append(host.trim()).append("\n\n")
            append("host\n")
            append(UNSIGNED_PAYLOAD)
        }
        val signature = calculateSignature(
            secretKey = secretKey,
            dateStamp = dateStamp,
            region = region,
            stringToSign = stringToSign(amzDate, scope, canonicalRequest),
        )
        return "${scheme.lowercase()}://$host$encodedPath?$canonicalQuery&X-Amz-Signature=$signature"
    }

    private fun credentialScope(dateStamp: String, region: String): String =
        "$dateStamp/$region/$SERVICE/$TERMINATOR"

    private suspend fun stringToSign(amzDate: String, scope: String, canonicalRequest: String): String =
        "$ALGORITHM\n$amzDate\n$scope\n${sha256(canonicalRequest.encodeToByteArray()).toHex()}"

    private suspend fun calculateSignature(
        secretKey: String,
        dateStamp: String,
        region: String,
        stringToSign: String,
    ): String {
        val dateKey = hmac(("AWS4$secretKey").encodeToByteArray(), dateStamp)
        val regionKey = hmac(dateKey, region)
        val serviceKey = hmac(regionKey, SERVICE)
        val signingKey = hmac(serviceKey, TERMINATOR)
        return hmac(signingKey, stringToSign).toHex()
    }

    private suspend fun sha256(value: ByteArray): ByteArray = sha256.hash(value)

    private suspend fun hmac(keyBytes: ByteArray, value: String): ByteArray {
        val key = hmac.keyDecoder(SHA256)
            .decodeFromByteArray(HMAC.Key.Format.RAW, keyBytes)
        return key.signatureGenerator().generateSignature(value.encodeToByteArray())
    }

    internal fun canonicalizeQuery(parameters: List<Pair<String, String>>): String =
        parameters
            .map { (name, value) -> encodeQueryComponent(name) to encodeQueryComponent(value) }
            .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
            .joinToString("&") { (name, value) -> "$name=$value" }

    internal fun encodePath(path: String): String {
        val normalized = if (path.startsWith('/')) path else "/$path"
        return encode(normalized, preserveSlash = true)
    }

    private fun encodeQueryComponent(value: String): String = encode(value, preserveSlash = false)

    private fun encode(value: String, preserveSlash: Boolean): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val code = byte.toInt() and 0xff
            val unreserved = code in 'A'.code..'Z'.code ||
                code in 'a'.code..'z'.code ||
                code in '0'.code..'9'.code ||
                code == '-'.code || code == '.'.code || code == '_'.code || code == '~'.code
            when {
                unreserved -> append(code.toChar())
                preserveSlash && code == '/'.code -> append('/')
                else -> {
                    append('%')
                    append(HEX[code ushr 4].uppercaseChar())
                    append(HEX[code and 0x0f].uppercaseChar())
                }
            }
        }
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        this@toHex.forEach { byte ->
            val code = byte.toInt() and 0xff
            append(HEX[code ushr 4])
            append(HEX[code and 0x0f])
        }
    }
}
