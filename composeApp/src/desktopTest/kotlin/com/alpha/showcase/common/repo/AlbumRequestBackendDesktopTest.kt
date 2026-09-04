package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.storage.remote.MusicPlatform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AlbumRequestBackendDesktopTest {

    @Test
    fun appleMusicUsesTheNativeClientWithoutReadingRuntimeConfig() = runTest {
        var configuredEndpointRead = false
        var configuredAuthRead = false

        val backend = resolveAlbumRequestBackend(
            platform = MusicPlatform.Apple.key,
            configuredUrl = {
                configuredEndpointRead = true
                "https://configured.example.test/"
            },
            configuredAuth = {
                configuredAuthRead = true
                "credentials"
            },
        )

        assertEquals(AlbumRequestBackend.AppleMusic, backend)
        assertFalse(configuredEndpointRead)
        assertFalse(configuredAuthRead)
    }

    @Test
    fun musicApiUsesRuntimeConfigAndServerCredentials() = runTest {
        var configuredEndpointReads = 0
        var configuredAuthReads = 0

        val backend = resolveAlbumRequestBackend(
            platform = MusicPlatform.Netease.key,
            configuredUrl = {
                configuredEndpointReads++
                "https://configured.example.test/"
            },
            configuredAuth = {
                configuredAuthReads++
                "credentials"
            },
        ) as AlbumRequestBackend.MusicApi

        assertEquals("https://configured.example.test/", backend.baseUrl)
        assertEquals("Basic credentials", backend.authorization)
        assertEquals(1, configuredEndpointReads)
        assertEquals(1, configuredAuthReads)
    }
}
