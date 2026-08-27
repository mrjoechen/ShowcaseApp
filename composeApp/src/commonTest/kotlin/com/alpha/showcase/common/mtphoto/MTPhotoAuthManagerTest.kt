package com.alpha.showcase.common.mtphoto

import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO_AUTH_TYPE_API_KEY
import com.alpha.showcase.common.networkfile.storage.remote.MTPhotoSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class MTPhotoAuthManagerTest {

    @Test
    fun sessionIsReusedUntilExpiryAndThenRefreshed() = runTest {
        var now = 1_000L
        var loads = 0
        val manager = MTPhotoAuthManager(
            authLoader = {
                loads += 1
                MTPhotoAuthSession(
                    authCode = "auth-$loads",
                    headerName = "x-api-key",
                    headerValue = "secret",
                )
            },
            nowMillis = { now },
        )
        val source = source()
        val sourceKey = manager.register(source)

        assertEquals("auth-1", manager.getAuthForKey(sourceKey).authCode)
        assertEquals("auth-1", manager.getAuthForKey(sourceKey).authCode)
        assertEquals(1, loads)

        now += MTPhotoAuthManager.AUTH_TTL_MILLIS + 1

        assertEquals("auth-2", manager.getAuthForKey(sourceKey).authCode)
        assertEquals(2, loads)
    }

    @Test
    fun invalidationForcesRefresh() = runTest {
        var loads = 0
        val manager = MTPhotoAuthManager(
            authLoader = {
                loads += 1
                MTPhotoAuthSession("auth-$loads", "x-api-key", "secret")
            },
        )
        val sourceKey = manager.register(source())

        manager.getAuthForKey(sourceKey)
        manager.invalidate(sourceKey)

        assertEquals("auth-2", manager.getAuthForKey(sourceKey).authCode)
    }

    @Test
    fun sourceKeyIsStableAndDoesNotExposeCredentials() {
        val manager = MTPhotoAuthManager(authLoader = { error("not used") })
        val source = source()

        val first = manager.sourceKey(source)
        val second = manager.sourceKey(source.copy(name = "Renamed"))
        val changedCredentials = manager.sourceKey(source.copy(apiKey = "other-secret"))

        assertEquals(first, second)
        assertNotEquals(first, changedCredentials)
        assertFalse(first.contains("plain-api-key"))
    }

    private fun source() = MTPhotoSource(
        name = "Photos",
        url = "https://photos.example/",
        authType = MTPHOTO_AUTH_TYPE_API_KEY,
        apiKey = "plain-api-key",
        albumId = 17,
        albumName = "旅行",
    )
}
