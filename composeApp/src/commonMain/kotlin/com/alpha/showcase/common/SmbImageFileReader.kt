@file:OptIn(kotlin.time.ExperimentalTime::class)
package com.alpha.showcase.common

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.util.RConfig
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import okio.Path
import kotlin.time.Clock

/** Testable file-based bridge protocol shared by the iOS fetcher and desktop contract tests. */
internal class SmbImageFileReader(
    private val invoke: (String) -> String,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private data class Session(val key: String, val id: String, var usedAt: Long)
    private val mutex = Mutex()
    private val idle = mutableListOf<Session>()

    suspend fun download(file: NetworkFile, destination: Path) {
        val remote = file.remote as? Smb ?: error("SMB source required")
        val key = listOf(remote.id, remote.host, remote.port, remote.user, remote.passwd)
            .joinToString("\u0000").encodeUtf8().sha256().hex()
        val expired = mutex.withLock { idle.filter { now() - it.usedAt > 120_000 }.also { idle.removeAll(it.toSet()) } }
        expired.forEach { close(it.id) }
        var session = mutex.withLock { idle.firstOrNull { it.key == key }?.also { idle.remove(it) } }
        var reusable = false
        try {
            currentCoroutineContext().ensureActive()
            session?.let { cached ->
                // Probe only an exclusively borrowed idle session, before creating any output.
                val valid = try { request(Request("validate", sessionId = cached.id)); true }
                    catch (failure: IllegalStateException) {
                        if (failure is CancellationException) throw failure
                        false
                    }
                if (!valid) { close(cached.id); session = null }
            }
            currentCoroutineContext().ensureActive()
            val active = session ?: Session(key, request(Request("open", host = remote.host, port = remote.port,
                user = remote.user, password = RConfig.decryptBlocking(remote.passwd))).sessionId
                ?: error("SMB bridge returned no session"), now()).also { session = it }
            val path = file.path.substringAfter("://").substringAfter('/').trimStart('/')
            // Native SMB listings already contain literal server paths.
            val share = path.substringBefore('/')
            val filePath = path.substringAfter('/', "")
            require(share.isNotEmpty() && filePath.isNotEmpty()) { "Invalid SMB file path" }
            request(Request("downloadFile", sessionId = active.id, share = share,
                path = filePath, destination = destination.toString()))
            currentCoroutineContext().ensureActive()
            reusable = true
        } finally {
            withContext(NonCancellable) {
                session?.let { owned ->
                    val keep = mutex.withLock {
                        if (reusable && idle.size < 3) { owned.usedAt = now(); idle.add(owned); true } else false
                    }
                    if (!keep) close(owned.id)
                }
            }
        }
    }

    suspend fun closeIdle() {
        mutex.withLock { idle.toList().also { idle.clear() } }.forEach { close(it.id) }
    }

    private fun close(id: String) { runCatching { request(Request("close", sessionId = id)) } }
    private fun request(value: Request): Response {
        val response = json.decodeFromString<Response>(invoke(json.encodeToString(value)))
        check(response.ok) { "SMB image operation failed" }
        return response
    }
    @Serializable
    private data class Request(val action: String, val host: String? = null, val port: Int? = null,
        val user: String? = null, val password: String? = null, val sessionId: String? = null,
        val share: String? = null, val path: String? = null, val destination: String? = null)
    @Serializable
    private data class Response(val ok: Boolean, val sessionId: String? = null)
}
