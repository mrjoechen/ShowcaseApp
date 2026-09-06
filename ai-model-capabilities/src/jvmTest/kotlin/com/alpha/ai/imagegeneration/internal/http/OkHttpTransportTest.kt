package com.alpha.ai.imagegeneration.internal.http

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.buffer

class OkHttpTransportTest {
    @Test
    fun `same origin redirects use canonical IPv6 and IDN hosts`() = runTest {
        listOf(
            Triple("https://[::1]/v1", "/next", "https://[::1]/next"),
            Triple("https://bücher.test/v1", "https://xn--bcher-kva.test/next", "https://xn--bcher-kva.test/next"),
        ).forEach { (initialUrl, location, expectedUrl) ->
            val requests = mutableListOf<okhttp3.Request>()
            val delegate = okhttp3.OkHttpClient()
            val canonicalTransport = OkHttpTransport(callFactory = okhttp3.Call.Factory { request ->
                requests += request
                object : okhttp3.Call by delegate.newCall(request) {
                    override fun enqueue(responseCallback: okhttp3.Callback) {
                        val response = okhttp3.Response.Builder().request(request)
                            .protocol(okhttp3.Protocol.HTTP_1_1).message("OK").body("{}".toResponseBody())
                        if (requests.size == 1) response.code(302).header("Location", location)
                        else response.code(200)
                        responseCallback.onResponse(this, response.build())
                    }
                }
            })
            val result = canonicalTransport.prepare(
                HttpRequest.get(io.ktor.http.Url(initialUrl), mapOf("Authorization" to "Bearer private")), 16,
            ).start().await()
            assertEquals(200, result.status)
            assertEquals(2, requests.size)
            assertEquals(expectedUrl, requests.last().url.toString())
            assertEquals("Bearer private", requests.last().header("Authorization"))
        }
    }

    private val origin = MockWebServer()
    private val target = MockWebServer()
    private val transport = OkHttpTransport()

    @AfterTest
    fun tearDown() {
        origin.close()
        target.close()
    }

