package com.alpha.showcase.common

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Sftp
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.repo.FileDirSource
import com.alpha.showcase.common.repo.SourceRepository
import com.alpha.showcase.common.utils.getExtension
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Vector

class NativeSftpSourceRepo : SourceRepository<Sftp, NetworkFile>, FileDirSource<Sftp, NetworkFile> {

    private var jsch: JSch? = null
    private var session: Session? = null
    private var sftpChannel: ChannelSftp? = null

    override suspend fun getItem(remoteApi: Sftp): Result<NetworkFile> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: Sftp,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?
    ): Result<List<NetworkFile>> {
        return try {
            initConnection(remoteApi, 15000)
            val path = if (remoteApi.path.startsWith("/")) remoteApi.path else "/${remoteApi.path}"
            val files = withContext(Dispatchers.IO){
                withTimeout(30000) { // 30 second timeout for file operations
                    if (recursive) {
                        listFilesRecursively(remoteApi, path) // Max depth 5
                    } else {
                        listFiles(remoteApi, path)
                    }
                }
            }
            
            val filteredFiles = files.filter { filter?.invoke(it) ?: true }
            Result.success(filteredFiles)
        } catch (e: TimeoutCancellationException) {
            e.printStackTrace()
            Result.failure(Exception("SFTP operation timed out"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            closeConnection()
        }
    }

    private suspend fun initConnection(remoteApi: Sftp, timeout: Long = 15000) {
        withContext(Dispatchers.IO){
            withTimeout(timeout){
                jsch = JSch()
                session = jsch!!.getSession(remoteApi.user, remoteApi.host, remoteApi.port)
                session!!.setPassword(RConfig.decrypt(remoteApi.passwd))
                session!!.setConfig("StrictHostKeyChecking", "no")
                // 设置编码为 UTF-8 以支持中文文件名
                session!!.setConfig("file.encoding", "UTF-8")
                // Configure session timeouts
                session!!.timeout = timeout.toInt()
                session!!.connect(timeout.toInt())
                val channel = session!!.openChannel("sftp")
                channel.connect(timeout.toInt())
                sftpChannel = channel as ChannelSftp
                // 设置 SFTP 通道编码
                sftpChannel!!.setFilenameEncoding("UTF-8")
            }
        }
    }

    private fun listFiles(remoteApi: Sftp, path: String): List<NetworkFile> {
        val files = mutableListOf<NetworkFile>()
        
        try {
            @Suppress("UNCHECKED_CAST")
            val entries = sftpChannel!!.ls(path) as Vector<ChannelSftp.LsEntry>
            
            for (entry in entries) {
                if (entry.filename == "." || entry.filename == "..") continue
                
                if (!entry.attrs.isDir) {
                    val fullPath = if (path.endsWith("/")) {
                        "$path${entry.filename}"
                    } else {
                        "$path/${entry.filename}"
                    }
                    
                    val networkFile = NetworkFile(
                        remoteApi,
                        "sftp://${remoteApi.host}:${remoteApi.port}$fullPath",
                        entry.filename,
                        false,
                        entry.attrs.size,
                        entry.filename.getExtension(),
                        (entry.attrs.mTime * 1000L).toString()
                    )
                    files.add(networkFile)
                }
            }
        } catch (e: Exception) {
            throw e
        }
        
        return files
    }

    private fun listFilesRecursively(remoteApi: Sftp, path: String): List<NetworkFile> {
        val allFiles = mutableListOf<NetworkFile>()
        
        try {
            @Suppress("UNCHECKED_CAST")
            val entries = sftpChannel!!.ls(path) as Vector<ChannelSftp.LsEntry>
            
            for (entry in entries) {
                if (entry.filename == "." || entry.filename == "..") continue
                
                val fullPath = if (path.endsWith("/")) {
                    "$path${entry.filename}"
                } else {
                    "$path/${entry.filename}"
                }
                
                if (entry.attrs.isDir) {
                    allFiles.addAll(listFilesRecursively(remoteApi, fullPath))
                } else {
                    val networkFile = NetworkFile(
                        remoteApi,
                        "sftp://${remoteApi.host}:${remoteApi.port}$fullPath",
                        entry.filename,
                        false,
                        entry.attrs.size,
                        entry.filename.getExtension(),
                        (entry.attrs.mTime * 1000L).toString()
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
            withContext(Dispatchers.IO){
                sftpChannel?.exit()
                session?.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            sftpChannel = null
            session = null
            jsch = null
        }
    }

    override suspend fun getFileDirItems(remoteApi: Sftp): Result<List<NetworkFile>> {
        return try {
            initConnection(remoteApi, 15000)
            val path = if (remoteApi.path.startsWith("/")) remoteApi.path else "/${remoteApi.path}"
            val files = withContext(Dispatchers.IO){
                withTimeout(15000) { // 15 second timeout for directory listing
                    listFilesAndDirs(remoteApi, path)
                }
            }
            
            Result.success(files)
        } catch (e: TimeoutCancellationException) {
            e.printStackTrace()
            Result.failure(Exception("SFTP directory listing timed out"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            closeConnection()
        }
    }
    
    private fun listFilesAndDirs(remoteApi: Sftp, path: String): List<NetworkFile> {
        val files = mutableListOf<NetworkFile>()
        
        try {
            @Suppress("UNCHECKED_CAST")
            val entries = sftpChannel!!.ls(path) as Vector<ChannelSftp.LsEntry>
            
            for (entry in entries) {
                if (entry.filename == "." || entry.filename == "..") continue
                
                val fullPath = if (path.endsWith("/")) {
                    "$path${entry.filename}"
                } else {
                    "$path/${entry.filename}"
                }
                
                val networkFile = NetworkFile(
                    remoteApi,
                    if (entry.attrs.isDir) fullPath else "sftp://${remoteApi.host}:${remoteApi.port}$fullPath",
                    entry.filename,
                    entry.attrs.isDir,
                    entry.attrs.size,
                    if (entry.attrs.isDir) "" else entry.filename.getExtension(),
                    (entry.attrs.mTime * 1000L).toString()
                )
                files.add(networkFile)
            }
        } catch (e: Exception) {
            throw e
        }
        
        return files
    }
}