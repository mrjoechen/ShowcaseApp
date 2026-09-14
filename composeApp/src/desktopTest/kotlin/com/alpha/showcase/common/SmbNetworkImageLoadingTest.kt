package com.alpha.showcase.common

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.util.RConfig
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.*
import com.hierynomus.mssmb2.messages.*
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.common.SmbPath
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.connection.ConnectionContext
import com.hierynomus.smbj.connection.NegotiatedProtocol
import com.hierynomus.smbj.event.SMBEventBus
import com.hierynomus.smbj.paths.PathResolver
import com.hierynomus.smbj.server.ServerList
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.TreeConnect
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Future
import kotlin.test.*
import kotlinx.coroutines.*

/** Real SMBJ shares/files/streams; only authentication and wire replies are scripted. */
class SmbNetworkImageLoadingTest {
    @Test fun closedShareOnConnectedTransportIsReplacedBeforeNextImage() = runBlocking {
        Fixture().use { fixture ->
            fixture.readImage()
            val old = fixture.connections.single()
            // SMBJ closes shares before disconnecting its transport on a connection error.
            old.share.close()
            assertTrue(old.isConnected)
            assertFalse(old.share.isConnected)
            fixture.readImage()
            assertEquals(2, fixture.connections.size)
        }
    }

    @Test fun shareClosingBetweenBorrowAndOpenIsRecovered() = runBlocking {
        Fixture().use { fixture ->
            fixture.readImage()
            fixture.connections.single().beforeOpen = { it.close() }
            fixture.readImage()
            assertEquals(2, fixture.connections.size)
        }
    }

    @Test fun repeatedDisconnectIsRetriedOnlyOnce() = runBlocking {
        Fixture { beforeOpen = { it.close() } }.use { fixture ->
            val failure = assertFailsWith<com.hierynomus.smbj.common.SMBRuntimeException> { fixture.readImage() }
            assertEquals("DiskShare has already been closed", failure.message)
            assertEquals(2, fixture.connections.size)
            assertEquals(100, fixture.reader.getConnectionStats()["semaphore_global"])
            assertTrue(fixture.connections.all { !it.isConnected })
        }
    }

    @Test fun filePermissionErrorDoesNotReconnect() = runBlocking {
        Fixture {
            beforeOpen = { throw SMBApiException(NtStatus.STATUS_ACCESS_DENIED.value,
                SMB2MessageCommandCode.SMB2_CREATE, "Access denied", null) }
        }.use { fixture ->
            val failure = assertFailsWith<SMBApiException> { fixture.readImage() }
            assertEquals(NtStatus.STATUS_ACCESS_DENIED.value, failure.statusCode)
            assertEquals(1, fixture.connections.size)
        }
    }

    @Test fun cancelledOpenDoesNotReconnectAndReturnsCapacity() = runBlocking {
        Fixture().use { fixture ->
            fixture.readImage()
            val request = Job()
            fixture.connections.single().beforeOpen = { request.cancel(); it.close() }
            assertFailsWith<CancellationException> { withContext(request) { fixture.readImage() } }
            assertEquals(1, fixture.connections.size, "A cancelled page must not start another connection")
            assertEquals(100, fixture.reader.getConnectionStats()["semaphore_global"])
            assertEquals(30, fixture.reader.getConnectionStats()["semaphore_smb"])
            fixture.readImage()
            assertEquals(2, fixture.connections.size)
        }
    }

    @Test fun cleanupWhileOpeningDoesNotCloseTheActiveShare() = runBlocking {
        Fixture().use { fixture ->
            fixture.readImage()
            val old = fixture.connections.single()
            old.beforeOpen = { fixture.reader.cleanup() }
            fixture.readImage()
            assertFalse(old.isConnected, "The retired session must close when its stream returns")
            fixture.readImage()
            assertEquals(2, fixture.connections.size)
        }
    }

    @Test fun rapidPartialReadsKeepExclusiveReusableSessions() = runBlocking {
        Fixture().use { fixture ->
            withTimeout(10_000) {
                coroutineScope {
                    repeat(100) {
                        launch(Dispatchers.Default) {
                            fixture.reader.readFile(fixture.file).getOrThrow().use { input ->
                                assertEquals(1, input.read())
                                yield() // Another page can request a stream before this page leaves.
                            }
                        }
                    }
                }
            }
            assertTrue(fixture.connections.size in 1..5)
            assertEquals(100, fixture.reader.getConnectionStats()["semaphore_global"])
            assertEquals(30, fixture.reader.getConnectionStats()["semaphore_smb"])
            fixture.readImage()
        }
    }

