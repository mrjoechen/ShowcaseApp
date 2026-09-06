package com.alpha.showcase.common.ai

import androidx.compose.runtime.*
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import coil3.asImage
import com.alpha.ai.imagegeneration.*
import com.alpha.ai.imagegeneration.provider.registerBuiltIns
import com.alpha.showcase.common.storage.ObjectStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flow
import com.alpha.showcase.common.ui.ai.rememberAiSummaryPresentation
import kotlinx.serialization.json.*
import okio.buffer
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.io.encoding.Base64
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class AiSummaryImageBindingTest {
    private val profile = AiProfile("vision", name = "Vision", providerId = "openai-vision", model = "vision",
        baseUrl = "https://api.example/v1", encryptedToken = "test-token")

    @Test fun changedImageAtSameAddressIsNotDroppedWhileOldSummaryIsRunning() = runDesktopComposeUiTest {
        val client = DelayedClient()
        lateinit var engine: AiEngine
        lateinit var scope: CoroutineScope
        var first: AiSummaryRequest? = null
        var second: AiSummaryRequest? = null
        val red = bitmap(Color.RED)
        val blue = bitmap(Color.BLUE)
        val key = aiSummaryKey("https://photos.example/current.jpg", profile, "en-US")
        setContent {
            scope = rememberCoroutineScope()
            engine = remember { AiEngine(MemoryStore(), UnusedFiles, client, scope, { it }, { it }) }
            DisposableEffect(Unit) { onDispose { red.close(); blue.close() } }
        }
        runOnIdle { scope.launch {
            first = engine.summaries.prepare(key, red.asImage(), profile, "en-US")
            engine.summaries.request(first!!)
        } }
        waitUntil(timeoutMillis = 10_000) { client.firstStarted.isCompleted }
        runOnIdle { scope.launch {
            second = engine.summaries.prepare(key, blue.asImage(), profile, "en-US")
            engine.summaries.request(second!!)
        } }
        waitUntil(timeoutMillis = 10_000) { second != null }
        // The queued source must already be frozen; reusing the bitmap cannot change the upload.
        blue.erase(Color.RED)
        client.finishFirst.complete(Unit)
        client.finishSecond.complete(Unit)
        waitUntil(timeoutMillis = 10_000) { engine.summaries.observe(second!!).value.content != null }
        assertEquals("blue image", engine.summaries.observe(second!!).value.content?.narration)
        assertEquals("red image", engine.summaries.observe(first!!).value.content?.narration)
    }

    @Test fun lateSummaryNeverAppearsOnTheNextDisplayedImage() = runDesktopComposeUiTest {
        val client = DelayedClient()
        val red = bitmap(Color.RED)
        val blue = bitmap(Color.BLUE)
        val redImage = red.asImage()
        val blueImage = blue.asImage()
        var displayed by mutableStateOf(redImage)
        var current = AiSummaryState()
        val presentations = mutableListOf<Pair<String, String?>>()
        setContent {
            val scope = rememberCoroutineScope()
            val engine = remember { AiEngine(MemoryStore(), UnusedFiles, client, scope, { it }, { it }) }
            DisposableEffect(Unit) { onDispose { red.close(); blue.close() } }
            val presentation = rememberAiSummaryPresentation(engine,
                aiSummaryKey("https://photos.example/current.jpg", profile, "en-US"), displayed, profile, "en-US", true)
            SideEffect {
                current = presentation.state
                presentations += (if (displayed === redImage) "red image" else "blue image") to current.content?.narration
            }
        }
        waitUntil(timeoutMillis = 10_000) { client.firstStarted.isCompleted }
        runOnIdle { displayed = blueImage }
        waitForIdle()
        client.finishFirst.complete(Unit)
        waitUntil(timeoutMillis = 10_000) { client.secondStarted.isCompleted }
        assertNull(current.content)
        client.finishSecond.complete(Unit)
        waitUntil(timeoutMillis = 10_000) { current.content?.narration == "blue image" }
        runOnIdle { displayed = redImage }
        waitUntil(timeoutMillis = 10_000) { current.content?.narration == "red image" }
        assertTrue(presentations.all { (image, summary) -> summary == null || image == summary }, presentations.toString())
        assertEquals(2, client.calls) // Returning to the old image uses its own cached result.
    }

    @Test fun displayedPixelsAreDownscaledBeforeReachingTheOpenAiCompatibleHttpEndpoint() = runDesktopComposeUiTest {
        val received = CompletableDeferred<JsonObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            val body = exchange.requestBody.use { Json.parseToJsonElement(it.readBytes().decodeToString()).jsonObject }
            received.complete(body)
            val response = buildJsonObject {
                put("choices", buildJsonArray { add(buildJsonObject {
                    put("message", buildJsonObject { put("content", buildJsonObject {
                        put("summary", "Blue picture"); put("narration", "blue image"); put("tags", buildJsonArray { add("blue") })
                    }.toString()) })
                }) })
            }.toString().encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        val blue = Bitmap().apply { allocN32Pixels(3072, 2048); erase(Color.BLUE) }
        val image = blue.asImage()
        val configured = profile.copy(baseUrl = "http://127.0.0.1:${server.address.port}/v1", model = "gpt-5.5", allowInsecureHttp = true)
        var current = AiSummaryState()
        setContent {
            val scope = rememberCoroutineScope()
            val engine = remember { AiEngine(MemoryStore(), UnusedFiles, AiModel.builder().registerBuiltIns().build(), scope, { it }, { it }) }
            DisposableEffect(Unit) { onDispose { server.stop(0); blue.close() } }
            val presentation = rememberAiSummaryPresentation(engine, aiSummaryKey("blue.jpg", configured, "en-US"), image, configured, "en-US", true)
            SideEffect { current = presentation.state }
        }
        waitUntil(timeoutMillis = 10_000) { current.content != null || current.failed }
        assertEquals("blue image", current.content?.narration)
        assertTrue(received.isCompleted)
        val body = kotlinx.coroutines.runBlocking { received.await() }
        assertEquals("gpt-5.5", body.getValue("model").jsonPrimitive.content)
        val content = body.getValue("messages").jsonArray.single().jsonObject.getValue("content").jsonArray
        assertTrue(content.any { it.jsonObject.getValue("type").jsonPrimitive.content == "text" })
        val imagePart = content.single { it.jsonObject.getValue("type").jsonPrimitive.content == "image_url" }.jsonObject
        val dataUrl = imagePart.getValue("image_url").jsonObject.getValue("url").jsonPrimitive.content
        assertTrue(dataUrl.startsWith("data:image/jpeg;base64,"))
        val uploadedBytes = Base64.decode(dataUrl.substringAfter(','))
        assertTrue(uploadedBytes.size <= 512 * 1024)
        org.jetbrains.skia.Image.makeFromEncoded(uploadedBytes).use { decoded ->
            Bitmap.makeFromImage(decoded).use { uploaded ->
                assertEquals(768, uploaded.width)
                assertEquals(512, uploaded.height)
                assertTrue(Color.getB(uploaded.getColor(16, 16)) > 240)
                assertTrue(Color.getR(uploaded.getColor(16, 16)) < 15)
            }
        }
    }

    private fun bitmap(color: Int) = Bitmap().apply { allocN32Pixels(32, 32); erase(color) }

    private class DelayedClient : AiModelClient by AiModel.builder().registerBuiltIns().build() {
        val firstStarted = CompletableDeferred<Unit>()
        val finishFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val finishSecond = CompletableDeferred<Unit>()
        var calls = 0
        override fun understandImage(request: ImageUnderstandingRequest, config: ProviderRuntimeConfig) = flow {
            val bytes = request.source.openSource().buffer().use { it.readByteArray() }
            val label = org.jetbrains.skia.Image.makeFromEncoded(bytes).use { decoded ->
                Bitmap.makeFromImage(decoded).use { image ->
                    if (Color.getR(image.getColor(0, 0)) > Color.getB(image.getColor(0, 0))) "red image" else "blue image"
                }
            }
            if (++calls == 1) { firstStarted.complete(Unit); finishFirst.await() }
            else { secondStarted.complete(Unit); finishSecond.await() }
            emit(ImageUnderstandingEvent.Completed(request.operationId, ImageUnderstandingResult.Success(ImageUnderstandingOutput(
                buildJsonObject { put("summary", label); put("narration", label); put("tags", buildJsonArray { add(label) }) },
                config.providerId, config.model))))
        }
    }
    private class MemoryStore : ObjectStore<AiLibrary> {
        private var value: AiLibrary? = null
        override suspend fun get() = value
        override suspend fun set(value: AiLibrary) { this.value = value }
        override suspend fun delete() { value = null }
    }
    private object UnusedFiles : AiFiles {
        override suspend fun write(name: String, bytes: ByteArray) = error("No image files expected")
        override suspend fun read(name: String): ByteArray = error("No image files expected")
        override suspend fun delete(name: String) = Unit
        override fun imageModel(name: String): Any = name
    }
}
