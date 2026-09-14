package com.alpha.showcase.common

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Ftp
import com.alpha.showcase.common.networkfile.storage.remote.RemoteStorage
import com.alpha.showcase.common.networkfile.storage.remote.Sftp
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.util.RConfig
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import okio.ByteString.Companion.encodeUtf8
import org.apache.commons.net.ftp.FTPClient
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Native network streams with bounded IO, exclusive pooled sessions and explicit ownership. */
class NetworkFileReader internal constructor(private val smbClientFactory: (SmbConfig) -> SMBClient) {
    constructor() : this(::SMBClient)
    companion object {
        private const val IO_TIMEOUT = 30_000
        private val sharedReader by lazy { NetworkFileReader() }
        fun getInstance(): NetworkFileReader = sharedReader
    }

    data class FileStreamInfo(val inputStream: InputStream, val contentLength: Long = -1L)
    private val smbConnections = ConcurrentHashMap<String, NetworkConnectionPool<SmbSession>>()
    private val ftpConnections = ConcurrentHashMap<String, NetworkConnectionPool<FTPClient>>()
    private val sftpConnections = ConcurrentHashMap<String, NetworkConnectionPool<SftpSession>>()
    private val global = Semaphore(100)
    private val smb = Semaphore(30)
    private val ftp = Semaphore(15)
    private val sftp = Semaphore(20)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cleanupJob: Job? = null

    @Synchronized
    private fun startCleanup() {
        if (cleanupJob?.isActive == true) return
        cleanupJob = scope.launch {
            while (isActive) {
                delay(30_000)
                val now = System.currentTimeMillis()
                smbConnections.values.forEach { it.cleanupIdle(now, 120_000) }
                ftpConnections.values.forEach { it.cleanupIdle(now, 120_000) }
                sftpConnections.values.forEach { it.cleanupIdle(now, 120_000) }
            }
        }
    }

    suspend fun readFile(file: NetworkFile): Result<InputStream> = readFileWithInfo(file).map { it.inputStream }

