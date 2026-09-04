package com.alpha.showcase.api

import com.alpha.showcase.api.immich.buildImmichApiUrl
import kotlin.test.Test
import kotlin.test.assertEquals

class ImmichApiUrlTest {

    @Test
    fun apiUrlAcceptsBaseUrlsWithOrWithoutTrailingSlash() {
        assertEquals(
            "https://photos.example.test/api/albums",
            buildImmichApiUrl("https://photos.example.test", "api/albums"),
        )
        assertEquals(
            "https://photos.example.test/api/albums",
            buildImmichApiUrl("https://photos.example.test/", "/api/albums"),
        )
    }
}
