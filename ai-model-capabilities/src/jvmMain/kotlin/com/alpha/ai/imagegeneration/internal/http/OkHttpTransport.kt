package com.alpha.ai.imagegeneration.internal.http

import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal class OkHttpTransport(
    private val callFactory: Call.Factory = DefaultCallFactory,
) : HttpTransport {
    override fun prepare(request: HttpRequest, limitBytes: Long): PreparedHttpCall {
        if (limitBytes < 0) throw ImageGenerationTransportException("Invalid response limit")
        return PreparedOkHttpCall(request, limitBytes)
    }

    private inner class PreparedOkHttpCall(
        private val request: HttpRequest,
        private val limitBytes: Long,
    ) : PreparedHttpCall {
        private val started = AtomicBoolean(false)

        override fun start(): RunningHttpCall {
            if (!started.compareAndSet(false, true)) throw ImageGenerationTransportException("HTTP call already started")
            val activeCall = ActiveCall()
            try {
                val initialRequest = request.toOkHttpRequest()
                val first = EnqueuedCall(callFactory.newCall(initialRequest), activeCall)
                first.enqueue()
                return RunningOkHttpCall(request, initialRequest.url, limitBytes, first, activeCall)
            } catch (_: Throwable) {
                runCatching { activeCall.cancel() }
                throw ImageGenerationTransportException("Unable to start HTTP call")
            }
        }
    }

    private inner class RunningOkHttpCall(
        private val originalRequest: HttpRequest,
        private val originalUrl: HttpUrl,
        private val limitBytes: Long,
        private var next: EnqueuedCall,
        private val activeCall: ActiveCall,
    ) : RunningHttpCall {
        private val awaited = AtomicBoolean(false)

        override suspend fun await(): HttpResponse = suspendCancellableCoroutine { suspended ->
            if (!awaited.compareAndSet(false, true)) throw ImageGenerationTransportException("HTTP call already awaited")
            suspended.invokeOnCancellation { activeCall.cancel() }
            consume(next, redirects = 0, suspended)
        }

        override fun cancelIfActive() {
            activeCall.cancel()
        }

        private fun consume(
            enqueued: EnqueuedCall,
            redirects: Int,
            suspended: CancellableContinuation<HttpResponse>,
        ) {
            enqueued.whenComplete { completed ->
                completed.fold(
                    onSuccess = { response -> processResponse(response, redirects, suspended) },
                    onFailure = { throwable ->
                        if (suspended.isActive) suspended.resumeWithException(throwable)
                    },
                )
            }
        }

        private fun processResponse(
            response: Response,
            redirects: Int,
            suspended: CancellableContinuation<HttpResponse>,
        ) {
            response.use {
                if (!suspended.isActive) return
                if (response.code !in RedirectCodes) {
                    val body = try {
                        BoundedBodyReader.read(response.body.source(), limitBytes)
                    } catch (error: ImageGenerationTransportException) {
                        if (suspended.isActive) suspended.resumeWithException(error)
                        return
                    } catch (_: IOException) {
                        if (suspended.isActive) {
                            suspended.resumeWithException(ImageGenerationTransportException("Unable to read HTTP response"))
                        }
                        return
                    } catch (_: RuntimeException) {
                        if (suspended.isActive) {
                            suspended.resumeWithException(ImageGenerationTransportException("Unable to read HTTP response"))
                        }
                        return
                    }
                    if (!suspended.isActive) return
                    suspended.resume(
                        HttpResponse(
                            status = response.code,
                            headers = response.headers.names()
                                .associate { name -> name.lowercase() to response.header(name).orEmpty() },
                            body = body,
                        ),
                    )
                    return
                }

                if (redirects >= MaxRedirects) {
                    suspended.resumeWithException(ImageGenerationTransportException("Too many redirects"))
                    return
                }
                val redirectUrl = response.header("Location")?.let(response.request.url::resolve)
                if (redirectUrl == null) {
                    suspended.resumeWithException(ImageGenerationTransportException("Invalid redirect"))
                    return
                }
                if (!sameOrigin(originalUrl, redirectUrl)) {
                    suspended.resumeWithException(ImageGenerationTransportException("Redirect origin rejected"))
                    return
                }
                if (!suspended.isActive) return

                val followUp = try {
                    EnqueuedCall(callFactory.newCall(originalRequest.withUrl(redirectUrl)), activeCall).also { it.enqueue() }
                } catch (_: Throwable) {
                    if (suspended.isActive) {
                        suspended.resumeWithException(ImageGenerationTransportException("Unable to start HTTP call"))
                    }
                    return
                }
                next = followUp
                consume(followUp, redirects + 1, suspended)
            }
        }
    }

    private class EnqueuedCall(
        private val call: Call,
        private val activeCall: ActiveCall,
    ) {
        private val lock = Any()
        private var result: Result<Response>? = null
        private var consumer: ((Result<Response>) -> Unit)? = null
        private var cancelled = false

        fun enqueue() {
            if (!activeCall.publish(this)) {
                complete(Result.failure(ImageGenerationTransportException("HTTP request failed")))
                return
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    complete(Result.failure(ImageGenerationTransportException("HTTP request failed")))
                }

                override fun onResponse(call: Call, response: Response) {
                    complete(Result.success(response))
                }
            })
        }

        fun whenComplete(onComplete: (Result<Response>) -> Unit) {
            val completed: Result<Response>?
            synchronized(lock) {
                completed = result ?: if (cancelled) cancellationFailure() else null
                // Taking the result transfers response ownership to the consumer's response.use.
                result = null
                if (completed == null) consumer = onComplete
            }
            completed?.let(onComplete)
        }

        fun cancel() {
            val unconsumedResponse = synchronized(lock) {
                cancelled = true
                result?.getOrNull().also {
                    if (result != null) result = cancellationFailure()
                }
            }
            try {
                call.cancel()
            } finally {
                runCatching { unconsumedResponse?.close() }
            }
        }

        private fun complete(completed: Result<Response>) {
            val delivered: Result<Response>
            val discardResponse: Boolean
            val onComplete = synchronized(lock) {
                discardResponse = cancelled
                delivered = if (discardResponse) cancellationFailure() else completed
                result = delivered
                consumer.also {
                    if (it != null) result = null
                    consumer = null
                }
            }
            if (discardResponse) runCatching { completed.getOrNull()?.close() }
            onComplete?.invoke(delivered)
        }

        private fun cancellationFailure(): Result<Response> =
            Result.failure(ImageGenerationTransportException("HTTP request failed"))
    }

    private class ActiveCall {
        private val current = AtomicReference<EnqueuedCall?>(null)
        private val cancelled = AtomicBoolean(false)

        fun publish(call: EnqueuedCall): Boolean {
            if (cancelled.get()) {
                call.cancel()
                return false
            }
            current.set(call)
            // Cancellation is durable: publishing after its handler ran cannot revive the request.
            if (cancelled.get()) {
                current.compareAndSet(call, null)
                call.cancel()
                return false
            }
            return true
        }

        fun cancel() {
            cancelled.set(true)
            current.getAndSet(null)?.cancel()
        }
    }

    private fun HttpRequest.toOkHttpRequest(): Request = Request.Builder()
        .url(url.toString())
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .method(method, if (method == "GET" || method == "HEAD") null else (body ?: ByteArray(0)).toRequestBody())
        .build()

    private fun HttpRequest.withUrl(url: HttpUrl): Request = copy(url = io.ktor.http.Url(url.toString())).toOkHttpRequest()

    private fun sameOrigin(first: HttpUrl, candidate: HttpUrl): Boolean =
        first.scheme.equals(candidate.scheme, ignoreCase = true) &&
            first.host.equals(candidate.host, ignoreCase = true) &&
            first.port == candidate.port

    private companion object {
        val DefaultCallFactory: Call.Factory = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.MINUTES)
            .readTimeout(10, TimeUnit.MINUTES)
            .callTimeout(10, TimeUnit.MINUTES)
            .build()
        val RedirectCodes = setOf(301, 302, 303, 307, 308)
        const val MaxRedirects = 5
    }
}
