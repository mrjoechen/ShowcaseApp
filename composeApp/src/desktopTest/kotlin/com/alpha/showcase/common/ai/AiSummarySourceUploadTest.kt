package com.alpha.showcase.common.ai

import androidx.compose.runtime.*
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import coil3.asImage
import com.alpha.ai.imagegeneration.AiModel
import com.alpha.ai.imagegeneration.provider.registerBuiltIns
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import com.alpha.showcase.common.repo.SignedS3ObjectUrl
import com.alpha.showcase.common.storage.ObjectStore
import com.alpha.showcase.common.ui.play.DataWithType
import com.alpha.showcase.common.ui.play.ResolvedImageModel
import com.alpha.showcase.common.ui.play.UrlWithAuth
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import kotlin.io.encoding.Base64
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class AiSummarySourceUploadTest {
    @Test fun allSourceRepresentationsUploadOnlyThumbnailPixelsToEveryVisionAdapter() = runDesktopComposeUiTest {
        val received = CopyOnWriteArrayList<Pair<String, String>>()
        val summary = """{"summary":"Blue picture","narration":"blue image","tags":["blue"]}"""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.use { it.readBytes().decodeToString() }
            val headers = exchange.requestHeaders.entries.joinToString { (name, values) -> "$name: ${values.joinToString()}" }
            received += body to headers
            val response = if (exchange.requestURI.path.endsWith(":generateContent")) buildJsonObject {
                put("candidates", buildJsonArray { add(buildJsonObject {
                    put("content", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", summary) }) }) })
                    put("finishReason", "STOP")
                }) })
            } else buildJsonObject {
                put("choices", buildJsonArray { add(buildJsonObject {
                    put("message", buildJsonObject { put("content", summary) })
                }) })
            }
            val bytes = response.toString().encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val bitmap = Bitmap().apply { allocN32Pixels(3072, 2048); erase(Color.BLUE) }
        val image = bitmap.asImage()
        val privateUrl = "https://source-only.example/source-only-photo.jpg?token=source-only-token"
        val signed = ResolvedImageModel(SignedS3ObjectUrl(privateUrl, Long.MAX_VALUE),
            stableKey = "source-only-object", cacheKey = "source-only-cache-key",
            refreshSignedRequest = { error("AI must never fetch a source URL") })
        val sources = listOf(
            privateUrl,
            "/private/source-only-photo.jpg",
            "content://source-only-library/source-only-photo",
            UrlWithAuth(privateUrl, "Authorization", "source-only-token"),
            DataWithType(privateUrl, "image/jpeg", mapOf("X-Source-Only-Key" to "source-only-token")),
            signed,
            DataWithType(signed, "image/jpeg"),
            NetworkFile(S3Source("source-only-storage", "source-only.example", "source-only-access", "source-only-secret", "source-only-bucket"),
                "source-only-object", "source-only-photo.jpg", false, 1000, "image/jpeg", "source-only-modified"),
        )
        lateinit var engine: AiEngine
        lateinit var scope: CoroutineScope
        var completed = false
        setContent {
            scope = rememberCoroutineScope()
            engine = remember { AiEngine(MemoryStore(), UnusedFiles, AiModel.builder().registerBuiltIns().build(), scope, { it }, { it }) }
            DisposableEffect(Unit) { onDispose { server.stop(0); bitmap.close() } }
        }
        runOnIdle { scope.launch {
            for (provider in listOf("openai-vision", "gemini-vision", "deepseek-vision")) {
                val profile = AiProfile(provider, name = "Vision", providerId = provider, model = "vision-model",
                    baseUrl = "http://127.0.0.1:${server.address.port}/v1", encryptedToken = "api-test-token", allowInsecureHttp = true)
                for (source in sources) {
                    val request = engine.summaries.prepare(aiSummaryKey(source, profile, "en-US"), image, profile, "en-US")
                    engine.summaries.request(request, force = true)
                    val state = engine.summaries.observe(request).first { !it.generating }
                    assertEquals("blue image", state.content?.narration)
                }
            }
            completed = true
        } }
        waitUntil(timeoutMillis = 60_000) { completed }
        assertEquals(24, received.size)
        for ((json, headers) in received) {
            assertFalse(json.contains("source-only", ignoreCase = true), "Source metadata leaked into request body")
            assertFalse(headers.contains("source-only", ignoreCase = true), "Source credentials leaked into request headers")
            val body = Json.parseToJsonElement(json).jsonObject
            val bytes = if ("messages" in body) {
                val content = body.getValue("messages").jsonArray.single().jsonObject.getValue("content").jsonArray
                val part = content.single { it.jsonObject.getValue("type").jsonPrimitive.content == "image_url" }.jsonObject
                val inline = part.getValue("image_url").jsonObject.getValue("url").jsonPrimitive.content
                assertTrue(inline.startsWith("data:image/jpeg;base64,"))
                Base64.decode(inline.substringAfter(','))
            } else {
                val parts = body.getValue("contents").jsonArray.single().jsonObject.getValue("parts").jsonArray
                val inline = parts.single { "inlineData" in it.jsonObject }.jsonObject.getValue("inlineData").jsonObject
                assertEquals("image/jpeg", inline.getValue("mimeType").jsonPrimitive.content)
                Base64.decode(inline.getValue("data").jsonPrimitive.content)
            }
            assertTrue(bytes.size <= 512 * 1024)
            org.jetbrains.skia.Image.makeFromEncoded(bytes).use { decoded ->
                assertEquals(768, decoded.width)
                assertEquals(512, decoded.height)
                Bitmap.makeFromImage(decoded).use { uploaded ->
                    assertTrue(Color.getB(uploaded.getColor(100, 100)) > 240)
                    assertTrue(Color.getR(uploaded.getColor(100, 100)) < 15)
                }
            }
        }
    }

    private class MemoryStore : ObjectStore<AiLibrary> {
        private var value: AiLibrary? = null
        override suspend fun get() = value
        override suspend fun set(value: AiLibrary) { this.value = value }
        override suspend fun delete() { value = null }
    }
    private object UnusedFiles : AiFiles {
        override suspend fun write(name: String, bytes: ByteArray) = error("No source files expected")
        override suspend fun read(name: String): ByteArray = error("No source files expected")
        override suspend fun delete(name: String) = Unit
        override fun imageModel(name: String): Any = error("No source URLs expected")
    }
}