    private class Fixture(private val configure: ScriptedConnection.() -> Unit = {}) : AutoCloseable {
        val connections = CopyOnWriteArrayList<ScriptedConnection>()
        val reader = NetworkFileReader { config ->
            object : SMBClient(config) {
                override fun connect(hostname: String, port: Int): Connection =
                    ScriptedConnection(config, this).apply(configure).also { connections += it }
            }
        }
        init {
            RConfig.initEnCryptAndDecrypt({ it }, { it }, { it }, { it })
        }
        val file = NetworkFile(
            fileName = "photo.jpg", path = "smb://test-smb/photos/photo.jpg", size = 3,
            mimeType = "image/jpeg", modTime = "2026-09-14", isDirectory = false,
            remote = Smb(host = "test-smb", user = "guest", passwd = "", name = "test"),
        )
        suspend fun readImage() {
            reader.readFile(file).getOrThrow().use { assertContentEquals(byteArrayOf(1, 2, 3), it.readBytes()) }
        }
        override fun close() = reader.cleanup()
    }

    private class ScriptedConnection(config: SmbConfig, client: SMBClient) :
        Connection(config, client, SMBEventBus(), ServerList()) {
        private val protocol = NegotiatedProtocol(SMB2Dialect.SMB_2_1, 65536, 65536, 65536, true)
        private var connected = true
        var beforeOpen: ((DiskShare) -> Unit)? = null
        private var readCount = 0
        private val scriptedSession: Session = object : Session(this, config, AuthenticationContext.anonymous(),
            SMBEventBus(), PathResolver.LOCAL, null, null) {
            override fun connectShare(shareName: String) = share
            @Suppress("UNCHECKED_CAST")
            override fun <T : SMB2Packet> send(packet: SMB2Packet): Future<T> {
                val response = when (packet) {
                    is SMB2CreateRequest -> {
                        readCount = 0
                        object : SMB2CreateResponse() {
                            override fun getFileAttributes() = setOf(FileAttributes.FILE_ATTRIBUTE_NORMAL)
                            override fun getFileId() = SMB2FileId(ByteArray(8), ByteArray(8))
                        }
                    }
                    is SMB2ReadRequest -> {
                        val bytes = if (readCount++ == 0) byteArrayOf(1, 2, 3) else byteArrayOf()
                        object : SMB2ReadResponse() {
                            override fun getData() = bytes
                            override fun getDataLength() = bytes.size
                        }
                    }
                    is SMB2Close -> SMB2Close()
                    is SMB2TreeDisconnect -> SMB2TreeDisconnect()
                    else -> error("Unexpected SMB packet: ${packet.javaClass.simpleName}")
                }
                return CompletableFuture.completedFuture(response as T)
            }
            override fun close() { share.close() }
        }
        private val path = SmbPath("test-smb", "photos")
        // SMBJ's ConnectionContext constructor is package-private. No socket is opened in this fixture.
        private val context = ConnectionContext::class.java.getDeclaredConstructor(
            UUID::class.java, String::class.java, Int::class.javaPrimitiveType, SmbConfig::class.java,
        ).apply { isAccessible = true }.newInstance(config.clientGuid, "test-smb", 445, config).also {
            ConnectionContext::class.java.getDeclaredField("negotiatedProtocol")
                .apply { isAccessible = true }.set(it, protocol)
        }
        private val tree = TreeConnect(1, path, scriptedSession, emptySet(), config,
            context, SMBEventBus(), emptySet(), emptySet())
        val share: DiskShare = DiskShare(path, tree, object : PathResolver by PathResolver.LOCAL {
            override fun <T> resolve(session: Session, smbPath: SmbPath, action: PathResolver.ResolveAction<T>): T {
                beforeOpen?.also { beforeOpen = null }?.invoke(share)
                return PathResolver.LOCAL.resolve(session, smbPath, action)
            }
        })
        override fun authenticate(authContext: AuthenticationContext) = scriptedSession
        override fun isConnected() = connected
        override fun close() { connected = false }
    }
}
