package com.alpha.showcase.common
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.repo.FileDirSource
import com.alpha.showcase.common.repo.SourceRepository
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
class NativeSmbSourceRepo : SourceRepository<Smb, NetworkFile>, FileDirSource<Smb, NetworkFile> {

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
        return try {
            initConnection(remoteApi, 15000)
            val files = withContext(Dispatchers.IO) {
                withTimeout(30000) { // 30 second timeout for SMB operations
                    if (recursive) {
                        listFilesRecursively(remoteApi) // Max depth 5
                    } else {
                        listFilesAndDirs(remoteApi).filter { !it.isDirectory }
                    }
                }
            }

            val filteredFiles = files.filter { filter?.invoke(it) ?: true }
            Result.success(filteredFiles)
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
        // Extract share name from path like "/Documents/folder" -> "Documents"
        val cleanPath = path.removePrefix("/").removeSuffix("/")
        return if (cleanPath.contains("/")) {
            cleanPath.substringBefore("/")
        } else {
            cleanPath.ifEmpty { "" } // default share name if path is empty
        }
    }

    private fun getPathWithoutShare(path: String): String {
        // Extract path after share name like "/Documents/folder/file" -> "folder/file"
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
                    val transport: RPCTransport? =
                        SMBTransportFactories.SRVSVC.getTransport(session)
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
                            if(isDirectory) "$shareName/$fullPath" else "smb://${remoteApi.host}:${remoteApi.port}/$shareName/$fullPath",
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

    private suspend fun listFilesRecursively(remoteApi: Smb): List<NetworkFile> {
        val allFiles = mutableListOf<NetworkFile>()
        val shareName = extractShareNameFromPath(remoteApi.path)
        val directoryPath = getPathWithoutShare(remoteApi.path)

        try {
            withContext(Dispatchers.IO) {
                val fileInfos = share!!.list(directoryPath)

                for (fileInfo in fileInfos) {
                    if (fileInfo.fileName == "." || fileInfo.fileName == "..") continue

                    val isDirectory = fileInfo.fileAttributes.toInt() and 0x10 != 0
                    val fullPath = if (directoryPath.isEmpty()) {
                        fileInfo.fileName
                    } else {
                        "$directoryPath/${fileInfo.fileName}"
                    }

                    if (isDirectory) {
                        // Create new remoteApi with updated path for recursive call
                        val newPath = "/$shareName/$fullPath"
                        val newRemoteApi = remoteApi.copy(path = newPath)
                        allFiles.addAll(listFilesRecursively(newRemoteApi))
                    } else {
                        val networkFile = NetworkFile(
                            remoteApi,
                            "smb://${remoteApi.host}:${remoteApi.port}/$shareName/$fullPath",
                            fileInfo.fileName,
                            false,
                            fileInfo.endOfFile,
                            getMimeType(fileInfo.fileName),
                            fileInfo.lastWriteTime.toEpochMillis().toString()
                        )
                        allFiles.add(networkFile)
                    }
                }
            }
        } catch (e: Exception) {
            throw e
        }

        return allFiles
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
                withTimeout(15000) { // 15 second timeout for directory listing
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