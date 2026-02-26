package com.alpha.showcase.common

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.repo.SmbSourceRepo
import com.alpha.showcase.common.utils.getMimeType
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.RPCTransport
import com.rapid7.client.dcerpc.transport.SMBTransportFactories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class NativeSmbSourceRepo : SmbSourceRepo {

    private var smbClient: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    override suspend fun getItem(remoteApi: Smb): Result<NetworkFile> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: Smb,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?
    ): Result<List<NetworkFile>> {
        val files = mutableListOf<NetworkFile>()
        val streamResult = streamItems(remoteApi, recursive, filter) { batch ->
            files.addAll(batch)
        }
        return if (streamResult.isSuccess) {
            Result.success(files)
        } else {
            Result.failure(streamResult.exceptionOrNull() ?: Exception("SMB operation failed"))
        }
    }

    override suspend fun streamItems(
        remoteApi: Smb,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?,
        batchSize: Int,
        onBatch: suspend (List<NetworkFile>) -> Unit
    ): Result<Long> {
        val safeBatchSize = batchSize.coerceAtLeast(1)
        return try {
            initConnection(remoteApi, 15000)
            val total = withContext(Dispatchers.IO) {
                withTimeout(30000) {
                    if (recursive) {
                        streamFilesRecursively(remoteApi, filter, safeBatchSize, onBatch)
                    } else {
                        streamFilesInCurrentDirectory(remoteApi, filter, safeBatchSize, onBatch)
                    }
                }
            }

            Result.success(total)
        } catch (e: TimeoutCancellationException) {
            e.printStackTrace()
            Result.failure(Exception("SMB operation timed out"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            closeConnection()
        }
    }

    private suspend fun streamFilesInCurrentDirectory(
        remoteApi: Smb,
        filter: ((NetworkFile) -> Boolean)?,
        batchSize: Int,
        onBatch: suspend (List<NetworkFile>) -> Unit
    ): Long {
        val buffer = ArrayList<NetworkFile>(batchSize)
        var emitted = 0L

        listFilesAndDirs(remoteApi)
            .asSequence()
            .filter { !it.isDirectory }
            .forEach { file ->
                if (filter?.invoke(file) == false) {
                    return@forEach
                }
                buffer.add(file)
                if (buffer.size >= batchSize) {
                    onBatch(buffer.toList())
                    emitted += buffer.size
                    buffer.clear()
                }
            }

        if (buffer.isNotEmpty()) {
            onBatch(buffer.toList())
            emitted += buffer.size
        }

        return emitted
    }

    private suspend fun initConnection(remoteApi: Smb, timeout: Long = 15000) {
        withContext(Dispatchers.IO) {
            withTimeout(timeout) {
                smbClient = SMBClient()
                connection = smbClient!!.connect(remoteApi.host, remoteApi.port)
                val authContext = AuthenticationContext(
                    remoteApi.user,
                    RConfig.decrypt(remoteApi.passwd).toCharArray(),
                    null
                )

                session = connection!!.authenticate(authContext)
                val shareName = extractShareNameFromPath(remoteApi.path)
                if (shareName.isNotBlank()) {
                    share = session!!.connectShare(shareName) as DiskShare
                }
            }
        }
    }

    private fun extractShareNameFromPath(path: String): String {
        val cleanPath = path.removePrefix("/").removeSuffix("/")
        return if (cleanPath.contains("/")) {
            cleanPath.substringBefore("/")
        } else {
            cleanPath.ifEmpty { "" }
        }
    }

    private fun getPathWithoutShare(path: String): String {
        val cleanPath = path.removePrefix("/")
        return if (cleanPath.contains("/")) {
            cleanPath.substringAfter("/")
        } else {
            ""
        }
    }

    private suspend fun listFilesAndDirs(remoteApi: Smb): List<NetworkFile> {
        val shareName = extractShareNameFromPath(remoteApi.path)
        val directoryPath = getPathWithoutShare(remoteApi.path)

        return try {
            withContext(Dispatchers.IO) {
                if (shareName.isEmpty()) {
                    val transport: RPCTransport? = SMBTransportFactories.SRVSVC.getTransport(session)
                    val serverService = ServerService(transport)

                    val sharePaths = serverService.shares1.mapNotNull {
                        if (!(it.type.hasBits(ShareTypes.STYPE_PRINTQ.value)
                                    || it.type.hasBits(ShareTypes.STYPE_DEVICE.value)
                                    || it.type.hasBits(ShareTypes.STYPE_IPC.value))
                        ) {
                            it.netName
                        } else {
                            null
                        }
                    }
                    sharePaths.map {
                        NetworkFile(
                            remoteApi,
                            it,
                            it,
                            true,
                            0,
                            "",
                            "",
                            true
                        )
                    }
                } else {
                    val fileInfos = share!!.list(directoryPath)
                    val files = mutableListOf<NetworkFile>()

                    for (fileInfo in fileInfos) {
                        if (fileInfo.fileName == "." || fileInfo.fileName == "..") continue

                        val isDirectory = fileInfo.fileAttributes.toInt() and 0x10 != 0
                        val fullPath = if (directoryPath.isEmpty()) {
                            fileInfo.fileName
                        } else {
                            "$directoryPath/${fileInfo.fileName}"
                        }

                        val networkFile = NetworkFile(
                            remoteApi,
                            if (isDirectory) "$shareName/$fullPath" else "smb://${remoteApi.host}:${remoteApi.port}/$shareName/$fullPath",
                            fileInfo.fileName,
                            isDirectory,
                            fileInfo.endOfFile,
                            getMimeType(fileInfo.fileName),
                            fileInfo.lastWriteTime.toEpochMillis().toString()
                        )

                        files.add(networkFile)
                    }
                    files
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private suspend fun streamFilesRecursively(
        remoteApi: Smb,
        filter: ((NetworkFile) -> Boolean)?,
        batchSize: Int,
        onBatch: suspend (List<NetworkFile>) -> Unit
    ): Long {
        val shareName = extractShareNameFromPath(remoteApi.path)
        val directoryPath = getPathWithoutShare(remoteApi.path)
        if (shareName.isBlank()) {
            return 0L
        }

        val buffer = ArrayList<NetworkFile>(batchSize)
        val pendingDirs = ArrayDeque<String>()
        pendingDirs.add(directoryPath)
        var emitted = 0L

        while (pendingDirs.isNotEmpty()) {
            val currentDirectory = pendingDirs.removeLast()
            val fileInfos = share!!.list(currentDirectory)
            for (fileInfo in fileInfos) {
                if (fileInfo.fileName == "." || fileInfo.fileName == "..") continue

                val isDirectory = fileInfo.fileAttributes.toInt() and 0x10 != 0
                val fullPath = if (currentDirectory.isEmpty()) {
                    fileInfo.fileName
                } else {
                    "$currentDirectory/${fileInfo.fileName}"
                }

                if (isDirectory) {
                    pendingDirs.add(fullPath)
                    continue
                }

                val networkFile = NetworkFile(
                    remoteApi,
                    "smb://${remoteApi.host}:${remoteApi.port}/$shareName/$fullPath",
                    fileInfo.fileName,
                    false,
                    fileInfo.endOfFile,
                    getMimeType(fileInfo.fileName),
                    fileInfo.lastWriteTime.toEpochMillis().toString()
                )

                if (filter?.invoke(networkFile) == false) {
                    continue
                }

                buffer.add(networkFile)
                if (buffer.size >= batchSize) {
                    onBatch(buffer.toList())
                    emitted += buffer.size
                    buffer.clear()
                }
            }
        }

        if (buffer.isNotEmpty()) {
            onBatch(buffer.toList())
            emitted += buffer.size
        }

        return emitted
    }

    private suspend fun closeConnection() {

        try {
            withContext(Dispatchers.IO) {
                share?.close()
                session?.close()
                connection?.close()
                smbClient?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            share = null
            session = null
            connection = null
            smbClient = null
        }
    }


    override suspend fun getFileDirItems(remoteApi: Smb): Result<List<NetworkFile>> {
        return try {
            initConnection(remoteApi, 15000)
            val files = withContext(Dispatchers.IO) {
                withTimeout(15000) {
                    listFilesAndDirs(remoteApi)
                }
            }
            Result.success(files)
        } catch (e: TimeoutCancellationException) {
            e.printStackTrace()
            Result.failure(Exception("SMB directory listing timed out"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            closeConnection()
        }
    }
}

fun Int.hasBits(bits: Int): Boolean = this and bits == bits

infix fun Int.andInv(other: Int): Int = this and other.inv()