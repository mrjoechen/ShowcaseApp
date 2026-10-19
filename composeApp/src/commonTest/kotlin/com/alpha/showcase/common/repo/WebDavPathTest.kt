package com.alpha.showcase.common.repo

import kotlin.test.Test
import kotlin.test.assertEquals

class WebDavPathTest {

    @Test
    fun originKeepsSchemeHostAndPortButDropsPath() {
        assertEquals("https://openlist.example.test", webDavOrigin("https://openlist.example.test/dav"))
        assertEquals("http://nas.example.test:5005", webDavOrigin("http://nas.example.test:5005/dav/?x=1"))
        assertEquals("http://nas.example.test:5005", webDavOrigin("http://nas.example.test:5005"))
        // The host contains the path text; a plain replace would mangle it.
        assertEquals("https://dav.example.test", webDavOrigin("https://dav.example.test/dav"))
    }

    @Test
    fun serverPathPrependsTheUrlPath() {
        assertEquals("/dav/", webDavServerPath("https://openlist.example.test/dav", "/"))
        assertEquals("/dav/", webDavServerPath("https://openlist.example.test/dav/", ""))
        assertEquals("/dav/photos", webDavServerPath("https://openlist.example.test/dav", "/photos/"))
        assertEquals("/", webDavServerPath("http://nas.example.test:5005", "/"))
        assertEquals("/photos", webDavServerPath("http://nas.example.test:5005/", "photos"))
    }

    @Test
    fun relativePathStripsTheUrlPathFromHrefs() {
        val url = "https://openlist.example.test/dav"
        assertEquals("/", webDavRelativePath(url, "/dav/"))
        assertEquals("/photos", webDavRelativePath(url, "/dav/photos/"))
        assertEquals("/photos/a.jpg", webDavRelativePath(url, "/dav/photos/a.jpg"))
        // Only whole segments count as the base path.
        assertEquals("/davx/photos", webDavRelativePath(url, "/davx/photos/"))
        assertEquals("/photos", webDavRelativePath("http://nas.example.test:5005", "/photos/"))
    }

    @Test
    fun relativePathMatchesEncodedHrefsAgainstUnencodedUrl() {
        assertEquals(
            "/%E7%85%A7%E7%89%87",
            webDavRelativePath("https://nas.example.test/我的/dav", "/%E6%88%91%E7%9A%84/dav/%E7%85%A7%E7%89%87/"),
        )
    }

    @Test
    fun relativePathRoundTripsThroughServerPath() {
        val url = "https://openlist.example.test/dav"
        val relative = webDavRelativePath(url, "/dav/photos/2024/")
        assertEquals("/dav/photos/2024", webDavServerPath(url, relative))
    }
}