    suspend fun readFileWithInfo(file: NetworkFile): Result<FileStreamInfo> {
        startCleanup()
        var opened: InputStream? = null
        try {
            val result = withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val protocol = when (file.remote) {
                    is Smb -> smb
                    is Ftp -> ftp
                    is Sftp -> sftp
                    else -> throw IOException("Unsupported network image protocol")
                }
                global.acquire()
                var protocolAcquired = false
                var transferred = false
                try {
                    protocol.acquire()
                    protocolAcquired = true
                    val raw = when (val remote = file.remote) {
                        is Smb -> openSmb(file, remote)
                        is Ftp -> openFtp(file, remote)
                        is Sftp -> openSftp(file, remote)
                        else -> error("Unsupported protocol")
                    }
                    val stream = ReleasingInputStream(raw) { protocol.release(); global.release() }
                    opened = stream
                    transferred = true
                    currentCoroutineContext().ensureActive()
                    FileStreamInfo(stream, file.size)
                } finally {
                    if (!transferred) {
                        if (protocolAcquired) protocol.release()
                        global.release()
                    }
                }
            }
            return Result.success(result)
        } catch (failure: Throwable) {
            opened?.let { runCatching { it.close() } }
            if (failure is CancellationException) throw failure
            if (failure !is Exception) throw failure
            return Result.failure(failure)
        }
    }

    private fun poolKey(remote: RemoteStorage): String = listOf(remote.schema, remote.host, remote.port,
        remote.user, remote.passwd).joinToString("\u0000").encodeUtf8().sha256().hex()

    private suspend fun openFtp(file: NetworkFile, remote: Ftp): InputStream {
        val pool = ftpConnections.computeIfAbsent(poolKey(remote)) {
            NetworkConnectionPool(5, { createFtp(remote) }, { it.isConnected && it.sendNoOp() }, { it.disconnect() })
        }
        val lease = pool.borrow()
        try {
            val stream = lease.value.retrieveFileStream(remotePath(file.path))
            if (stream == null) {
                // A negative RETR reply has already completed the command; do not wait for another reply.
                lease.release()
                throw IOException("FTP file could not be opened (${lease.value.replyCode})")
            }
            return ReleasingInputStream(stream) { healthy ->
                var reusable = false
                try {
                    if (healthy) {
                        reusable = lease.value.completePendingCommand()
                        if (!reusable) throw IOException("FTP transfer did not complete")
                    }
                } finally { lease.release(reusable) }
            }
        } catch (failure: Throwable) {
            lease.release(false)
            throw failure
        }
    }

    private fun createFtp(remote: Ftp): FTPClient {
        val client = FTPClient()
        try {
            client.controlEncoding = Charsets.UTF_8.name()
            client.connectTimeout = IO_TIMEOUT
            client.defaultTimeout = IO_TIMEOUT
            client.setDataTimeout(Duration.ofMillis(IO_TIMEOUT.toLong()))
            client.connect(remote.host, remote.port)
            client.soTimeout = IO_TIMEOUT
            if (!client.login(remote.user, RConfig.decryptBlocking(remote.passwd))) throw IOException("FTP authentication failed")
            client.enterLocalPassiveMode()
            if (!client.setFileType(FTPClient.BINARY_FILE_TYPE)) throw IOException("FTP binary mode unavailable")
            return client
        } catch (failure: Throwable) {
            runCatching { client.disconnect() }
            throw failure
        }
    }

    private class SmbSession(val client: SMBClient, val connection: Connection, val session: Session) {
        val shares = mutableMapOf<String, DiskShare>()
        // SMBJ closes its shares before disconnecting the transport on an error.
        fun isReusable(): Boolean = connection.isConnected && shares.values.all { it.isConnected }

        fun close() {
            shares.values.forEach { runCatching { it.close() } }
            runCatching { session.close() }
            runCatching { connection.close() }
            client.close()
        }
    }

    private suspend fun openSmb(file: NetworkFile, remote: Smb): InputStream {
        val pool = smbConnections.computeIfAbsent(poolKey(remote)) {
            NetworkConnectionPool(5, { createSmb(remote) }, { it.isReusable() }, { it.close() })
        }
        val path = remotePath(file.path).trimStart('/')
        val shareName = path.substringBefore('/')
        val filePath = path.substringAfter('/', "")
        require(shareName.isNotEmpty() && filePath.isNotEmpty()) { "Invalid SMB image path" }
        var canReconnect = true
        while (true) {
            currentCoroutineContext().ensureActive()
            val lease = pool.borrow()
            try {
                val share = lease.value.shares.getOrPut(shareName) { lease.value.session.connectShare(shareName) as DiskShare }
                val smbFile = share.openFile(filePath, setOf(AccessMask.FILE_READ_DATA), null,
                    SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
                val input = try { smbFile.inputStream } catch (failure: Throwable) {
                    runCatching { smbFile.close() }
                    throw failure
                }
                return ReleasingInputStream(input) { healthy ->
                    var reusable = healthy
                    try { smbFile.close() } catch (failure: Throwable) { reusable = false; throw failure }
                    finally { lease.release(reusable) }
                }
            } catch (failure: Throwable) {
                // A share can close after borrow's validation. Retry only a lost session,
                // before exposing a stream; file errors and cancelled requests must propagate.
                val reconnect = canReconnect && failure is Exception && failure !is CancellationException &&
                    !lease.value.isReusable()
                lease.release(false)
                currentCoroutineContext().ensureActive()
                if (reconnect) {
                    canReconnect = false
                    continue
                }
                throw failure
            }
        }
    }

    private fun createSmb(remote: Smb): SmbSession {
        val config = SmbConfig.builder().withTimeout(IO_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
            .withSoTimeout(IO_TIMEOUT.toLong(), TimeUnit.MILLISECONDS).build()
        val client = smbClientFactory(config)
        var connection: Connection? = null
        try {
            connection = client.connect(remote.host, remote.port)
            val session = connection.authenticate(AuthenticationContext(remote.user,
                RConfig.decryptBlocking(remote.passwd).toCharArray(), null))
            return SmbSession(client, connection, session)
        } catch (failure: Throwable) {
            runCatching { connection?.close() }
            runCatching { client.close() }
            throw failure
        }
    }

    private class SftpSession(val session: com.jcraft.jsch.Session, val channel: ChannelSftp) {
        fun close() { try { channel.disconnect() } finally { session.disconnect() } }
    }

    private suspend fun openSftp(file: NetworkFile, remote: Sftp): InputStream {
        val pool = sftpConnections.computeIfAbsent(poolKey(remote)) {
            NetworkConnectionPool(5, { createSftp(remote) }, { it.session.isConnected && it.channel.isConnected }, { it.close() })
        }
        val lease = pool.borrow()
        try {
            return ReleasingInputStream(lease.value.channel.get(remotePath(file.path))) { healthy -> lease.release(healthy) }
        } catch (failure: Throwable) {
            lease.release(false)
            throw failure
        }
    }

    private fun createSftp(remote: Sftp): SftpSession {
        val session = JSch().getSession(remote.user, remote.host, remote.port)
        try {
            session.setPassword(RConfig.decryptBlocking(remote.passwd))
            session.setConfig("StrictHostKeyChecking", "no")
            session.timeout = IO_TIMEOUT
            session.connect(IO_TIMEOUT)
            val channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(IO_TIMEOUT)
            return SftpSession(session, channel)
        } catch (failure: Throwable) {
            session.disconnect()
            throw failure
        }
    }

    // Native source repositories append literal server filenames, not URL-encoded segments.
    // URI.path/URLDecoder would corrupt names containing %, +, # or ?.
    private fun remotePath(path: String): String = "/" + path.substringAfter("://").substringAfter('/', "")

    suspend fun readFiles(files: List<NetworkFile>): List<Result<InputStream>> {
        // Returning a batch of open streams while awaiting more than the pool capacity would
        // deadlock: the caller cannot close any until the whole batch returns.
        val results = mutableListOf<Result<InputStream>>()
        try {
            return withContext(Dispatchers.IO) {
                files.forEach { file ->
                    currentCoroutineContext().ensureActive()
                    var temp: java.io.File? = null
                    try {
                        val local = java.io.File.createTempFile("showcase-batch-", ".tmp").also { temp = it }
                        readFile(file).getOrThrow().use { input ->
                            local.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        results += Result.success(ReleasingInputStream(local.inputStream()) { local.delete() })
                    } catch (failure: Throwable) {
                        temp?.delete()
                        if (failure is CancellationException || failure !is Exception) throw failure
                        results += Result.failure(failure)
                    }
                }
                results
            }
        } catch (failure: Throwable) {
            results.forEach { it.getOrNull()?.let { stream -> runCatching { stream.close() } } }
            throw failure
        }
    }

    fun getConnectionStats(): Map<String, Any> = mapOf(
        "smb_pools" to smbConnections.size, "smb_connections" to smbConnections.mapValues { it.value.size() },
        "ftp_pools" to ftpConnections.size, "ftp_connections" to ftpConnections.mapValues { it.value.size() },
        "sftp_pools" to sftpConnections.size, "sftp_connections" to sftpConnections.mapValues { it.value.size() },
        "semaphore_global" to global.availablePermits, "semaphore_smb" to smb.availablePermits,
        "semaphore_ftp" to ftp.availablePermits, "semaphore_sftp" to sftp.availablePermits,
    )

    @Synchronized
    fun cleanup() {
        cleanupJob?.cancel()
        cleanupJob = null
        smbConnections.values.forEach { it.close() }; smbConnections.clear()
        ftpConnections.values.forEach { it.close() }; ftpConnections.clear()
        sftpConnections.values.forEach { it.close() }; sftpConnections.clear()
    }
}
