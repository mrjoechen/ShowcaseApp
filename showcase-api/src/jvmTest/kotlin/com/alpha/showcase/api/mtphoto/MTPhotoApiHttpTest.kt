package com.alpha.showcase.api.mtphoto

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MTPhotoApiHttpTest {

    @Test
    fun credentialsAndTokensAreNotWrittenToHttpLogs() = runBlocking {
        val recorder = RecordingAntilog()
        val server = mtPhotoServer()
        Napier.base(recorder)
        server.start()

        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val api = MTPhotoApi()

            api.login(
                baseUrl,
                MTPhotoLoginRequest("secret-user", "password-secret"),
            )
            api.getAuthCode(baseUrl, "api-key-secret")
            api.getAlbums(
                baseUrl = baseUrl,
                headerName = "x-api-key",
                headerValue = "header-secret",
            )
            api.getAlbums(
                baseUrl = baseUrl,
                headerName = "Authorization",
                headerValue = "Bearer bearer-header-secret",
            )

            val output = recorder.messages.joinToString("\n")
            assertTrue(output.isNotBlank(), "The test must observe real Ktor HTTP logs")
            listOf(
                "password-secret",
                "api-key-secret",
                "header-secret",
                "bearer-header-secret",
                "access-token-secret",
                "refresh-token-secret",
                "response-auth-code-secret",
            ).forEach { secret ->
                assertFalse(output.contains(secret), "HTTP logs exposed $secret")
            }
        } finally {
            server.stop(0)
            Napier.takeLogarithm(recorder)
        }
    }

    @Test
    fun albumFilesUseTheAndroidReferenceFlatContractWithoutDetailRequest() = runBlocking {
        val detailRequestCount = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/api-album/filesFlat/17") { exchange ->
                exchange.respondJson(
                    """[{"id":23,"MD5":"flat-md5-23","status":1,"tokenAt":"2026-01-02T10:00:00.000Z","fileType":"image/jpeg","duration":null,"fileSize":"1234","width":1600,"height":900},{"id":24,"MD5":"flat-md5-24","status":1,"tokenAt":"2026-01-03T11:00:00.000Z","fileType":"video/mp4","duration":12.5,"fileSize":"5678","width":1920,"height":1080}]"""
                )
            }
            createContext("/gateway/fileInIds") { exchange ->
                detailRequestCount.incrementAndGet()
                exchange.respondJson(
                    """{"result":[{"day":"2026-01-02","files":[]}]}"""
                )
            }
        }
        server.start()

        try {
            val files = MTPhotoApi().getAlbumFiles(
                baseUrl = "http://127.0.0.1:${server.address.port}",
                albumId = 17,
                headerName = "x-api-key",
                headerValue = "header-secret",
            )

            assertEquals(listOf(23, 24), files.map { it.id })
            val file = files.first()
            assertEquals(23, file.id)
            assertEquals("flat-md5-23", file.md5)
            assertEquals("2026-01-02T10:00:00.000Z", file.tokenAt)
            assertEquals("image/jpeg", file.fileType)
            assertEquals("1234", file.fileSize)
            assertEquals(1600, file.width)
            assertEquals(900, file.height)
            assertEquals("flat-md5-24", files.last().md5)
            assertEquals(12.5f, files.last().duration)
            assertEquals(0, detailRequestCount.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun metadataRequestsHaveATotalDeadline() = runBlocking {
        val releaseResponse = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/api-album") { exchange ->
                releaseResponse.await(5, TimeUnit.SECONDS)
                exchange.respondJson("[]")
            }
        }
        server.start()

        try {
            assertFailsWith<MTPhotoMetadataTimeoutException> {
                MTPhotoApi(
                    metadataRequestTimeoutMillis = 100,
                    connectTimeoutMillis = 1_000,
                    socketTimeoutMillis = 1_000,
                ).getAlbums(
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                    headerName = "x-api-key",
                    headerValue = "header-secret",
                )
            }
            Unit
        } finally {
            releaseResponse.countDown()
            server.stop(0)
        }
    }

    @Test
    fun albumFilesFlatAppliesTheMetadataDeadline() = runBlocking {
        val requestCount = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/api-album/filesFlat/17") { exchange ->
                requestCount.incrementAndGet()
                Thread.sleep(400)
                exchange.respondJson("[]")
            }
        }
        server.start()

        try {
            assertFailsWith<MTPhotoMetadataTimeoutException> {
                MTPhotoApi(
                    metadataRequestTimeoutMillis = 100,
                    connectTimeoutMillis = 1_000,
                    socketTimeoutMillis = 2_000,
                ).getAlbumFiles(
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                    albumId = 17,
                    headerName = "x-api-key",
                    headerValue = "header-secret",
                )
            }
            assertEquals(1, requestCount.get())
            Unit
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun callerCancellationIsNotConvertedIntoAMetadataTimeout() = runBlocking {
        val requestStarted = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/api-album") { exchange ->
                requestStarted.countDown()
                releaseResponse.await(5, TimeUnit.SECONDS)
                exchange.respondJson("[]")
            }
        }
        server.start()

        try {
            val request = async(Dispatchers.IO) {
                MTPhotoApi(metadataRequestTimeoutMillis = 5_000).getAlbums(
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                    headerName = "x-api-key",
                    headerValue = "header-secret",
                )
            }
            assertTrue(requestStarted.await(5, TimeUnit.SECONDS))

            request.cancel(CancellationException("caller left the page"))

            assertFailsWith<CancellationException> { request.await() }
            Unit
        } finally {
            releaseResponse.countDown()
            server.stop(0)
        }
    }

    private fun mtPhotoServer(): HttpServer = HttpServer.create(InetSocketAddress(0), 0).apply {
        createContext("/auth/login") { exchange ->
            exchange.respondJson(
                """{"access_token":"access-token-secret","refresh_token":"refresh-token-secret","auth_code":"response-auth-code-secret"}"""
            )
        }
        createContext("/auth/auth_code") { exchange ->
            exchange.respondJson("""{"auth_code":"response-auth-code-secret"}""")
        }
        createContext("/api-album") { exchange ->
            exchange.respondJson("[]")
        }
    }

    private fun HttpExchange.respondJson(body: String) {
        val bytes = body.encodeToByteArray()
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(200, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private class RecordingAntilog : Antilog() {
        val messages = mutableListOf<String>()

        override fun performLog(
            priority: LogLevel,
            tag: String?,
            throwable: Throwable?,
            message: String?,
        ) {
            if (message != null) messages += message
        }
    }
}
