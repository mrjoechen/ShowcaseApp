package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.networkfile.util.RConfig
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** WebDAV mounted under /dav with 405 everywhere else, like OpenList/AList. */
class NativeWebdavSourceRepoTest {

    private lateinit var server: HttpServer
    private val requestedPaths = CopyOnWriteArrayList<String>()
    private val repo = NativeWebdavSourceRepo()

    @BeforeTest
    fun setUp() {
        RConfig.initEnCryptAndDecrypt({ it }, { it }, { it }, { it })
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val path = exchange.requestURI.rawPath
                requestedPaths += path
                val body = when (path.trimEnd('/')) {
                    "/dav" -> multistatus(collection("/dav/"), collection("/dav/photos/"))
                    "/dav/photos" -> multistatus(collection("/dav/photos/"), file("/dav/photos/a.jpg"))
                    else -> null
                }
                if (body == null) {
                    exchange.sendResponseHeaders(405, -1)
                } else {
                    val bytes = body.toByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/xml; charset=utf-8")
                    exchange.sendResponseHeaders(207, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                exchange.close()
            }
            start()
        }
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
    }

    private fun webDav(path: String) = WebDav(
        url = "http://127.0.0.1:${server.address.port}/dav",
        user = "user",
        passwd = "password",
        name = "dav",
        path = path,
    )

    @Test
    fun folderPickerListsAndNavigatesUnderTheUrlPath() = runBlocking {
        val root = repo.getFileDirItems(webDav("/")).getOrThrow()
        assertEquals(listOf("/photos"), root.map { it.path })
        assertTrue(root.single().isDirectory)

        val photos = repo.getFileDirItems(webDav(root.single().path)).getOrThrow()
        assertEquals(listOf("/photos/a.jpg"), photos.map { it.path })
        assertTrue("/" !in requestedPaths, "requests must stay under /dav, got $requestedPaths")
    }

    @Test
    fun recursiveAndFlatListingResolvePathAgainstTheUrlPath() = runBlocking {
        val recursive = repo.getItems(webDav("/"), recursive = true, filter = null).getOrThrow()
        // Playback joins these server-absolute hrefs onto the origin.
        assertEquals(listOf("/dav/photos/a.jpg"), recursive.map { it.path })

        val flat = repo.getItems(webDav("/photos"), recursive = false, filter = null).getOrThrow()
        assertEquals(listOf("/dav/photos/a.jpg"), flat.map { it.path })
    }

    private fun multistatus(vararg responses: String) =
        """<D:multistatus xmlns:D="DAV:">${responses.joinToString("")}</D:multistatus>"""

    private fun collection(href: String) = response(href, "<D:resourcetype><D:collection/></D:resourcetype>")

    private fun file(href: String) = response(
        href,
        "<D:resourcetype/><D:getcontenttype>image/jpeg</D:getcontenttype><D:getcontentlength>3</D:getcontentlength>",
    )

    private fun response(href: String, props: String) =
        """<D:response><D:href>$href</D:href><D:propstat><D:prop>$props</D:prop>""" +
            """<D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>"""
}
