package com.alpha.showcase.common

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.util.RConfig
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okio.Path.Companion.toPath

class SmbImageFileReaderTest {
    private val remote = Smb(id = "nas", host = "nas", user = "user", passwd = "secret", name = "NAS")
    private val file = NetworkFile(remote, "smb://nas/Shared Photos/a%20+b.jpg", "photo.jpg", false, 12_000_000, "image/jpeg", "1")
    private fun setup() { RConfig.initEnCryptAndDecrypt({ it }, { it }, { it }, { it }) }

    @Test fun successiveImagesReuseSessionAndPassAFilePathWithoutBase64() = runTest {
        setup()
        val requests = mutableListOf<JsonObject>()
        val reader = SmbImageFileReader({ raw ->
            val request = Json.parseToJsonElement(raw).jsonObject
            requests += request
            if (request["action"]!!.jsonPrimitive.content == "open") """{"ok":true,"sessionId":"session"}""" else """{"ok":true}"""
        })
        repeat(2) { reader.download(file, "/tmp/image-$it".toPath()) }
        reader.closeIdle()
        assertEquals(listOf("open", "downloadFile", "validate", "downloadFile", "close"), requests.map { it["action"]!!.jsonPrimitive.content })
        val download = requests[1]
        assertEquals("Shared Photos", download["share"]!!.jsonPrimitive.content)
        assertEquals("a%20+b.jpg", download["path"]!!.jsonPrimitive.content)
        assertEquals("/tmp/image-0", download["destination"]!!.jsonPrimitive.content)
        assertFalse(download.containsKey("dataBase64"))
    }

    @Test fun serverClosedIdleSessionIsReplacedBeforeDownloadingNextImage() = runTest {
        setup()
        var opens = 0
        var downloads = 0
        val closed = mutableListOf<String>()
        val reader = SmbImageFileReader({ raw ->
            val request = Json.parseToJsonElement(raw).jsonObject
            when (request["action"]!!.jsonPrimitive.content) {
                "open" -> { opens++; """{"ok":true,"sessionId":"s$opens"}""" }
                "validate" -> """{"ok":false,"error":"connection lost"}"""
                "close" -> { closed += request["sessionId"]!!.jsonPrimitive.content; """{"ok":true}""" }
                else -> { downloads++; """{"ok":true}""" }
            }
        })
        reader.download(file, "/tmp/first".toPath())
        reader.download(file, "/tmp/next".toPath())
        assertEquals(2, opens)
        assertEquals(2, downloads, "Do not retry the actual file transfer on an invalid session")
        assertEquals(listOf("s1"), closed)
        reader.closeIdle()
    }

    @Test fun failureDiscardsSessionAndNextImageReconnects() = runTest {
        setup()
        var opens = 0
        var closes = 0
        var failDownload = true
        val reader = SmbImageFileReader({ raw ->
            when (Json.parseToJsonElement(raw).jsonObject["action"]!!.jsonPrimitive.content) {
                "open" -> { opens++; """{"ok":true,"sessionId":"s$opens"}""" }
                "close" -> { closes++; """{"ok":true}""" }
                else -> if (failDownload) """{"ok":false}""" else """{"ok":true}"""
            }
        })
        assertFailsWith<IllegalStateException> { reader.download(file, "/tmp/failed".toPath()) }
        failDownload = false
        reader.download(file, "/tmp/retry".toPath())
        assertEquals(2, opens)
        assertEquals(1, closes)
        reader.closeIdle()
    }

    @Test fun cancellationWhileValidatingDiscardsOwnedSessionWithoutRetry() = runTest {
        setup()
        var opens = 0
        var closes = 0
        val reader = SmbImageFileReader({ raw ->
            when (Json.parseToJsonElement(raw).jsonObject["action"]!!.jsonPrimitive.content) {
                "open" -> { opens++; """{"ok":true,"sessionId":"s$opens"}""" }
                "validate" -> throw CancellationException("left playback")
                "close" -> { closes++; """{"ok":true}""" }
                else -> """{"ok":true}"""
            }
        })
        reader.download(file, "/tmp/first".toPath())
        assertFailsWith<CancellationException> { reader.download(file, "/tmp/cancelled".toPath()) }
        assertEquals(1, opens)
        assertEquals(1, closes)
        reader.closeIdle()
        assertEquals(1, closes)
    }

    @Test fun expiredOrChangedAccountSessionsAreNotReused() = runTest {
        setup()
        var time = 0L
        var opens = 0
        var closes = 0
        val reader = SmbImageFileReader({ raw ->
            when (Json.parseToJsonElement(raw).jsonObject["action"]!!.jsonPrimitive.content) {
                "open" -> { opens++; """{"ok":true,"sessionId":"s$opens"}""" }
                "close" -> { closes++; """{"ok":true}""" }
                else -> """{"ok":true}"""
            }
        }, now = { time })
        reader.download(file, "/tmp/a".toPath())
        time = 120_001
        reader.download(file, "/tmp/b".toPath())
        reader.download(file.copy(remote = remote.copy(passwd = "new")), "/tmp/c".toPath())
        assertEquals(3, opens)
        assertEquals(1, closes)
        reader.closeIdle()
    }
}
