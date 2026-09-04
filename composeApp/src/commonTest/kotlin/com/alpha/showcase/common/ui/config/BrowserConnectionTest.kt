package com.alpha.showcase.common.ui.config

import com.alpha.showcase.common.networkfile.storage.remote.AlbumSource
import com.alpha.showcase.common.networkfile.storage.remote.ImmichSource
import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO_AUTH_TYPE_API_KEY
import com.alpha.showcase.common.networkfile.storage.remote.MTPhotoSource
import com.alpha.showcase.common.networkfile.storage.remote.RssSource
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.utils.ConnectionProbeTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowserConnectionTest {

    @Test
    fun httpsPageRejectsEveryHttpBackedDirectSourceBeforeNetworkAccess() {
        val sources = listOf(
            WebDav(url = "http://dav.example.test", user = "", passwd = "", name = "dav"),
            ImmichSource(name = "immich", url = "http://photos.example.test/", port = 2283),
            MTPhotoSource(
                name = "mtphoto",
                url = "http://photos.example.test:8063",
                authType = MTPHOTO_AUTH_TYPE_API_KEY,
                apiKey = "key",
            ),
            S3Source(
                name = "s3",
                endpoint = "objects.example.test:9000",
                accessKey = "access",
                secretKey = "secret",
                bucket = "bucket",
                useSSL = false,
            ),
            RssSource(name = "rss", url = "http://feeds.example.test/photos.xml"),
        )

        sources.forEach { source ->
            assertEquals(
                BrowserConnectionProblem.MixedContent,
                classifyBrowserConnectionProblem(
                    pageProtocol = "https:",
                    baseUrl = source.browserRequestBaseUrl(),
                ),
                source::class.simpleName,
            )
        }
    }

    @Test
    fun albumShareLinkIsNotTreatedAsTheBackendRequestUrl() {
        val source = AlbumSource(
            name = "album",
            playlistUrl = "https://y.qq.com/n/ryqq/playlist/9533705141",
        )

        assertNull(source.browserRequestBaseUrl())
    }

    @Test
    fun s3EndpointSchemeOverridesSslSwitchWhenClassifyingMixedContent() {
        val source = S3Source(
            name = "s3",
            endpoint = "https://objects.example.test",
            accessKey = "access",
            secretKey = "secret",
            bucket = "bucket",
            useSSL = false,
        )

        assertEquals("https://objects.example.test", source.browserRequestBaseUrl())
        assertNull(
            classifyBrowserConnectionProblem(
                pageProtocol = "https:",
                baseUrl = source.browserRequestBaseUrl(),
            )
        )
    }

    @Test
    fun browserTimeoutAndFetchFailuresExplainBrowserAccessRequirements() {
        listOf(
            ConnectionProbeTimeoutException(10_000),
            IllegalStateException("TypeError: Failed to fetch"),
            IllegalStateException("Request timeout has expired"),
            IllegalStateException("CORS preflight blocked the request"),
        ).forEach { error ->
            assertEquals(
                BrowserConnectionProblem.BrowserAccess,
                classifyBrowserConnectionProblem(
                    pageProtocol = "https:",
                    baseUrl = "https://dav.example.test",
                    error = error,
                ),
            )
        }

        assertNull(
            classifyBrowserConnectionProblem(
                pageProtocol = null,
                baseUrl = "https://dav.example.test",
                error = ConnectionProbeTimeoutException(10_000),
            )
        )
    }
}
