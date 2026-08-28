package com.alpha.showcase.common.ui.config

import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO_AUTH_TYPE_API_KEY
import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO_AUTH_TYPE_PASSWORD
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
