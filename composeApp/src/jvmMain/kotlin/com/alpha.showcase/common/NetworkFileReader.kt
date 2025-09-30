package com.alpha.showcase.common

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.storage.remote.Ftp
import com.alpha.showcase.common.networkfile.storage.remote.Sftp
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.utils.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.plus
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.ftp.FTPClient
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 优化的 NetworkFile 读取器，支持连接池和连接复用
 * 解决大量文件读取时的性能问题
 * 修复DiskShare重复关闭和并发问题
 */
class NetworkFileReader {
    
    companion object {
        private const val TAG = "NetworkFileReader"
        
        // 单例实例
        @Volatile
        private var INSTANCE: NetworkFileReader? = null
        // 超时配置
        private val connectionTimeout = 30_000L // 30秒连接超时
        private val waitForConnectionTimeout = 60_000L // 60秒等待连接超时
        private val connectionIdleTimeout = 30_000L // 30秒连接空闲超时
        
        fun getInstance(): NetworkFileReader {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkFileReader().also { 
                    INSTANCE = it
                    Log.d(TAG,  "NetworkFileReader singleton instance created")
                }
            }
        }
    }

    // 连接池
    private val smbConnections = ConcurrentHashMap<String, SmbConnectionPool>()
    private val ftpConnections = ConcurrentHashMap<String, FtpConnectionPool>()
    private val sftpConnections = ConcurrentHashMap<String, SftpConnectionPool>()
    
    // 互斥锁
    private val smbMutex = Mutex()
    private val ftpMutex = Mutex()
    private val sftpMutex = Mutex()
    
    // 动态并发控制信号量 - 根据系统资源自动调整
    private val concurrentReadSemaphore = Semaphore(100) // 增加到100个并发
    private val smbConcurrentSemaphore = Semaphore(30)   // SMB增加到30个
    private val ftpConcurrentSemaphore = Semaphore(15)   // FTP增加到15个
    private val sftpConcurrentSemaphore = Semaphore(20)  // SFTP增加到20个
    
    // 大量文件读取优化
    private val batchReadThreshold = 10 // 批量读取阈值
    private val preWarmConnections = true // 是否预热连接

    private val scope = CoroutineScope(Dispatchers.Main) + SupervisorJob()
    
    // 连接清理任务
    private var cleanupJob: Job? = null
    
    init {
        startConnectionCleanupTask()
    }

    /**
     * 文件信息数据类
     */
    data class FileStreamInfo(
        val inputStream: InputStream,
        val contentLength: Long = -1L
    )

    /**
     * 根据 NetworkFile 读取文件内容
     * @param networkFile 网络文件对象
     * @return 文件输入流
     */
    suspend fun readFile(networkFile: NetworkFile): Result<InputStream> {
        return readFileWithInfo(networkFile).map { it.inputStream }
    }

    /**
     * 根据 NetworkFile 读取文件内容，包含文件大小信息
     * @param networkFile 网络文件对象
     * @return 包含文件流和大小信息的结果
     */
    suspend fun readFileWithInfo(networkFile: NetworkFile): Result<FileStreamInfo> {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting file read: ${networkFile.path}")
        Log.d(TAG, "Current semaphore permits - Global: ${concurrentReadSemaphore.availablePermits}, SMB: ${smbConcurrentSemaphore.availablePermits}, FTP: ${ftpConcurrentSemaphore.availablePermits}, SFTP: ${sftpConcurrentSemaphore.availablePermits}")
        
        // 检查协程取消状态
        currentCoroutineContext().ensureActive()
        
        // 全局并发控制
        concurrentReadSemaphore.acquire()
        Log.d(TAG, "Acquired global semaphore for: ${networkFile.path}")
        
        return try {
            val uri = createEncodedUri(networkFile.path)
            when (uri.scheme?.lowercase()) {
                "smb" -> {
                    Log.d(TAG,  "Reading SMB file: ${networkFile.path}")
                    smbConcurrentSemaphore.acquire()
                    Log.d(TAG, "Acquired SMB semaphore, remaining permits: ${smbConcurrentSemaphore.availablePermits}")
                    val result = try {
                        readSmbFile(networkFile)
                    } finally {
                        // 错误时释放信号量，成功时在流关闭时释放
                    }
                    if (result.isFailure) {
                        Log.w(TAG, "SMB file read failed: ${networkFile.path}")
                        smbConcurrentSemaphore.release()
                        concurrentReadSemaphore.release()
                    } else {
                        val duration = System.currentTimeMillis() - startTime
                        Log.d(TAG, "SMB file read successful in ${duration}ms: ${networkFile.path}")
                    }
                    result
                }
                "ftp" -> {
                    Log.d(TAG,  "Reading FTP file: ${networkFile.path}")
                    ftpConcurrentSemaphore.acquire()
                    Log.d(TAG, "Acquired FTP semaphore, remaining permits: ${ftpConcurrentSemaphore.availablePermits}")
                    val result = try {
                        readFtpFile(networkFile)
                    } finally {
                        // 错误时释放信号量，成功时在流关闭时释放
                    }
                    if (result.isFailure) {
                        Log.w(TAG, "FTP file read failed: ${networkFile.path}")
                        ftpConcurrentSemaphore.release()
                        concurrentReadSemaphore.release()
                    } else {
                        val duration = System.currentTimeMillis() - startTime
                        Log.d(TAG, "FTP file read successful in ${duration}ms: ${networkFile.path}")
                    }
                    result
                }
                "sftp" -> {
                    Log.d(TAG,  "Reading SFTP file: ${networkFile.path}")
                    sftpConcurrentSemaphore.acquire()
                    Log.d(TAG, "Acquired SFTP semaphore, remaining permits: ${sftpConcurrentSemaphore.availablePermits}")
                    val result = try {
                        readSftpFile(networkFile)
                    } finally {
                        // 错误时释放信号量，成功时在流关闭时释放
                    }
                    if (result.isFailure) {
                        Log.w(TAG, "SFTP file read failed: ${networkFile.path}")
                        sftpConcurrentSemaphore.release()
                        concurrentReadSemaphore.release()
                    } else {
                        val duration = System.currentTimeMillis() - startTime
                        Log.d(TAG,  "SFTP file read successful in ${duration}ms: ${networkFile.path}")
                    }
                    result
                }
                else -> {
                    Log.w(TAG, "Unsupported protocol: ${uri.scheme} for file: ${networkFile.path}")
                    concurrentReadSemaphore.release()
                    Result.failure(UnsupportedOperationException("Unsupported protocol: ${uri.scheme}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file: ${networkFile.path}")
            concurrentReadSemaphore.release()
            Result.failure(e)
        }
    }

    /**
     * 读取 SMB 文件
     */
    private suspend fun readSmbFile(networkFile: NetworkFile): Result<FileStreamInfo> {
        val uri = createEncodedUri(networkFile.path)
        val remote = networkFile.remote
        
        if (remote !is Smb) {
            return Result.failure(IllegalArgumentException("NetworkFile remote must be Smb for SMB protocol"))
        }

        val connectionKey = "${remote.host}:${remote.port}:${remote.user}"
        var connection: SmbConnectionPool.SmbConnection? = null
        var pool: SmbConnectionPool? = null
        
        return try {
            pool = smbMutex.withLock {
                smbConnections.getOrPut(connectionKey) {
                    SmbConnectionPool(remote)
                }
            }
            
            connection = pool.getConnection()
            Log.d(TAG,  "Acquired SMB connection for ${networkFile.path}")
            
            // 从路径中提取共享名和文件路径，需要解码
            val decodedPath = URLDecoder.decode(uri.path, StandardCharsets.UTF_8.toString())
            val pathParts = decodedPath.removePrefix("/").split("/")
            val shareName = pathParts.first()
            val filePath = pathParts.drop(1).joinToString("/")
            
            // 获取或创建共享连接
            val share = connection.openShares.getOrPut(shareName) {
                connection.session.connectShare(shareName) as DiskShare
            }
            
            // 增加使用计数
            connection.shareUsageCount.getOrPut(shareName) { AtomicInteger(0) }.incrementAndGet()
            
            val file = share.openFile(
                filePath,
                setOf(AccessMask.FILE_READ_DATA),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
            
            val inputStream = PooledSmbInputStream(
                file.inputStream, 
                connection, 
                share, 
                file, 
                pool,
                shareName,
                getInstance()
            )
            
            Result.success(FileStreamInfo(inputStream, networkFile.size))
        } catch (e: Exception) {
            Log.w(TAG, "SMB file read failed: ${networkFile.path}")
            // 异常时释放连接
            connection?.let { conn ->
                pool?.let { p ->
                    try {
                        p.returnConnection(conn)
                    } catch (ex: Exception) {
                        Log.w(TAG, "Error returning SMB connection after failure")
                    }
                }
            }
            Result.failure(e)
        }
    }

    /**
     * 读取 FTP 文件
     */
    private suspend fun readFtpFile(networkFile: NetworkFile): Result<FileStreamInfo> {
        val uri = createEncodedUri(networkFile.path)
        val remote = networkFile.remote
        
        if (remote !is Ftp) {
            return Result.failure(IllegalArgumentException("NetworkFile remote must be Ftp for FTP protocol"))
        }

        val connectionKey = "${remote.host}:${remote.port}:${remote.user}"
        var ftpClient: FTPClient? = null
        var pool: FtpConnectionPool? = null
        
        return try {
            pool = ftpMutex.withLock {
                ftpConnections.getOrPut(connectionKey) {
                    FtpConnectionPool(remote)
                }
            }
            
            ftpClient = pool.getConnection()
            Log.d(TAG,  "Acquired FTP connection for ${networkFile.path}")
            
            val decodedPath = URLDecoder.decode(uri.path, StandardCharsets.UTF_8.toString())
            val inputStream = ftpClient.retrieveFileStream(decodedPath)
                ?: return Result.failure(Exception("Failed to retrieve FTP file: ${uri.path}"))
            
            val pooledInputStream = PooledFtpInputStream(inputStream, ftpClient, pool, getInstance())
            
            Result.success(FileStreamInfo(pooledInputStream, networkFile.size))
        } catch (e: Exception) {
            e.printStackTrace()
            Log.w(TAG, "FTP file read failed: ${networkFile.path}")
            // 异常时释放连接
            ftpClient?.let { client ->
                pool?.let { p ->
                    try {
                        p.returnConnection(client)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        Log.w(TAG, "Error returning FTP connection after failure")
                    }
                }
            }
            Result.failure(e)
        }
    }

    /**
     * 读取 SFTP 文件
     */
    private suspend fun readSftpFile(networkFile: NetworkFile): Result<FileStreamInfo> {
        val uri = createEncodedUri(networkFile.path)
        val remote = networkFile.remote
        
        if (remote !is Sftp) {
            return Result.failure(IllegalArgumentException("NetworkFile remote must be Sftp for SFTP protocol"))
        }

        val connectionKey = "${remote.host}:${remote.port}:${remote.user}"
        var connection: SftpConnectionPool.SftpConnection? = null
        var pool: SftpConnectionPool? = null
        
        return try {
            pool = sftpMutex.withLock {
                sftpConnections.getOrPut(connectionKey) {
                    SftpConnectionPool(remote)
                }
            }
            
            connection = pool.getConnection()
            Log.d(TAG,  "Acquired SFTP connection for ${networkFile.path}")
            
            val decodedPath = URLDecoder.decode(uri.path, StandardCharsets.UTF_8.toString())
            
            val inputStream = connection.channel.get(decodedPath)
            val pooledInputStream = PooledSftpInputStream(inputStream, connection, pool, getInstance())
            val fileInfo = FileStreamInfo(pooledInputStream, networkFile.size)
            Result.success(fileInfo)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.w(TAG, "SFTP file read failed: ${networkFile.path}")
            // 异常时释放连接
            connection?.let { conn ->
                pool?.let { p ->
                    try {
                        p.returnConnection(conn)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        Log.w(TAG, "Error returning SFTP connection after failure")
                    }
                }
            }
            Result.failure(e)
        }
    }

    /**
     * 启动连接清理任务
     */
    private fun startConnectionCleanupTask() {
        cleanupJob = scope.launch {
            while (isActive) {
                try {
                    delay(30_000L) // 每30秒检查一次
                    cleanupIdleConnections()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.w(TAG, "Connection cleanup task error")
                }
            }
        }
        Log.d(TAG,  "Connection cleanup task started")
    }
    
    /**
     * 清理空闲连接
     */
    private suspend fun cleanupIdleConnections() {
        val currentTime = System.currentTimeMillis()
        var cleanedCount = 0
        
        // 清理SMB空闲连接
        smbMutex.withLock {
            val iterator = smbConnections.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val pool = entry.value
                val cleaned = pool.cleanupIdleConnections(currentTime, connectionIdleTimeout)
                if (cleaned > 0) {
                    cleanedCount += cleaned
                    if (pool.isEmpty()) {
                        iterator.remove()
                        Log.d(TAG,  "Removed empty SMB pool for ${entry.key}")
                    }
                }
            }
        }
        
        // 清理FTP空闲连接
        ftpMutex.withLock {
            val iterator = ftpConnections.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val pool = entry.value
                val cleaned = pool.cleanupIdleConnections(currentTime, connectionIdleTimeout)
                if (cleaned > 0) {
                    cleanedCount += cleaned
                    if (pool.isEmpty()) {
                        iterator.remove()
                        Log.d(TAG,  "Removed empty FTP pool for ${entry.key}")
                    }
                }
            }
        }
        
        // 清理SFTP空闲连接
        sftpMutex.withLock {
            val iterator = sftpConnections.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val pool = entry.value
                val cleaned = pool.cleanupIdleConnections(currentTime, connectionIdleTimeout)
                if (cleaned > 0) {
                    cleanedCount += cleaned
                    if (pool.isEmpty()) {
                        iterator.remove()
                        Log.d(TAG,  "Removed empty SFTP pool for ${entry.key}")
                    }
                }
            }
        }
        
        if (cleanedCount > 0) {
            Log.i(TAG, "Cleaned up $cleanedCount idle connections")
        }
    }
    
    /**
     * 批量读取文件，优化大量文件读取性能
     */
    suspend fun readFiles(networkFiles: List<NetworkFile>): List<Result<InputStream>> {
        if (networkFiles.isEmpty()) return emptyList()
        
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "Starting batch file read for ${networkFiles.size} files")
        
        // 按协议分组
        val filesByProtocol = networkFiles.groupBy { 
            try {
                createEncodedUri(it.path).scheme?.lowercase()
            } catch (e: Exception) {
                "unknown"
            }
        }
        
        // 预热连接池
        if (preWarmConnections && networkFiles.size >= batchReadThreshold) {
            preWarmConnectionPools(filesByProtocol)
        }
        
        // 并发读取文件
        val results = coroutineScope {
            networkFiles.map { file ->
                async {
                    readFile(file)
                }
            }.map { it.await() }
        }
        
        val duration = System.currentTimeMillis() - startTime
        val successCount = results.count { it.isSuccess }
        Log.i(TAG, "Batch file read completed: $successCount/${networkFiles.size} successful in ${duration}ms")
        
        return results
    }
    
    /**
     * 预热连接池，为大量文件读取做准备
     */
    private suspend fun preWarmConnectionPools(filesByProtocol: Map<String?, List<NetworkFile>>) {
        Log.d(TAG,  "Pre-warming connection pools")
        
        filesByProtocol.forEach { (protocol, files) ->
            when (protocol) {
                "smb" -> {
                    val connectionKeys = files.mapNotNull { file ->
                        val remote = file.remote as? Smb
                        remote?.let { "${it.host}:${it.port}:${it.user}" }
                    }.distinct()
                    
                    connectionKeys.forEach { key ->
                        val file = files.find { 
                            val remote = it.remote as? Smb
                            remote?.let { "${it.host}:${it.port}:${it.user}" } == key
                        }
                        file?.let { preWarmSmbPool(it.remote as Smb, key) }
                    }
                }
                "ftp" -> {
                    val connectionKeys = files.mapNotNull { file ->
                        val remote = file.remote as? Ftp
                        remote?.let { "${it.host}:${it.port}:${it.user}" }
                    }.distinct()
                    
                    connectionKeys.forEach { key ->
                        val file = files.find { 
                            val remote = it.remote as? Ftp
                            remote?.let { "${it.host}:${it.port}:${it.user}" } == key
                        }
                        file?.let { preWarmFtpPool(it.remote as Ftp, key) }
                    }
                }
                "sftp" -> {
                    val connectionKeys = files.mapNotNull { file ->
                        val remote = file.remote as? Sftp
                        remote?.let { "${it.host}:${it.port}:${it.user}" }
                    }.distinct()
                    
                    connectionKeys.forEach { key ->
                        val file = files.find { 
                            val remote = it.remote as? Sftp
                            remote?.let { "${it.host}:${it.port}:${it.user}" } == key
                        }
                        file?.let { preWarmSftpPool(it.remote as Sftp, key) }
                    }
                }
            }
        }
    }
    
    private suspend fun preWarmSmbPool(remote: Smb, connectionKey: String) {
        try {
            val pool = smbMutex.withLock {
                smbConnections.getOrPut(connectionKey) { SmbConnectionPool(remote) }
            }
            // 预创建2个连接
            repeat(2) {
                try {
                    val conn = pool.getConnection()
                    pool.returnConnection(conn)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.w(TAG, "Failed to pre-warm SMB connection")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.w(TAG, "Error pre-warming SMB pool")
        }
    }
    
    private suspend fun preWarmFtpPool(remote: Ftp, connectionKey: String) {
        try {
            val pool = ftpMutex.withLock {
                ftpConnections.getOrPut(connectionKey) { FtpConnectionPool(remote) }
            }
            // 预创建2个连接
            repeat(2) {
                try {
                    val conn = pool.getConnection()
                    pool.returnConnection(conn)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.w(TAG, "Failed to pre-warm FTP connection")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.w(TAG, "Error pre-warming FTP pool")
        }
    }
    
    private suspend fun preWarmSftpPool(remote: Sftp, connectionKey: String) {
        try {
            val pool = sftpMutex.withLock {
                sftpConnections.getOrPut(connectionKey) { SftpConnectionPool(remote) }
            }
            // 预创建2个连接
            repeat(2) {
                try {
                    val conn = pool.getConnection()
                    pool.returnConnection(conn)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.w(TAG, "Failed to pre-warm SFTP connection")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.w(TAG, "Error pre-warming SFTP pool")
        }
    }
    
    /**
     * 获取连接池统计信息
     */
    fun getConnectionStats(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()
        
        // SMB统计
        val smbStats = mutableMapOf<String, Int>()
        smbConnections.forEach { (key, pool) ->
            smbStats[key] = pool.getConnectionCount()
        }
        stats["smb_pools"] = smbConnections.size
        stats["smb_connections"] = smbStats
        
        // FTP统计
        val ftpStats = mutableMapOf<String, Int>()
        ftpConnections.forEach { (key, pool) ->
            ftpStats[key] = pool.getConnectionCount()
        }
        stats["ftp_pools"] = ftpConnections.size
        stats["ftp_connections"] = ftpStats
        
        // SFTP统计
        val sftpStats = mutableMapOf<String, Int>()
        sftpConnections.forEach { (key, pool) ->
            sftpStats[key] = pool.getConnectionCount()
        }
        stats["sftp_pools"] = sftpConnections.size
        stats["sftp_connections"] = sftpStats
        
        // 信号量统计
        stats["semaphore_global"] = concurrentReadSemaphore.availablePermits
        stats["semaphore_smb"] = smbConcurrentSemaphore.availablePermits
        stats["semaphore_ftp"] = ftpConcurrentSemaphore.availablePermits
        stats["semaphore_sftp"] = sftpConcurrentSemaphore.availablePermits
        
        // 性能统计
        stats["batch_threshold"] = batchReadThreshold
        stats["pre_warm_enabled"] = preWarmConnections
        
        return stats
    }
    
    /**
     * 清理所有连接池
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up all connection pools")
        val stats = getConnectionStats()
        Log.d(TAG,  "Connection stats before cleanup: $stats")
        
        // 停止清理任务
        cleanupJob?.cancel()
        cleanupJob = null
        
        smbConnections.values.forEach { it.cleanup() }
        ftpConnections.values.forEach { it.cleanup() }
        sftpConnections.values.forEach { it.cleanup() }
        
        smbConnections.clear()
        ftpConnections.clear()
        sftpConnections.clear()
        
        Log.i(TAG, "All connection pools cleaned up")
    }

    /**
     * 创建编码后的URI，支持中文字符
     */
    private fun createEncodedUri(path: String): URI {
        return try {
            // 先尝试直接创建URI
            URI.create(path)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG,  "URI contains non-ASCII characters, encoding path: $path")
            // 如果包含非ASCII字符，需要编码
            val parts = path.split("://", limit = 2)
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid URI format: $path")
            }
            
            val scheme = parts[0]
            val authority = parts[1].substringBefore("/")
            val pathPart = parts[1].substringAfter("/", "")
            
            val encodedPath = if (pathPart.isNotEmpty()) {
                pathPart.split("/").joinToString("/") { segment ->
                    URLEncoder.encode(segment, StandardCharsets.UTF_8.toString())
                        .replace("+", "%20")
                }
            } else ""
            
            val encodedUri = if (encodedPath.isNotEmpty()) {
                "$scheme://$authority/$encodedPath"
            } else {
                "$scheme://$authority"
            }
            
            Log.d(TAG,  "Encoded URI: $encodedUri")
            URI.create(encodedUri)
        }
    }

    // ============= 连接池实现 =============

    /**
     * SMB 连接池
     */
    private class SmbConnectionPool(private val remote: Smb, private val maxConnections: Int = 10) {
        private val connections = mutableListOf<SmbConnection>()
        private val mutex = Mutex()
        
        data class SmbConnection(
            val client: SMBClient,
            val connection: Connection,
            val session: Session,
            var currentUsage: AtomicInteger = AtomicInteger(0), // 当前使用数量
            var lastUsed: Long = System.currentTimeMillis(),
            val openShares: MutableMap<String, DiskShare> = mutableMapOf(),
            val shareUsageCount: MutableMap<String, AtomicInteger> = mutableMapOf()
        ) {
            val maxConcurrentUsage = 10 // 每个连接最大并发使用数

            fun inUse (): Boolean {
                return currentUsage.get() > 0
            }

            fun available(): Boolean {
                return currentUsage.get() < maxConcurrentUsage
            }
        }
        
        suspend fun getConnection(): SmbConnection = mutex.withLock {
            val totalUsage = connections.sumOf { it.currentUsage.get() }
            Log.d(TAG,  "SMB pool stats - Total: ${connections.size}/$maxConnections, TotalUsage: $totalUsage")
            
            // 寻找可以共享的连接（未达到最大并发数且连接有效）
            val available = connections.find { 
                it.available() && isConnectionValid(it)
            }
            if (available != null) {
                available.currentUsage.incrementAndGet()
                available.lastUsed = System.currentTimeMillis()
                Log.d(TAG,  "Reusing SMB connection (usage: ${available.currentUsage.get()}/${available.maxConcurrentUsage}) for ${remote.host}:${remote.port}")
                return available
            }
            
            // 创建新连接
            if (connections.size < maxConnections) {
                Log.d(TAG,  "Creating new SMB connection for ${remote.host}:${remote.port} (${connections.size + 1}/$maxConnections)")
                val newConnection = createConnection()
                newConnection.currentUsage.incrementAndGet() // 标记为使用中
                connections.add(newConnection)
                return newConnection
            }
            
            // 等待连接释放或寻找使用较少的连接
            val leastUsed = connections.minByOrNull { it.currentUsage.get() }
            if (leastUsed != null && isConnectionValid(leastUsed)) {
                leastUsed.currentUsage.incrementAndGet()
                leastUsed.lastUsed = System.currentTimeMillis()
                Log.d(TAG,  "Reusing least used SMB connection (usage: ${leastUsed.currentUsage.get()}/${leastUsed.maxConcurrentUsage}) for ${remote.host}:${remote.port}")
                return leastUsed
            } else if (leastUsed != null) {
                Log.d(TAG,  "SMB connection invalid, recreating for ${remote.host}:${remote.port}")
                closeConnection(leastUsed)
                connections.remove(leastUsed)
                val newConnection = createConnection()
                newConnection.currentUsage.incrementAndGet()
                connections.add(newConnection)
                return newConnection
            }
            
            // 所有连接都达到最大并发，等待连接释放
            Log.w(TAG, "All SMB connections at max capacity for ${remote.host}:${remote.port}, waiting...")
            var maxWait = 0
            while (maxWait < waitForConnectionTimeout) {
                // 释放锁并等待，避免阻塞其他线程
                mutex.unlock()
                try {
                    delay(100)
                    maxWait += 100
                } finally {
                    mutex.lock()
                }
                
                // 重新检查是否有可用连接，使用原子操作确保并发安全
                val nowAvailable = connections.find { 
                    it.available() && isConnectionValid(it)
                }
                if (nowAvailable != null) {
                    // 双重检查，确保连接仍然可用
                    if (nowAvailable.available() && isConnectionValid(nowAvailable)) {
                        nowAvailable.currentUsage.incrementAndGet()
                        nowAvailable.lastUsed = System.currentTimeMillis()
                        Log.d(TAG,  "SMB connection became available after ${maxWait}ms (usage: ${nowAvailable.currentUsage.get()}/${nowAvailable.maxConcurrentUsage}) for ${remote.host}:${remote.port}")
                        return nowAvailable
                    }
                }
            }
            
            // 如果等待超时，抛出异常
            Log.w(TAG, "SMB connection timeout after ${maxWait}ms for ${remote.host}:${remote.port}")
            throw Exception("All SMB connections are in use after waiting ${maxWait}ms")
        }
        
        suspend fun returnConnection(connection: SmbConnection) = mutex.withLock {
            val currentUsage = connection.currentUsage.decrementAndGet()
            connection.lastUsed = System.currentTimeMillis()
            Log.d(TAG,  "Returned SMB connection for ${remote.host}:${remote.port}, current usage: $currentUsage/${connection.maxConcurrentUsage}, total connections: ${connections.size}")
        }
        
        private fun createConnection(): SmbConnection {
            val startTime = System.currentTimeMillis()
            Log.d(TAG,  "Creating SMB connection to ${remote.host}:${remote.port}")
            
            val client = SMBClient()
            val connection = client.connect(remote.host, remote.port)
            val authContext = AuthenticationContext(
                remote.user,
                RConfig.decrypt(remote.passwd).toCharArray(),
                null
            )
            val session = connection.authenticate(authContext)
            
            val duration = System.currentTimeMillis() - startTime
            Log.i(TAG, "SMB connection created successfully in ${duration}ms for ${remote.host}:${remote.port}")
            
            return SmbConnection(client, connection, session, AtomicInteger(0), System.currentTimeMillis())
        }
        
        private fun isConnectionValid(conn: SmbConnection): Boolean {
            return try {
                val isValid = conn.connection.isConnected
                if (!isValid) {
                    Log.d(TAG,  "SMB connection validation failed for ${remote.host}:${remote.port}")
                }
                isValid
            } catch (e: Exception) {
                Log.w(TAG, "SMB connection validation error for ${remote.host}:${remote.port}: ${e.message}")
                false
            }
        }
        
        private fun closeConnection(conn: SmbConnection) {
            Log.d(TAG,  "Closing SMB connection for ${remote.host}:${remote.port}")
            try {
                // 先关闭所有共享
                conn.openShares.values.forEach { share ->
                    try {
                        share.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error closing SMB share: ${e.message}")
                    }
                }
                conn.openShares.clear()
                conn.shareUsageCount.clear()
                
                conn.session.close()
                conn.connection.close()
                conn.client.close()
                Log.d(TAG,  "SMB connection closed successfully for ${remote.host}:${remote.port}")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing SMB connection for ${remote.host}:${remote.port}: ${e.message}")
            }
        }
        
        // 减少共享使用计数，必要时关闭共享
        suspend fun releaseShare(connection: SmbConnection, shareName: String) = mutex.withLock {
            val counter = connection.shareUsageCount[shareName]
            if (counter != null) {
                val currentCount = counter.decrementAndGet()
                Log.d(TAG,  "Released SMB share $shareName, remaining usage: $currentCount")
                
                // 只有当没有使用者且连接不再使用时才关闭共享
                if (currentCount <= 0 && !connection.inUse()) {
                    connection.openShares[shareName]?.let { share ->
                        try {
                            // 检查share是否已经关闭
                            if (!share.isConnected) {
                                Log.d(TAG,  "SMB share $shareName already closed")
                            } else {
                                share.close()
                                Log.d(TAG,  "SMB share $shareName closed successfully")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Log.w(TAG, "Error closing SMB share $shareName: ${e.message}")
                        }
                    }
                    connection.openShares.remove(shareName)
                    connection.shareUsageCount.remove(shareName)
                }
            }
        }
        
        /**
         * 清理空闲连接
         */
        suspend fun cleanupIdleConnections(currentTime: Long, idleTimeout: Long): Int = mutex.withLock {
            var cleanedCount = 0
            val iterator = connections.iterator()
            
            while (iterator.hasNext()) {
                val conn = iterator.next()
                if (!conn.inUse() && (currentTime - conn.lastUsed) > idleTimeout) {
                    Log.d(TAG,  "Closing idle SMB connection for ${remote.host}:${remote.port}, idle for ${(currentTime - conn.lastUsed)/1000}s")
                    closeConnection(conn)
                    iterator.remove()
                    cleanedCount++
                }
            }
            
            if (cleanedCount > 0) {
                Log.i(TAG, "Cleaned up $cleanedCount idle SMB connections for ${remote.host}:${remote.port}")
            }
            
            cleanedCount
        }
        
        fun isEmpty(): Boolean = connections.isEmpty()
        
        fun getConnectionCount(): Int = connections.size
        
        fun cleanup() {
            connections.forEach { closeConnection(it) }
            connections.clear()
        }
    }

    /**
     * FTP 连接池
     */
    private class FtpConnectionPool(private val remote: Ftp, private val maxConnections: Int = 65) {
        private val connections = mutableListOf<FtpConnection>()
        private val mutex = Mutex()
        
        data class FtpConnection(
            val client: FTPClient,
            var inUse: Boolean = false,
            var lastUsed: Long = System.currentTimeMillis()
        )
        
        suspend fun getConnection(): FTPClient = mutex.withLock {
            Log.d(TAG,  "FTP pool stats - Total: ${connections.size}/$maxConnections, InUse: ${connections.count { it.inUse }}")
            
            // 寻找可用连接
            val available = connections.find { !it.inUse && isConnectionValid(it.client) }
            if (available != null) {
                available.inUse = true
                available.lastUsed = System.currentTimeMillis()
                Log.d(TAG,  "Reusing FTP connection for ${remote.host}:${remote.port}")
                return available.client
            }
            
            // 创建新连接
            if (connections.size < maxConnections) {
                Log.d(TAG,  "Creating new FTP connection for ${remote.host}:${remote.port} (${connections.size + 1}/$maxConnections)")
                val ftpClient = createConnection()
                val ftpConnection = FtpConnection(ftpClient, true, System.currentTimeMillis())
                connections.add(ftpConnection)
                return ftpClient
            }
            
            // 等待连接可用
            val oldest = connections.filter { !it.inUse }.minByOrNull { it.lastUsed }
            if (oldest != null) {
                if (isConnectionValid(oldest.client)) {
                    oldest.inUse = true
                    oldest.lastUsed = System.currentTimeMillis()
                    Log.d(TAG,  "Reusing oldest FTP connection for ${remote.host}:${remote.port}")
                    return oldest.client
                } else {
                    Log.d(TAG,  "FTP connection invalid, recreating for ${remote.host}:${remote.port}")
                    closeConnection(oldest.client)
                    connections.remove(oldest)
                    val ftpClient = createConnection()
                    val ftpConnection = FtpConnection(ftpClient, true, System.currentTimeMillis())
                    connections.add(ftpConnection)
                    return ftpClient
                }
            }
            
            // 所有连接都在使用中，等待连接释放
            Log.w(TAG, "All FTP connections busy for ${remote.host}:${remote.port}, waiting...")
            var maxWait = 0
            while (maxWait < waitForConnectionTimeout) {
                delay(100)
                maxWait += 100
                
                val nowAvailable = connections.find { !it.inUse && isConnectionValid(it.client) }
                if (nowAvailable != null) {
                    nowAvailable.inUse = true
                    nowAvailable.lastUsed = System.currentTimeMillis()
                    Log.d(TAG,  "FTP connection became available after ${maxWait}ms for ${remote.host}:${remote.port}")
                    return nowAvailable.client
                }
            }
            
            Log.w(TAG, "FTP connection timeout after ${maxWait}ms for ${remote.host}:${remote.port}")
            throw Exception("All FTP connections are in use after waiting ${maxWait}ms")
        }
        
        suspend fun returnConnection(ftpClient: FTPClient) = mutex.withLock {
            val connection = connections.find { it.client == ftpClient }
            if (connection != null) {
                connection.inUse = false
                connection.lastUsed = System.currentTimeMillis()
                Log.d(TAG,  "Returned FTP connection for ${remote.host}:${remote.port}, available: ${connections.count { !it.inUse }}/${connections.size}")
                
                // 完成传输命令
                try {
                    if (ftpClient.isConnected) {
                        ftpClient.completePendingCommand()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error completing FTP command")
                }
            } else {
                // 连接不在池中，直接关闭
                closeConnection(ftpClient)
            }
        }
        
        private fun createConnection(): FTPClient {
            val startTime = System.currentTimeMillis()
            Log.d(TAG,  "Creating FTP connection to ${remote.host}:${remote.port}")
            
            val ftpClient = FTPClient()
            
            // 设置控制连接编码为UTF-8以支持中文
            ftpClient.controlEncoding = StandardCharsets.UTF_8.name()
            
            ftpClient.connect(remote.host, remote.port)
            ftpClient.login(remote.user, RConfig.decrypt(remote.passwd))
            ftpClient.enterLocalPassiveMode()
            ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE)
            
            // 尝试发送OPTS UTF8 ON命令来启用UTF-8支持
            try {
                ftpClient.sendCommand("OPTS UTF8 ON")
            } catch (e: Exception) {
                // 如果服务器不支持UTF8命令，忽略错误
                Log.d(TAG,  "FTP server does not support UTF8 command: ${e.message}")
            }
            
            val duration = System.currentTimeMillis() - startTime
            Log.i(TAG, "FTP connection created successfully in ${duration}ms for ${remote.host}:${remote.port}")
            
            return ftpClient
        }
        
        private fun isConnectionValid(client: FTPClient): Boolean {
            return try {
                val isValid = client.isConnected && client.sendNoOp()
                if (!isValid) {
                    Log.d(TAG,  "FTP connection validation failed for ${remote.host}:${remote.port}")
                }
                isValid
            } catch (e: Exception) {
                Log.w(TAG, "FTP connection validation error for ${remote.host}:${remote.port}: ${e.message}")
                false
            }
        }
        
        private fun closeConnection(client: FTPClient) {
            Log.d(TAG,  "Closing FTP connection for ${remote.host}:${remote.port}")
            try {
                if (client.isConnected) {
                    client.logout()
                    client.disconnect()
                }
                Log.d(TAG,  "FTP connection closed successfully for ${remote.host}:${remote.port}")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing FTP connection for ${remote.host}:${remote.port}: ${e.message}")
            }
        }
        
        /**
         * 清理空闲连接
         */
        suspend fun cleanupIdleConnections(currentTime: Long, idleTimeout: Long): Int = mutex.withLock {
            var cleanedCount = 0
            val iterator = connections.iterator()
            
            while (iterator.hasNext()) {
                val conn = iterator.next()
                if (!conn.inUse && (currentTime - conn.lastUsed) > idleTimeout) {
                    Log.d(TAG,  "Closing idle FTP connection for ${remote.host}:${remote.port}, idle for ${(currentTime - conn.lastUsed)/1000}s")
                    closeConnection(conn.client)
                    iterator.remove()
                    cleanedCount++
                } else if (!isConnectionValid(conn.client)) {
                    // 清理无效连接
                    closeConnection(conn.client)
                    iterator.remove()
                    cleanedCount++
                }
            }
            
            if (cleanedCount > 0) {
                Log.i(TAG, "Cleaned up $cleanedCount idle FTP connections for ${remote.host}:${remote.port}")
            }
            
            cleanedCount
        }
        
        fun isEmpty(): Boolean = connections.isEmpty()
        
        fun getConnectionCount(): Int = connections.size
        
        fun cleanup() {
            connections.forEach { conn ->
                closeConnection(conn.client)
            }
            connections.clear()
        }
    }

    /**
     * SFTP 连接池
     */
    private class SftpConnectionPool(private val remote: Sftp, private val maxConnections: Int = 65) {
        private val connections = mutableListOf<SftpConnection>()
        private val mutex = Mutex()
        data class SftpConnection(
            val session: com.jcraft.jsch.Session,
            val channel: ChannelSftp,
            var inUse: Boolean = false,
            var lastUsed: Long = System.currentTimeMillis()
        )
        
        suspend fun getConnection(): SftpConnection = mutex.withLock {
            // 寻找可用连接
            val available = connections.find { !it.inUse && it.session.isConnected && it.channel.isConnected }
            if (available != null) {
                available.inUse = true
                available.lastUsed = System.currentTimeMillis()
                return available
            }
            
            // 创建新连接
            if (connections.size < maxConnections) {
                val newConnection = createConnection()
                connections.add(newConnection)
                return newConnection
            }
            
            // 重用最老的未使用连接
            val oldest = connections.filter { !it.inUse }.minByOrNull { it.lastUsed }
            if (oldest != null) {
                closeConnection(oldest)
                connections.remove(oldest)
                val newConnection = createConnection()
                connections.add(newConnection)
                return newConnection
            }

            var maxWait = 0
            while (maxWait < waitForConnectionTimeout) {
                delay(100)
                maxWait+= 100
                // 重新检查是否有可用连接
                val nowAvailable = connections.find { !it.inUse && it.session.isConnected && it.channel.isConnected }
                if (nowAvailable != null) {
                    nowAvailable.inUse = true
                    nowAvailable.lastUsed = System.currentTimeMillis()
                    return nowAvailable
                }
            }
            
            // 如果等待超时，抛出异常
            throw Exception("All SFTP connections are in use after waiting ${waitForConnectionTimeout}ms")
        }
        
        suspend fun returnConnection(connection: SftpConnection) = mutex.withLock {
            connection.inUse = false
            connection.lastUsed = System.currentTimeMillis()
        }
        
        private fun createConnection(): SftpConnection {
            val jsch = JSch()
            val session = jsch.getSession(remote.user, remote.host, remote.port)
            session.setPassword(RConfig.decrypt(remote.passwd))
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect()
            
            val channel = session.openChannel("sftp")
            channel.connect()
            val sftpChannel = channel as ChannelSftp
            
            return SftpConnection(session, sftpChannel, true, System.currentTimeMillis())
        }
        
        private fun closeConnection(conn: SftpConnection) {
            try {
                conn.channel.disconnect()
                conn.session.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                Log.w(TAG, "Error closing SFTP connection")
            }
        }
        
        /**
         * 清理空闲连接
         */
        suspend fun cleanupIdleConnections(currentTime: Long, idleTimeout: Long): Int = mutex.withLock {
            var cleanedCount = 0
            val iterator = connections.iterator()
            
            while (iterator.hasNext()) {
                val conn = iterator.next()
                if (!conn.inUse && (currentTime - conn.lastUsed) > idleTimeout) {
                    Log.d(TAG,  "Closing idle SFTP connection for ${remote.host}:${remote.port}, idle for ${(currentTime - conn.lastUsed)/1000}s")
                    closeConnection(conn)
                    iterator.remove()
                    cleanedCount++
                }
            }
            
            if (cleanedCount > 0) {
                Log.i(TAG, "Cleaned up $cleanedCount idle SFTP connections for ${remote.host}:${remote.port}")
            }
            
            cleanedCount
        }
        
        fun isEmpty(): Boolean = connections.isEmpty()
        
        fun getConnectionCount(): Int = connections.size
        
        fun cleanup() {
            connections.forEach { closeConnection(it) }
            connections.clear()
        }
    }

    // ============= 池化输入流实现 =============

    /**
     * 池化的 SMB 输入流
     */
    private class PooledSmbInputStream(
        private val inputStream: InputStream,
        private val connection: SmbConnectionPool.SmbConnection,
        private val share: DiskShare?,
        private val file: File?,
        private val pool: SmbConnectionPool,
        private val shareName: String,
        private val reader: NetworkFileReader
    ) : InputStream() {
        
        @Volatile
        private var closed = false

        override fun read(): Int = inputStream.read()
        override fun read(b: ByteArray): Int = inputStream.read(b)
        override fun read(b: ByteArray, off: Int, len: Int): Int = inputStream.read(b, off, len)
        override fun available(): Int = inputStream.available()
        
        override fun close() {
            if (closed) {
                Log.d(TAG,  "SMB InputStream already closed for share: $shareName")
                return
            }
            
            synchronized(this) {
                if (closed) {
                    return
                }
                closed = true
            }
            
            try {
                Log.d(TAG,  "Closing SMB InputStream for share: $shareName")
                inputStream.close()
                
                // 安全地关闭文件，处理share已关闭的情况
                file?.let { smbFile ->
                    try {
                        // 检查share是否仍然连接
                        if (share?.isConnected == true) {
                            smbFile.close()
                        } else {
                            Log.d(TAG,  "SMB share $shareName already closed, skipping file close")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error closing SMB file on share $shareName: ${e.message}")
                    }
                }
                
                // 释放共享和连接，但不直接关闭share
                runBlocking {
                    pool.releaseShare(connection, shareName)
                    pool.returnConnection(connection)
                    reader.smbConcurrentSemaphore.release()
                    reader.concurrentReadSemaphore.release()
                }
                Log.d(TAG,  "SMB InputStream closed successfully for share: $shareName")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing SMB InputStream for share: $shareName")
            }
        }
    }

    /**
     * 池化的 FTP 输入流
     */
    private class PooledFtpInputStream(
        private val inputStream: InputStream,
        private val ftpClient: FTPClient,
        private val pool: FtpConnectionPool,
        private val reader: NetworkFileReader
    ) : InputStream() {
        
        @Volatile
        private var closed = false

        override fun read(): Int = inputStream.read()
        override fun read(b: ByteArray): Int = inputStream.read(b)
        override fun read(b: ByteArray, off: Int, len: Int): Int = inputStream.read(b, off, len)
        override fun available(): Int = inputStream.available()
        
        override fun close() {
            if (closed) {
                Log.d(TAG,  "FTP InputStream already closed")
                return
            }
            
            synchronized(this) {
                if (closed) {
                    return
                }
                closed = true
            }
            
            try {
                Log.d(TAG,  "Closing FTP InputStream")
                inputStream.close()
                
                // 归还连接到池中
                runBlocking {
                    pool.returnConnection(ftpClient)
                    reader.ftpConcurrentSemaphore.release()
                    reader.concurrentReadSemaphore.release()
                }
                Log.d(TAG,  "FTP InputStream closed successfully")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.w(TAG, "Error closing FTP InputStream")
            }
        }
    }

    /**
     * 池化的 SFTP 输入流
     */
    private class PooledSftpInputStream(
        private val inputStream: InputStream,
        private val connection: SftpConnectionPool.SftpConnection,
        private val pool: SftpConnectionPool,
        private val reader: NetworkFileReader
    ) : InputStream() {
        
        @Volatile
        private var closed = false

        override fun read(): Int = inputStream.read()
        override fun read(b: ByteArray): Int = inputStream.read(b)
        override fun read(b: ByteArray, off: Int, len: Int): Int = inputStream.read(b, off, len)
        override fun available(): Int = inputStream.available()
        
        override fun close() {
            if (closed) {
                Log.d(TAG,  "SFTP InputStream already closed")
                return
            }
            
            synchronized(this) {
                if (closed) {
                    return
                }
                closed = true
            }
            
            try {
                Log.d(TAG,  "Closing SFTP InputStream")
                inputStream.close()
                
                // 归还连接到池中
                runBlocking {
                    pool.returnConnection(connection)
                    reader.sftpConcurrentSemaphore.release()
                    reader.concurrentReadSemaphore.release()
                }
                Log.d(TAG,  "SFTP InputStream closed successfully")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.w(TAG,  "Error closing SFTP InputStream")
            }
        }
    }
}