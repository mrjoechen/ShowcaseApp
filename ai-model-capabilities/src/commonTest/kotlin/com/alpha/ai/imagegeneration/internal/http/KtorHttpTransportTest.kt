package com.alpha.ai.imagegeneration.internal.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KtorHttpTransportTest {
    @Test
    fun queryOnlyRedirectsRetainEndpointAndReplacePreviousQuery() = runTest {
        val visited = mutableListOf<String>()
        val locations = listOf("?region=us", "?region=eu&attempt=2", "/complete")
        HttpClient(MockEngine { request ->
            visited += request.url.toString()
            locations.getOrNull(visited.size - 1)?.let {
                respond("", HttpStatusCode.TemporaryRedirect, headersOf("Location", it))
            } ?: respond("{}")
        }) { followRedirects = false }.use { client ->
            val response = KtorHttpTransport(client).prepare(
                HttpRequest.post(Url("https://provider.test/v1/images/edits"), body = "{}".encodeToByteArray()), 16,
            ).start().await()
            assertEquals(200, response.status)
            assertEquals(listOf(
                "https://provider.test/v1/images/edits",
                "https://provider.test/v1/images/edits?region=us",
                "https://provider.test/v1/images/edits?region=eu&attempt=2",
                "https://provider.test/complete",
            ), visited)
        }
    }

    @Test
    fun redirectReferencesResolvePathsQueriesAndFragmentsIndependently() = runTest {
        val base = "https://provider.test/v1/images/edits?old=yes"
        val cases = listOf(
            "next" to "https://provider.test/v1/images/next",
            "../next?new=yes" to "https://provider.test/v1/next?new=yes",
            "./next/../done" to "https://provider.test/v1/images/done",
            "/next" to "https://provider.test/next",
            "https://provider.test/next?new=yes" to "https://provider.test/next?new=yes",
            "//provider.test/next" to "https://provider.test/next",
            "?" to "https://provider.test/v1/images/edits?",
            "#result" to "https://provider.test/v1/images/edits?old=yes#result",
            "" to base,
            "next?path=/../kept#fragment/../kept" to "https://provider.test/v1/images/next?path=/../kept#fragment/../kept",
        )
        cases.forEach { (location, expected) ->
            val visited = mutableListOf<String>()
            HttpClient(MockEngine { request ->
                visited += request.url.toString()
                if (visited.size == 1) respond("", HttpStatusCode.Found, headersOf("Location", location))
                else respond("{}")
            }) { followRedirects = false }.use { client ->
                KtorHttpTransport(client).prepare(HttpRequest.get(Url(base)), 16).start().await()
                assertEquals(listOf(base, expected), visited, "Location: $location")
            }
        }
    }

    @Test
    fun redirectReferencesNeverForwardCredentialsToDisallowedOriginsOrSchemes() = runTest {
        listOf("//other.test/next", "http://provider.test/next", "https://provider.test:444/next", "ftp://provider.test/next", "https://user@provider.test/next").forEach { location ->
            var dispatches = 0
            HttpClient(MockEngine {
                dispatches++
                respond("", HttpStatusCode.Found, headersOf("Location", location))
            }) { followRedirects = false }.use { client ->
                assertFailsWith<ImageGenerationTransportException> {
                    KtorHttpTransport(client).prepare(HttpRequest.get(Url("https://provider.test/v1"), mapOf("Authorization" to "Bearer private")), 16).start().await()
                }
                assertEquals(1, dispatches, "Location: $location")
            }
        }
    }

    @Test
    fun boundedResponseRejectsUnknownLengthOversizeBodies() = runTest {
        HttpClient(MockEngine { respond(ByteReadChannel("12345")) }).use { client ->
            val transport = KtorHttpTransport(client)
            assertFailsWith<ImageGenerationTransportException> {
                transport.prepare(HttpRequest.get(Url("https://provider.test/v1")), 4).start().await()
            }
        }
    }

    @Test
    fun largestLimitDoesNotOverflowAndResponseHeaderNamesAreNormalized() = runTest {
        HttpClient(MockEngine { respond("{}", headers = headersOf("Content-Type", "application/json")) }).use { client ->
            val response = KtorHttpTransport(client).prepare(HttpRequest.get(Url("https://provider.test/v1")), Long.MAX_VALUE).start().await()
            assertContentEquals("{}".encodeToByteArray(), response.body)
            assertEquals("application/json", response.headers["content-type"])
        }
    }

    @Test
    fun redirectsCannotSendCredentialsToAnotherOrigin() = runTest {
        val visited = mutableListOf<String>()
        HttpClient(MockEngine { request ->
            visited += request.url.toString()
            respond("", HttpStatusCode.Found, headersOf("Location", "https://other.test/steal"))
        }) { followRedirects = false }.use { client ->
            assertFailsWith<ImageGenerationTransportException> {
                KtorHttpTransport(client).prepare(HttpRequest.get(Url("https://provider.test/v1"), mapOf("Authorization" to "Bearer private")), 16).start().await()
            }
            assertEquals(listOf("https://provider.test/v1"), visited)
        }
    }

    @Test
    fun sameOriginRedirectPreservesPostBodyAndStopsAfterFiveHops() = runTest {
        var dispatches = 0
        HttpClient(MockEngine { request ->
            dispatches++
            assertEquals("POST", request.method.value)
            assertContentEquals("{}".encodeToByteArray(), (request.body as OutgoingContent.ByteArrayContent).bytes())
            assertEquals("Bearer private", request.headers["Authorization"])
            respond("", HttpStatusCode.TemporaryRedirect, headersOf("Location", "/next"))
        }) { followRedirects = false }.use { client ->
            assertFailsWith<ImageGenerationTransportException> {
                KtorHttpTransport(client).prepare(HttpRequest.post(Url("https://provider.test/v1"), mapOf("Authorization" to "Bearer private"), "{}".encodeToByteArray()), 16).start().await()
            }
            assertEquals(6, dispatches)
        }
    }

    @Test
    fun transportFailuresDoNotRetryPostsOrExposeEngineDetails() = runTest {
        var dispatches = 0
        HttpClient(MockEngine { dispatches++; error("Bearer private body") }).use { client ->
            val failure = assertFailsWith<ImageGenerationTransportException> {
                KtorHttpTransport(client).prepare(HttpRequest.post(Url("https://provider.test/v1"), body = "{}".encodeToByteArray()), 16).start().await()
            }
            assertEquals(1, dispatches)
            assertTrue(!failure.toString().contains("private"))
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.none { it.toString().contains("private") })
        }
    }

    @Test
    fun cancellationWhileStreamingClosesResponseBody() = runTest {
        val channel = ByteChannel()
        val dispatched = CompletableDeferred<Unit>()
        HttpClient(MockEngine { dispatched.complete(Unit); respond(channel) }).use { client ->
            val running = KtorHttpTransport(client).prepare(HttpRequest.get(Url("https://provider.test/v1")), 16).start()
            val awaiting = async { running.await() }
            dispatched.await()
            awaiting.cancel()
            assertFailsWith<CancellationException> { awaiting.await() }
            running.cancelIfActive()
            withTimeout(5_000) { while (!channel.isClosedForRead) yield() }
            assertTrue(channel.isClosedForRead)
        }
    }
}
