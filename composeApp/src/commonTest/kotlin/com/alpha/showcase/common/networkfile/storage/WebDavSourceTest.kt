package com.alpha.showcase.common.networkfile.storage

import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import kotlin.test.Test
import kotlin.test.assertEquals

class WebDavSourceTest {

    @Test
    fun httpsUrlKeepsItsSchemeHostAndExplicitPort() {
        val source = WebDav(
            url = "https://dav.example.test:8443/photos",
            user = "user",
            passwd = "password",
            name = "dav",
        )

        assertEquals("dav.example.test", source.host)
        assertEquals(8443, source.port)
    }

    @Test
    fun schemeLessUrlStillDefaultsToHttp() {
        val source = WebDav(
            url = "dav.example.test:8080/photos",
            user = "user",
            passwd = "password",
            name = "dav",
        )

        assertEquals("dav.example.test", source.host)
        assertEquals(8080, source.port)
    }
}
