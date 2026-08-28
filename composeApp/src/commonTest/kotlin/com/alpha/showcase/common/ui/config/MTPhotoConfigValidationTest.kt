package com.alpha.showcase.common.ui.config

import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO_AUTH_TYPE_API_KEY
import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO_AUTH_TYPE_PASSWORD
import com.alpha.showcase.common.networkfile.storage.remote.MTPhotoSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MTPhotoConfigValidationTest {

    @Test
    fun apiKeyAuthenticationRequiresKeyAndSelectedAlbum() {
        assertEquals(
            MTPhotoConfigError.ApiKeyRequired,
            validateMTPhotoConfig(MTPHOTO_AUTH_TYPE_API_KEY, "", "", "", null),
        )
        assertEquals(
            MTPhotoConfigError.AlbumRequired,
            validateMTPhotoConfig(MTPHOTO_AUTH_TYPE_API_KEY, "key", "", "", null),
        )
        assertNull(validateMTPhotoConfig(MTPHOTO_AUTH_TYPE_API_KEY, "key", "", "", 17))
    }

    @Test
    fun passwordAuthenticationRequiresUsernamePasswordAndAlbum() {
        assertEquals(
            MTPhotoConfigError.UsernameRequired,
            validateMTPhotoConfig(MTPHOTO_AUTH_TYPE_PASSWORD, "", "", "", null),
        )
        assertEquals(
            MTPhotoConfigError.PasswordRequired,
            validateMTPhotoConfig(MTPHOTO_AUTH_TYPE_PASSWORD, "", "joe", "", null),
        )
        assertNull(validateMTPhotoConfig(MTPHOTO_AUTH_TYPE_PASSWORD, "", "joe", "pass", 17))
    }

    @Test
    fun albumResponseIsRejectedWhenConnectionInputsChange() {
        val requested = apiKeySource()
        val changedConnections = listOf(
            requested.copy(url = "https://new.example.test"),
            requested.copy(authType = MTPHOTO_AUTH_TYPE_PASSWORD, apiKey = null, user = "joe", pass = "pass"),
            requested.copy(apiKey = "new-key"),
        )

        changedConnections.forEach { current ->
            assertFalse(isCurrentMTPhotoAlbumResponse(requested, current))
        }
    }

    @Test
    fun albumResponseRemainsCurrentWhenOnlyPresentationChanges() {
        val requested = apiKeySource()
        val current = requested.copy(name = "Renamed", albumId = 99, albumName = "Other album")

        assertTrue(isCurrentMTPhotoAlbumResponse(requested, current))
    }

    @Test
    fun passwordAlbumResponseIsRejectedWhenCredentialsChange() {
        val requested = MTPhotoSource(
            name = "Photos",
            url = "https://photos.example.test",
            authType = MTPHOTO_AUTH_TYPE_PASSWORD,
            user = "joe",
            pass = "pass",
        )

        assertFalse(isCurrentMTPhotoAlbumResponse(requested, requested.copy(user = "jane")))
        assertFalse(isCurrentMTPhotoAlbumResponse(requested, requested.copy(pass = "new-pass")))
    }

    @Test
    fun olderAlbumResponseIsRejectedEvenWhenConnectionInputsMatch() {
        val source = apiKeySource()

        assertFalse(
            isCurrentMTPhotoAlbumResponse(
                requested = source,
                current = source,
                requestId = 1,
                latestRequestId = 2,
            )
        )
        assertTrue(
            isCurrentMTPhotoAlbumResponse(
                requested = source,
                current = source,
                requestId = 2,
                latestRequestId = 2,
            )
        )
    }

    @Test
    fun albumLoadTimeoutIsReturnedAsFailure() = runTest {
        val result = loadMTPhotoAlbumsWithTimeout<List<String>>(timeoutMillis = 10_000) {
            awaitCancellation()
        }

        assertTrue(result.isFailure)
        assertEquals(
            "MTPhoto album loading timed out after 10 seconds",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun externalAlbumLoadCancellationPropagates() = runTest {
        val cancellation = CancellationException("screen left")

        val thrown = assertFailsWith<CancellationException> {
            loadMTPhotoAlbumsWithTimeout<List<String>>(timeoutMillis = 10_000) {
                throw cancellation
            }
        }

        assertEquals(cancellation.message, thrown.message)
    }

    private fun apiKeySource() = MTPhotoSource(
        name = "Photos",
        url = "https://photos.example.test",
        authType = MTPHOTO_AUTH_TYPE_API_KEY,
        apiKey = "key",
        albumId = 17,
        albumName = "Trips",
    )
}
