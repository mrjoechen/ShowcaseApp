package com.alpha.showcase.common.ai

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.ui.play.PlayViewModel
import com.alpha.showcase.common.ui.play.DataWithType
import com.alpha.showcase.common.ui.play.PlaybackFileOrigin
import com.alpha.showcase.common.ui.play.UrlWithAuth
import com.alpha.showcase.common.ui.play.convertNetworkFilesForPlayback
import com.alpha.showcase.common.ui.ext.buildImageRequest
import coil3.PlatformContext
import coil3.network.httpHeaders
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SummarySourceProvenanceTest {
    @Test fun webDavPlaybackKeepsTheConfiguredSourceInSummaryReferences() = runTest {
        RConfig.initEnCryptAndDecrypt({ it }, { it }, { it }, { it })
        val source = WebDav(url = "http://photos.example:5005", user = "test-user", passwd = "test-password", name = "Home WebDAV")
        val file = NetworkFile(source, "/Share/test/heic_example.heic", "heic_example.heic", false, 718114, "image/heic", "")
        assertEquals(source.name, summaryFileReference(file, testImageIdentity())?.sourceName)
        val playback = PlayViewModel().convertNetworkFiles(source, listOf(file)).single()
        assertIs<UrlWithAuth>(playback)
        // Paged and full-list playback use the same conversion, and serialization retains the origin.
        assertEquals(playback, convertNetworkFilesForPlayback(source, listOf(file)).single())
        assertEquals(playback, Json.decodeFromString<UrlWithAuth>(Json.encodeToString(playback)))
        val reference = assertNotNull(summaryFileReference(playback, testImageIdentity()))
        assertEquals(source.name, reference.sourceName)
        assertEquals("webdav", reference.sourceProtocol)
        assertEquals("http://photos.example:5005/Share/test/heic_example.heic", reference.filePath)
        assertEquals(file.fileName, reference.fileName)
        val request = buildImageRequest(PlatformContext.INSTANCE, playback)
        assertEquals(playback.url, request.data)
        assertEquals(playback.cacheKey, request.diskCacheKey)
        assertEquals(playback.value, request.httpHeaders[playback.key])
        assertFalse(summaryJson.encodeToString(reference).contains("test-password"))
        assertFalse(summaryJson.encodeToString(reference).contains(playback.value))
    }

    @Test fun wrappedUrlsKeepOriginButNeverExportUrlCredentialsOrQueryTokens() {
        val media = UrlWithAuth("https://user:password@photos.example/encoded%20name.jpg?token=private#fragment",
            "Authorization", "test-secret", origin = PlaybackFileOrigin("Photos", "webdav", "encoded name.jpg"))
        val reference = assertNotNull(summaryFileReference(DataWithType(media, "image/jpeg"), testImageIdentity()))
        assertEquals("Photos", reference.sourceName)
        assertEquals("webdav", reference.sourceProtocol)
        assertEquals("https://photos.example/encoded%20name.jpg", reference.filePath)
        assertEquals("encoded name.jpg", reference.fileName)
        val json = summaryJson.encodeToString(reference)
        for (secret in listOf("password", "test-secret", "token=", "fragment")) assertFalse(json.contains(secret))
    }

    @Test fun plainHttpUrlsWithoutSourceContextRemainUnnamedInsteadOfGuessingFromAnotherSource() {
        val media = UrlWithAuth("http://photos.example/photo.jpg", "Authorization", "secret")
        val reference = assertNotNull(summaryFileReference(media, testImageIdentity()))
        assertEquals("", reference.sourceName)
        assertEquals("http", reference.sourceProtocol)
        assertNull(reference.legacyAnonymousWebDavReference())
    }
}
