package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.storage.remote.MusicPlatform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AlbumRequestBackendWebTest {

    @Test
    fun musicApiUsesRuntimeConfigWithoutReadingServerCredentials() = runTest {
        var configuredEndpointReads = 0
        var configuredAuthRead = false

        val backend = resolveAlbumRequestBackend(
            platform = MusicPlatform.QQ.key,
            configuredUrl = {
                configuredEndpointReads++
                "https://configured.example.test/"
            },
            configuredAuth = {
                configuredAuthRead = true
                "credentials"
            },
        ) as AlbumRequestBackend.MusicApi

        assertEquals("https://configured.example.test/", backend.baseUrl)
        assertNull(backend.authorization)
        assertEquals(1, configuredEndpointReads)
        assertFalse(configuredAuthRead)
    }

    @Test
    fun appleMusicFailsThroughThePlatformAdapterWithoutReadingRuntimeConfig() = runTest {
        var configuredEndpointRead = false
        var configuredAuthRead = false

        assertFailsWith<AlbumPlatformUnavailableException> {
            resolveAlbumRequestBackend(
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
        }

        assertFalse(configuredEndpointRead)
        assertFalse(configuredAuthRead)
    }
}
