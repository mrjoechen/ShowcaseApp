package com.alpha.showcase.common.mtphoto

import com.alpha.showcase.api.mtphoto.MTPhotoApi
import com.alpha.showcase.api.mtphoto.MTPhotoLoginRequest
import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO_AUTH_TYPE_API_KEY
import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO_AUTH_TYPE_PASSWORD
import com.alpha.showcase.common.networkfile.storage.remote.MTPhotoSource
import com.alpha.showcase.common.networkfile.util.RConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

data class MTPhotoAuthSession(
    val authCode: String,
    val headerName: String,
    val headerValue: String,
)

class MTPhotoAuthManager(
    private val authLoader: suspend (MTPhotoSource) -> MTPhotoAuthSession = ::loadMTPhotoAuthSession,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private data class CachedSession(
        val session: MTPhotoAuthSession,
        val createdAtMillis: Long,
    )

    private val mutex = Mutex()
    private val sources = mutableMapOf<String, MTPhotoSource>()
    private val sessions = mutableMapOf<String, CachedSession>()

    suspend fun register(source: MTPhotoSource): String {
        val key = sourceKey(source)
        mutex.withLock {
            sources[key] = source
        }
        return key
    }

    fun sourceKey(source: MTPhotoSource): String {
        val credentialIdentity = when (source.authType) {
            MTPHOTO_AUTH_TYPE_API_KEY -> source.apiKey.orEmpty()
            MTPHOTO_AUTH_TYPE_PASSWORD -> "${source.user.orEmpty()}:${source.pass.orEmpty()}"
            else -> "unsupported:${source.authType}"
        }
        val identity = listOf(
            source.url.trim().trimEnd('/'),
            source.authType,
            credentialIdentity,
        ).joinToString("|")
        return "mtphoto-${stableFingerprint(identity)}"
    }

    suspend fun getAuthForKey(sourceKey: String): MTPhotoAuthSession = mutex.withLock {
        val now = nowMillis()
        sessions[sourceKey]
            ?.takeIf { now - it.createdAtMillis < AUTH_TTL_MILLIS }
            ?.let { return@withLock it.session }

        val source = sources[sourceKey]
            ?: throw IllegalStateException("MTPhoto source is not registered: $sourceKey")
        val session = authLoader(source)
        sessions[sourceKey] = CachedSession(session, now)
        session
    }

    suspend fun baseUrlForKey(sourceKey: String): String = mutex.withLock {
        sources[sourceKey]?.url
            ?: throw IllegalStateException("MTPhoto source is not registered: $sourceKey")
    }

    suspend fun invalidate(sourceKey: String) {
        mutex.withLock {
            sessions.remove(sourceKey)
        }
    }

    companion object {
        const val AUTH_TTL_MILLIS: Long = 23L * 60L * 60L * 1_000L
    }
}

object MTPhotoRuntime {
    val authManager = MTPhotoAuthManager()
}

private suspend fun loadMTPhotoAuthSession(source: MTPhotoSource): MTPhotoAuthSession {
    val api = MTPhotoApi()
    return when (source.authType) {
        MTPHOTO_AUTH_TYPE_API_KEY -> {
            val apiKey = RConfig.decryptAsync(source.apiKey.orEmpty())
            require(apiKey.isNotBlank()) { "MTPhoto API key is required" }
            val authCode = api.getAuthCode(source.url, apiKey).authCode
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("MTPhoto auth code is empty")
            MTPhotoAuthSession(authCode, "x-api-key", apiKey)
        }

        MTPHOTO_AUTH_TYPE_PASSWORD -> {
            val username = source.user.orEmpty()
            val password = RConfig.decryptAsync(source.pass.orEmpty())
            require(username.isNotBlank()) { "MTPhoto username is required" }
            require(password.isNotBlank()) { "MTPhoto password is required" }
            val login = api.login(source.url, MTPhotoLoginRequest(username, password))
            val authCode = login.authCode
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("MTPhoto auth code is empty")
            val accessToken = login.accessToken
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("MTPhoto access token is empty")
            MTPhotoAuthSession(
                authCode = authCode,
                headerName = "Authorization",
                headerValue = "Bearer $accessToken",
            )
        }

        else -> throw IllegalArgumentException("Unsupported MTPhoto auth type: ${source.authType}")
    }
}

private fun stableFingerprint(value: String): String {
    var hash = 0x811c9dc5u
    value.encodeToByteArray().forEach { byte ->
        hash = hash xor byte.toUByte().toUInt()
        hash *= 0x01000193u
    }
    return hash.toString(16).padStart(8, '0')
}