    @Test
    fun `same origin redirect preserves request and returns bounded response`() = runTest {
        origin.start()
        origin.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/next"))
        origin.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val response = transport.prepare(
            HttpRequest.post(io.ktor.http.Url(origin.url("/v1").toString()), headers = mapOf("Authorization" to "Bearer secret"), body = "{}".encodeToByteArray()),
            limitBytes = 16,
        ).start().await()

        assertEquals(200, response.status)
        assertContentEquals("{}".encodeToByteArray(), response.body)
        assertEquals("Bearer secret", origin.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer secret", origin.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `start enqueues before returning and is single use`() = runTest {
        origin.start()
        origin.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val prepared = transport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/v1").toString())), limitBytes = 16)

        val running = prepared.start()

        assertEquals("GET", origin.takeRequest(1, TimeUnit.SECONDS)?.method)
        assertFailsWith<ImageGenerationTransportException> { prepared.start() }
        assertEquals(200, running.await().status)
    }

    @Test
    fun `synchronous enqueue failure is sanitized as a transport error`() {
        val failingTransport = OkHttpTransport(callFactory = okhttp3.Call.Factory { request ->
            object : okhttp3.Call by okhttp3.OkHttpClient().newCall(request) {
                override fun enqueue(responseCallback: okhttp3.Callback) {
                    throw IllegalStateException("Authorization: Bearer secret")
                }
            }
        })

        assertFailsWith<ImageGenerationTransportException> {
            failingTransport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/v1").toString())), limitBytes = 16).start()
        }
    }

    @Test
    fun `synchronous enqueue failure stays sanitized when cancellation cleanup throws`() {
        val failingTransport = OkHttpTransport(callFactory = okhttp3.Call.Factory { request ->
            object : okhttp3.Call by okhttp3.OkHttpClient().newCall(request) {
                override fun enqueue(responseCallback: okhttp3.Callback) {
                    throw IllegalStateException("Authorization: Bearer enqueue-secret")
                }

                override fun cancel() {
                    throw IllegalStateException("Authorization: Bearer cleanup-secret")
                }
            }
        })

        val failure = assertFailsWith<ImageGenerationTransportException> {
            failingTransport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/v1").toString())), limitBytes = 16).start()
        }
        assertEquals("Unable to start HTTP call", failure.message)
        assertNull(failure.cause)
    }

    @Test
    fun `request construction failure is sanitized as a transport error`() {
        assertFailsWith<ImageGenerationTransportException> {
            transport.prepare(HttpRequest(method = "", url = io.ktor.http.Url(origin.url("/v1").toString())), limitBytes = 16).start()
        }
    }

    @Test
    fun `new call failure is sanitized as a transport error`() {
        val failingTransport = OkHttpTransport(callFactory = okhttp3.Call.Factory {
            throw IllegalStateException("Authorization: Bearer secret")
        })

        assertFailsWith<ImageGenerationTransportException> {
            failingTransport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/v1").toString())), limitBytes = 16).start()
        }
    }

    @Test
    fun `callback IO failure is a transport error rather than cancellation`() = runTest {
        val failingTransport = OkHttpTransport(callFactory = okhttp3.Call.Factory { request ->
            object : okhttp3.Call by okhttp3.OkHttpClient().newCall(request) {
                override fun enqueue(responseCallback: okhttp3.Callback) {
                    responseCallback.onFailure(this, IOException("connection failed"))
                }
            }
        })

        assertFailsWith<ImageGenerationTransportException> {
            failingTransport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/v1").toString())), limitBytes = 16).start().await()
        }
    }

    @Test
    fun `response body IO failure is sanitized as a transport error`() = runTest {
        val failingTransport = OkHttpTransport(callFactory = okhttp3.Call.Factory { request ->
            object : okhttp3.Call by okhttp3.OkHttpClient().newCall(request) {
                override fun enqueue(responseCallback: okhttp3.Callback) {
                    responseCallback.onResponse(
                        this,
                        okhttp3.Response.Builder()
                            .request(request)
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(object : okhttp3.ResponseBody() {
                                override fun contentLength() = -1L
                                override fun contentType() = null
                                override fun source() = object : okio.Source {
                                    override fun read(sink: Buffer, byteCount: Long): Long =
                                        throw IOException("response bytes include secret")

                                    override fun timeout() = okio.Timeout.NONE
                                    override fun close() = Unit
                                }.buffer()
                            })
                            .build(),
                    )
                }
            }
        })

        assertFailsWith<ImageGenerationTransportException> {
            failingTransport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/v1").toString())), limitBytes = 16).start().await()
        }
    }

    @Test
    fun `cross origin redirect never forwards credentials`() = runTest {
        origin.start()
        target.start()
        origin.enqueue(MockResponse().setResponseCode(302).addHeader("Location", target.url("/steal").toString()))

        assertFailsWith<ImageGenerationTransportException> {
            transport.prepare(
                HttpRequest.post(io.ktor.http.Url(origin.url("/v1").toString()), headers = mapOf("Authorization" to "Bearer secret"), body = "{}".encodeToByteArray()),
                limitBytes = ResponseLimits().jsonBytes,
            ).start().await()
        }

        assertNull(target.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `scheme changing redirect is rejected before credentials are forwarded`() = runTest {
        origin.start()
        origin.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://127.0.0.1:${origin.port}/steal"))

        assertFailsWith<ImageGenerationTransportException> {
            transport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/v1").toString()), mapOf("Authorization" to "Bearer secret")), 16).start().await()
        }

        assertEquals("Bearer secret", origin.takeRequest().getHeader("Authorization"))
        assertNull(origin.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `host changing redirect is rejected before credentials are forwarded`() = runTest {
        origin.start()
        origin.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "http://different.invalid:${origin.port}/steal"))

        assertFailsWith<ImageGenerationTransportException> {
            transport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/v1").toString()), mapOf("Authorization" to "Bearer secret")), 16).start().await()
        }

        assertEquals("Bearer secret", origin.takeRequest().getHeader("Authorization"))
        assertNull(origin.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `five same origin redirects are followed`() = runTest {
        origin.start()
        repeat(5) { origin.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/next")) }
        origin.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        assertEquals(200, transport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/v1").toString())), 16).start().await().status)
    }

    @Test
    fun `sixth same origin redirect is rejected`() = runTest {
        origin.start()
        repeat(6) { origin.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/next")) }

        assertFailsWith<ImageGenerationTransportException> {
            transport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/v1").toString())), 16).start().await()
        }
    }

    @Test
    fun `redirect follow up creation failure is sanitized as a transport error`() = runTest {
        var calls = 0
        val failingTransport = OkHttpTransport(callFactory = okhttp3.Call.Factory { request ->
            calls++
            if (calls == 2) throw IllegalStateException("Authorization: Bearer secret")
            object : okhttp3.Call by okhttp3.OkHttpClient().newCall(request) {
                override fun enqueue(responseCallback: okhttp3.Callback) {
                    responseCallback.onResponse(
                        this,
                        okhttp3.Response.Builder()
                            .request(request)
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(302)
                            .message("Found")
                            .addHeader("Location", "/next")
                            .body("".toResponseBody())
                            .build(),
                    )
                }
            }
        })

        assertFailsWith<ImageGenerationTransportException> {
            failingTransport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/v1").toString())), 16).start().await()
        }
    }

    @Test
    fun `rejects a response body larger than its limit`() = runTest {
        origin.start()
        origin.enqueue(MockResponse().setResponseCode(200).setBody("12345"))

        assertFailsWith<ImageGenerationTransportException> {
            transport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/v1").toString())), limitBytes = 4).start().await()
        }
    }

    @Test
    fun `large valid response limit does not overflow bounded reader`() = runTest {
        origin.start()
        origin.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val response = transport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/v1").toString())), limitBytes = Long.MAX_VALUE).start().await()

        assertContentEquals("{}".encodeToByteArray(), response.body)
    }

    @Test
    fun `disconnected POST is dispatched at most once`() = runTest {
        origin.start()
        origin.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertFailsWith<ImageGenerationTransportException> {
            transport.prepare(HttpRequest.post(io.ktor.http.Url(origin.url("/v1").toString()), body = "{}".encodeToByteArray()), 16).start().await()
        }

        val first = origin.takeRequest(1, TimeUnit.SECONDS)
        val second = origin.takeRequest(200, TimeUnit.MILLISECONDS)
        assertNull(second)
        // A disconnect can occur before the server parses a request; if it did parse one, there must be just one.
        first
    }

    @Test
    fun `await cancellation is the only path that exposes cancellation`() = runTest {
        origin.start()
        origin.enqueue(MockResponse().setResponseCode(200).setBody("{}").setBodyDelay(5, TimeUnit.SECONDS))
        val running = transport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/slow").toString())), 16).start()
        val awaiting = async { running.await() }

        awaiting.cancel()
        assertFailsWith<kotlinx.coroutines.CancellationException> { awaiting.await() }
    }

    @Test
    fun `cancelling while a redirect call is being created cannot leave its request active`() = runTest {
        val creatingRedirect = CountDownLatch(1)
        val releaseFactory = CountDownLatch(1)
        val redirectEnqueued = AtomicBoolean(false)
        val redirectBodyClosed = AtomicBoolean(false)
        val followUp = okhttp3.OkHttpClient().newCall(
            okhttp3.Request.Builder().url(origin.url("/next")).build(),
        )
        val fakeTransport = OkHttpTransport(callFactory = okhttp3.Call.Factory { request ->
            if (request.url.encodedPath == "/next") {
                creatingRedirect.countDown()
                check(releaseFactory.await(5, TimeUnit.SECONDS))
                object : okhttp3.Call by followUp {
                    override fun enqueue(responseCallback: okhttp3.Callback) {
                        redirectEnqueued.set(true)
                    }
                }
            } else {
                object : okhttp3.Call by okhttp3.OkHttpClient().newCall(request) {
                    override fun enqueue(responseCallback: okhttp3.Callback) {
                        responseCallback.onResponse(
                            this,
                            okhttp3.Response.Builder()
                                .request(request)
                                .protocol(okhttp3.Protocol.HTTP_1_1)
                                .code(302)
                                .message("Found")
                                .addHeader("Location", "/next")
                                .body(object : okhttp3.ResponseBody() {
                                    private val content = object : okio.ForwardingSource(Buffer()) {
                                        override fun close() {
                                            redirectBodyClosed.set(true)
                                            super.close()
                                        }
                                    }.buffer()

                                    override fun contentLength() = 0L
                                    override fun contentType() = null
                                    override fun source() = content
                                })
                                .build(),
                        )
                    }
                }
            }
        })
        val running = fakeTransport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/start").toString())), 16).start()
        val awaiting = async(Dispatchers.IO) { running.await() }

        try {
            assertTrue(creatingRedirect.await(1, TimeUnit.SECONDS))
            // Cancellation returns while Call.Factory remains blocked on another real thread.
            awaiting.cancel()
            releaseFactory.countDown()
            awaiting.join()

            assertTrue(!redirectEnqueued.get() || followUp.isCanceled(), "Redirect survived cancellation")
            assertTrue(redirectBodyClosed.get(), "Redirect response was not closed")
            assertFailsWith<CancellationException> { awaiting.await() }
        } finally {
            releaseFactory.countDown()
            running.cancelIfActive()
            awaiting.cancel()
        }
    }

    @Test
    fun `cancelling before await closes an already received response`() = runTest {
        val bodyClosed = AtomicBoolean(false)
        val fakeTransport = OkHttpTransport(callFactory = okhttp3.Call.Factory { request ->
            object : okhttp3.Call by okhttp3.OkHttpClient().newCall(request) {
                override fun enqueue(responseCallback: okhttp3.Callback) {
                    responseCallback.onResponse(
                        this,
                        okhttp3.Response.Builder()
                            .request(request)
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(object : okhttp3.ResponseBody() {
                                private val content = object : okio.ForwardingSource(Buffer().writeUtf8("{}")) {
                                    override fun close() {
                                        bodyClosed.set(true)
                                        super.close()
                                        throw IllegalStateException("private response-close detail")
                                    }
                                }.buffer()

                                override fun contentLength() = 2L
                                override fun contentType() = null
                                override fun source() = content
                            })
                            .build(),
                    )
                }
            }
        })
        val running = fakeTransport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/immediate").toString())), 16).start()

        running.cancelIfActive()

        assertTrue(bodyClosed.get(), "Response received before await was leaked")
        assertFailsWith<ImageGenerationTransportException> { running.await() }
    }

    @Test
    fun `explicit cancellation during redirect enqueue closes its late response`() = runTest {
        val enqueueStarted = CountDownLatch(1)
        val releaseEnqueue = CountDownLatch(1)
        val responseClosed = AtomicBoolean(false)
        val followUp = okhttp3.OkHttpClient().newCall(
            okhttp3.Request.Builder().url(origin.url("/next")).build(),
        )
        val fakeTransport = OkHttpTransport(callFactory = okhttp3.Call.Factory { request ->
            val isRedirect = request.url.encodedPath == "/next"
            object : okhttp3.Call by if (isRedirect) followUp else okhttp3.OkHttpClient().newCall(request) {
                override fun enqueue(responseCallback: okhttp3.Callback) {
                    val response = okhttp3.Response.Builder()
                        .request(request)
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .message("OK")
                    if (isRedirect) {
                        enqueueStarted.countDown()
                        check(releaseEnqueue.await(5, TimeUnit.SECONDS))
                        response.code(200).body(object : okhttp3.ResponseBody() {
                            private val content = object : okio.ForwardingSource(Buffer().writeUtf8("{}")) {
                                override fun close() {
                                    responseClosed.set(true)
                                    super.close()
                                }
                            }.buffer()

                            override fun contentLength() = 2L
                            override fun contentType() = null
                            override fun source() = content
                        })
                    } else {
                        response.code(302).addHeader("Location", "/next").body("".toResponseBody())
                    }
                    responseCallback.onResponse(this, response.build())
                }
            }
        })
        val running = fakeTransport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/start").toString())), 16).start()
        // Capture the expected transport error inside the child so it cannot cancel runTest's parent.
        val awaiting = async(Dispatchers.IO) { runCatching { running.await() } }

        try {
            assertTrue(enqueueStarted.await(1, TimeUnit.SECONDS))
            running.cancelIfActive()
            assertTrue(followUp.isCanceled(), "Cancellation waited for enqueue to return")
            releaseEnqueue.countDown()

            assertTrue(awaiting.await().exceptionOrNull() is ImageGenerationTransportException)
            assertTrue(responseClosed.get(), "Late response after cancellation was leaked")
        } finally {
            releaseEnqueue.countDown()
            running.cancelIfActive()
            awaiting.cancel()
        }
    }

    @Test
    fun `response arriving after cancellation is discarded without exposing close failures`() = runTest {
        lateinit var pendingCallback: okhttp3.Callback
        lateinit var pendingCall: okhttp3.Call
        val bodyClosed = AtomicBoolean(false)
        val fakeTransport = OkHttpTransport(callFactory = okhttp3.Call.Factory { request ->
            object : okhttp3.Call by okhttp3.OkHttpClient().newCall(request) {
                override fun enqueue(responseCallback: okhttp3.Callback) {
                    pendingCall = this
                    pendingCallback = responseCallback
                }
            }
        })
        val running = fakeTransport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/late").toString())), 16).start()
        running.cancelIfActive()
        val response = okhttp3.Response.Builder()
            .request(pendingCall.request())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(object : okhttp3.ResponseBody() {
                private val content = object : okio.ForwardingSource(Buffer()) {
                    override fun close() {
                        bodyClosed.set(true)
                        throw IllegalStateException("private late-response-close detail")
                    }
                }.buffer()

                override fun contentLength() = 0L
                override fun contentType() = null
                override fun source() = content
            })
            .build()

        pendingCallback.onResponse(pendingCall, response)

        assertTrue(bodyClosed.get(), "Cancelled response was not closed")
        assertFailsWith<ImageGenerationTransportException> { running.await() }
    }

    @Test
    fun `cancellation after headers cancels a call stalled while reading its body`() = runTest {
        val bodyReadStarted = CountDownLatch(1)
        val unblockBody = CountDownLatch(1)
        val callCancelled = CountDownLatch(1)
        val fakeTransport = OkHttpTransport(callFactory = okhttp3.Call.Factory { request ->
            val delegate = okhttp3.OkHttpClient().newCall(request)
            object : okhttp3.Call by delegate {
                override fun enqueue(responseCallback: okhttp3.Callback) {
                    responseCallback.onResponse(
                        this,
                        okhttp3.Response.Builder()
                            .request(request)
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(object : okhttp3.ResponseBody() {
                                override fun contentLength() = -1L
                                override fun contentType() = null
                                override fun source() = object : okio.Source {
                                    override fun read(sink: Buffer, byteCount: Long): Long {
                                        bodyReadStarted.countDown()
                                        unblockBody.await(5, TimeUnit.SECONDS)
                                        throw IOException("cancelled stalled body")
                                    }

                                    override fun timeout() = okio.Timeout.NONE
                                    override fun close() {
                                        unblockBody.countDown()
                                    }
                                }.buffer()
                            })
                            .build(),
                    )
                }

                override fun cancel() {
                    callCancelled.countDown()
                    unblockBody.countDown()
                    delegate.cancel()
                }
            }
        })
        val running = fakeTransport.prepare(HttpRequest.get(io.ktor.http.Url(origin.url("/stalled-body").toString())), 16).start()
        val awaiting = async(Dispatchers.IO) { running.await() }

        try {
            assertTrue(bodyReadStarted.await(1, TimeUnit.SECONDS))
            awaiting.cancel()
            assertTrue(callCancelled.await(1, TimeUnit.SECONDS))
            assertFailsWith<CancellationException> { awaiting.await() }
        } finally {
            running.cancelIfActive()
            unblockBody.countDown()
        }
    }
}
