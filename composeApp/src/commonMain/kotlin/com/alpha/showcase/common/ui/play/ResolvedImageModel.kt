package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import com.alpha.showcase.common.repo.S3_OBJECT_KEY
import com.alpha.showcase.common.repo.SignedS3ObjectUrl
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.encodeUtf8

internal const val S3_SIGNED_URL_REFRESH_WINDOW_MILLIS = 300_000L

internal class ResolvedImageModel(
    initialSignedRequest: SignedS3ObjectUrl,
    val stableKey: Any,
    val cacheKey: String,
    headers: Map<String, String> = emptyMap(),
    private val refreshSignedRequest: suspend () -> SignedS3ObjectUrl,
) {
    private val signedRequestMutex = Mutex()
    private var signedRequest = initialSignedRequest

    internal val headers: Map<String, String> = buildMap {
        headers.forEach { (name, value) ->
            val normalizedName = name.lowercase()
            require(normalizedName !in this) {
                "Duplicate image request header name (case-insensitive): $name"
            }
            put(normalizedName, value)
        }
    }

    internal suspend fun resolveForFetch(nowEpochMillis: Long): SignedS3ObjectUrl =
        signedRequestMutex.withLock {
            val current = signedRequest
            if (nowEpochMillis < current.expiresAtEpochMillis - S3_SIGNED_URL_REFRESH_WINDOW_MILLIS) {
                current
            } else {
                refreshSignedRequest().also { signedRequest = it }
            }
        }

    override fun equals(other: Any?): Boolean =
        other === this || other is ResolvedImageModel &&
            stableKey == other.stableKey &&
            cacheKey == other.cacheKey

    override fun hashCode(): Int {
        var result = stableKey.hashCode()
        result = 31 * result + cacheKey.hashCode()
        return result
    }

    override fun toString(): String =
        "ResolvedImageModel(signedRequest=<redacted>, stableKey=<redacted>, " +
            "cacheKey=<redacted>, headerCount=${headers.size})"
}

internal fun s3NetworkFileCacheKey(file: NetworkFile): String {
    val source = file.remote as S3Source
    val objectKey = file.extra?.get(S3_OBJECT_KEY) ?: file.path
    val etag = file.extra?.get("etag")?.takeIf { it.isNotBlank() }
    val identityParts = buildList {
        add("v1")
        add(source.useSSL.toString())
        add(source.endpoint)
        add(source.region)
        add(source.bucket)
        add(objectKey)
        if (etag != null) {
            add("etag")
            add(etag)
        } else {
            add("metadata")
            add(file.modTime)
            add(file.size.toString())
        }
    }
    val canonicalIdentity = buildString {
        identityParts.forEach { part ->
            append(part.length)
            append(':')
            append(part)
        }
    }
    return "s3:${canonicalIdentity.encodeUtf8().sha256().hex()}"
}
