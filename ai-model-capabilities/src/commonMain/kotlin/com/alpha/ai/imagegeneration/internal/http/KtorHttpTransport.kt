@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.alpha.ai.imagegeneration.internal.http

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.takeFrom
import io.ktor.utils.io.readAvailable
import kotlin.concurrent.atomics.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Buffer

/** The supplied client must disable automatic redirects and retries. */
internal class KtorHttpTransport(private val client: HttpClient) : HttpTransport {
    override fun prepare(request: HttpRequest, limitBytes: Long): PreparedHttpCall {
        if (limitBytes < 0) throw ImageGenerationTransportException("Invalid response limit")
        val snapshot = request.copy(body = request.body?.copyOf())
        return object : PreparedHttpCall {
            private val started = AtomicBoolean(false)
            override fun start(): RunningHttpCall {
                if (!started.compareAndSet(false, true)) throw ImageGenerationTransportException("HTTP call already started")
                val scope = CoroutineScope(Dispatchers.Default)
                val pending = scope.async { execute(snapshot, limitBytes) }
                return object : RunningHttpCall {
                    private val awaited = AtomicBoolean(false)
                    override suspend fun await(): HttpResponse {
                        if (!awaited.compareAndSet(false, true)) throw ImageGenerationTransportException("HTTP call already awaited")
                        try {
                            return pending.await()
                        } catch (cancelled: CancellationException) {
                            currentCoroutineContext().ensureActive()
                            throw ImageGenerationTransportException("HTTP request failed")
                        } finally {
                            scope.cancel()
                        }
                    }
                    override fun cancelIfActive() { scope.cancel() }
                }
            }
        }
    }

    private suspend fun execute(original: HttpRequest, limitBytes: Long): HttpResponse {
        try {
            var nextUrl = original.url
            repeat(6) { redirects ->
                currentCoroutineContext().ensureActive()
                var redirect: Url? = null
                val result = client.prepareRequest {
                    url.takeFrom(nextUrl)
                    method = HttpMethod(original.method)
                    original.headers.forEach { (name, value) -> headers.append(name, value) }
                    original.body?.let { setBody(it) }
                }.execute { response ->
                    if (response.status.value in REDIRECT_CODES) {
                        if (redirects == 5) throw ImageGenerationTransportException("Too many redirects")
                        val location = response.headers["Location"] ?: throw ImageGenerationTransportException("Invalid redirect")
                        val candidate = resolveRedirectUrl(nextUrl, location)
                        if (candidate.user != null || candidate.password != null || !sameOrigin(original.url, candidate)) {
                            throw ImageGenerationTransportException("Redirect origin rejected")
                        }
                        redirect = candidate
                        null
                    } else {
                        val channel = response.bodyAsChannel()
                        val body = Buffer()
                        val chunk = ByteArray(8192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val remaining = limitBytes - body.size
                            val probe = minOf(chunk.size.toLong(), if (remaining == Long.MAX_VALUE) remaining else remaining + 1).toInt()
                            val read = channel.readAvailable(chunk, 0, probe)
                            if (read == -1) break
                            body.write(chunk, 0, read)
                            if (body.size > limitBytes) throw ImageGenerationTransportException("Response exceeds size limit")
                        }
                        HttpResponse(
                            response.status.value,
                            response.headers.entries().associate { it.key.lowercase() to it.value.last() },
                            body.readByteArray(),
                        )
                    }
                }
                if (result != null) return result
                nextUrl = redirect ?: throw ImageGenerationTransportException("Invalid redirect")
            }
            throw ImageGenerationTransportException("Too many redirects")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ImageGenerationTransportException) {
            throw error
        } catch (_: Exception) {
            throw ImageGenerationTransportException("HTTP request failed")
        }
    }

    private fun sameOrigin(first: Url, candidate: Url): Boolean =
        first.protocol.name.equals(candidate.protocol.name, ignoreCase = true) &&
            first.host.equals(candidate.host, ignoreCase = true) && first.port == candidate.port

    private companion object { val REDIRECT_CODES = setOf(301, 302, 303, 307, 308) }
}
