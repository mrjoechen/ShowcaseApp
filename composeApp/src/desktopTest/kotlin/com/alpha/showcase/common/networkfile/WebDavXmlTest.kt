package com.alpha.showcase.common.networkfile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WebDavXmlTest {
    @Test
    fun initializesXmlAndDecodesPropfindResponse() {
        // Exercise WebDAV's actual XML configuration with the desktop runtime
        // dependencies: mixed xmlutil core/serialization versions fail at init.
        val client = WebDavClient("https://dav.example.test", "user", "password")
        val result = client.xml.decodeFromString(
            Multistatus.serializer(),
            """
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/photos/</D:href>
                    <D:propstat>
                      <D:prop>
                        <D:displayname>photos</D:displayname>
                        <D:resourcetype><D:collection/></D:resourcetype>
                      </D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/photos/sunset.jpg</D:href>
                    <D:propstat>
                      <D:prop>
                        <D:displayname>sunset.jpg</D:displayname>
                        <D:resourcetype/>
                        <D:getcontenttype>image/jpeg</D:getcontenttype>
                        <D:getcontentlength>1234</D:getcontentlength>
                      </D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                </D:multistatus>
            """.trimIndent(),
        )

        assertEquals(2, result.responses.size)
        assertNotNull(result.responses[0].propstat.prop.resourcetype.collection)
        val photo = result.responses[1]
        assertEquals("/photos/sunset.jpg", photo.href)
        assertEquals("sunset.jpg", photo.propstat.prop.displayname)
        assertNull(photo.propstat.prop.resourcetype.collection)
        assertEquals("image/jpeg", photo.propstat.prop.getcontenttype)
        assertEquals("1234", photo.propstat.prop.getcontentlength)
    }
}
