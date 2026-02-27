package com.alpha.showcase.common

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Ftp
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.repo.FtpSourceRepo
import com.alpha.showcase.common.utils.getExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.commons.net.ftp.FTPClient
import java.nio.charset.StandardCharsets

class NativeFtpSourceRepo : FtpSourceRepo {

    private var ftpClient: FTPClient? = null

    override suspend fun getItem(remoteApi: Ftp): Result<NetworkFile> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: Ftp,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?
    ): Result<List<NetworkFile>> {
        return try {
            initConnection(remoteApi, 15000)
            
            val path = if (remoteApi.path.startsWith("/")) remoteApi.path else "/${remoteApi.path}"
            val files = withContext(Dispatchers.IO){
                withTimeout(30000) { // 30 second timeout for FTP operations
                    if (recursive) {
                        listFilesRecursively(remoteApi, path)
                    } else {
                        listFiles(remoteApi, path)
                    }
                }
            }
            
            val filteredFiles = files.filter { filter?.invoke(it) ?: true }
            Result.success(filteredFiles)
        } catch (e: TimeoutCancellationException) {
            e.printStackTrace()
            Result.failure(Exception("FTP operation timed out"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            closeConnection()
        }
    }

    private suspend fun initConnection(remoteApi: Ftp, timeout: Long = 15000) {
        withTimeout(timeout) {
            withContext(Dispatchers.IO) {
                ftpClient = FTPClient()
                // Configure FTP client timeouts
                ftpClient!!.connectTimeout = timeout.toInt()
                ftpClient!!.defaultTimeout = timeout.toInt()
                
                // 设置控制连接编码为UTF-8以支持中文
                ftpClient!!.controlEncoding = StandardCharsets.UTF_8.name()
                
                ftpClient!!.connect(remoteApi.host, remoteApi.port)
                ftpClient!!.login(remoteApi.user, RConfig.decrypt(remoteApi.passwd))
                ftpClient!!.enterLocalPassiveMode()
                ftpClient!!.setFileType(FTPClient.BINARY_FILE_TYPE)
                
                // 尝试发送OPTS UTF8 ON命令来启用UTF-8支持
                try {
                    ftpClient!!.sendCommand("OPTS UTF8 ON")
                } catch (e: Exception) {
                    // 如果服务器不支持UTF8命令，忽略错误
                }
            }
        }
    }

    private fun listFiles(remoteApi: Ftp, path: String): List<NetworkFile> {
        val files = mutableListOf<NetworkFile>()
        
        try {
            val ftpFiles = ftpClient!!.listFiles(path)
            
            for (ftpFile in ftpFiles) {
                if (ftpFile.name == "." || ftpFile.name == "..") continue

                if (ftpFile.isFile) {
                    val fullPath = if (path.endsWith("/")) {
                        "$path${ftpFile.name}"
                    } else {
                        "$path/${ftpFile.name}"
                    }

                    val networkFile = NetworkFile(
                        remoteApi,
                        "ftp://${remoteApi.host}:${remoteApi.port}$fullPath",
                        ftpFile.name,
                        false,
                        ftpFile.size,
                        ftpFile.name.getExtension(),
                        ftpFile.timestamp?.timeInMillis?.toString() ?: System.currentTimeMillis().toString()
                    )
                    files.add(networkFile)
                }
            }
        } catch (e: Exception) {
            throw e
        }
        
        return files
    }

    private fun listFilesRecursively(remoteApi: Ftp, path: String): List<NetworkFile> {
        val allFiles = mutableListOf<NetworkFile>()

        try {
            val ftpFiles = ftpClient!!.listFiles(path)

            for (ftpFile in ftpFiles) {
                if (ftpFile.name == "." || ftpFile.name == "..") continue

                val fullPath = if (path.endsWith("/")) {
                    "$path${ftpFile.name}"
                } else {
                    "$path/${ftpFile.name}"
                }

                if (ftpFile.isDirectory) {
                    allFiles.addAll(listFilesRecursively(remoteApi, fullPath))
                } else if (ftpFile.isFile) {
                    val networkFile = NetworkFile(
                        remoteApi,
                        "ftp://${remoteApi.host}:${remoteApi.port}$fullPath",
                        ftpFile.name,
                        false,
                        ftpFile.size,
                        ftpFile.name.getExtension(),
                        ftpFile.timestamp?.timeInMillis?.toString() ?: System.currentTimeMillis().toString()
                    )
                    allFiles.add(networkFile)
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
                ftpClient?.logout()
                ftpClient?.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            ftpClient = null
        }
    }

    override suspend fun getFileDirItems(remoteApi: Ftp): Result<List<NetworkFile>> {
        return try {
            initConnection(remoteApi, 15000)
            val path = if (remoteApi.path.startsWith("/")) remoteApi.path else "/${remoteApi.path}"
            val files = withTimeout(15000) {
                withContext(Dispatchers.IO){
                 // 15 second timeout for directory listing
                    listFilesAndDirs(remoteApi, path)
                }
            }
            
            Result.success(files)
        } catch (e: TimeoutCancellationException) {
            e.printStackTrace()
            Result.failure(Exception("FTP directory listing timed out"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            closeConnection()
        }
    }
    
    private fun listFilesAndDirs(remoteApi: Ftp, path: String): List<NetworkFile> {
        val files = mutableListOf<NetworkFile>()
        
        try {
            val ftpFiles = ftpClient!!.listFiles(path)
            
            for (ftpFile in ftpFiles) {
                if (ftpFile.name == "." || ftpFile.name == "..") continue
                
                val fullPath = if (path.endsWith("/")) {
                    "$path${ftpFile.name}"
                } else {
                    "$path/${ftpFile.name}"
                }
                
                val networkFile = NetworkFile(
                    remoteApi,
                    if (ftpFile.isDirectory) fullPath else "ftp://${remoteApi.host}:${remoteApi.port}$fullPath",
                    ftpFile.name,
                    ftpFile.isDirectory,
                    ftpFile.size,
                    if (ftpFile.isDirectory) "" else ftpFile.name.getExtension(),
                    ftpFile.timestamp?.timeInMillis?.toString() ?: System.currentTimeMillis().toString()
                )
                files.add(networkFile)
            }
        } catch (e: Exception) {
            throw e
        }
        
        return files
    }
}
