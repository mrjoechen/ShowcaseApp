package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.WebDavClient
import com.alpha.showcase.common.networkfile.WebDavFile
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.utils.getExtension
import io.ktor.http.Url
import io.ktor.http.fullPath

class NativeWebdavSourceRepo : SourceRepository<WebDav, NetworkFile>,
    FileDirSource<WebDav, NetworkFile>,
    BatchSourceRepository<WebDav, NetworkFile> {

    private lateinit var webDavClient: WebDavClient

    override suspend fun getItem(remoteApi: WebDav): Result<NetworkFile> {
        TODO("Not yet implemented")
    }

    override suspend fun getFileDirItems(remoteApi: WebDav): Result<List<NetworkFile>> {
        return try {
            val urlWithoutPath = remoteApi.url.replace(Url(remoteApi.url).fullPath, "")
            val baseUrl = urlWithoutPath.ifBlank { remoteApi.url }
            webDavClient = WebDavClient(baseUrl, remoteApi.user, remoteApi.passwd)
            val path = remoteApi.path.ifBlank { "/" }
            val contents = webDavClient.listFiles(path)
            val resultList = contents.map { file ->
                NetworkFile(
                    remoteApi,
                    normalizePath(file.path),
                    file.name,
                    file.isDirectory,
                    file.contentLength,
                    file.name.getExtension(),
                    file.lastModified.ifBlank { file.creationDate }
                )
            }
            Result.success(resultList)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception(e.message))
        }
    }

    override suspend fun getItems(
        remoteApi: WebDav,
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
            Result.failure(streamResult.exceptionOrNull() ?: Exception("WebDAV operation failed"))
        }
    }

    override suspend fun streamItems(
        remoteApi: WebDav,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?,
        batchSize: Int,
        onBatch: suspend (List<NetworkFile>) -> Unit
    ): Result<Long> {
        val safeBatchSize = batchSize.coerceAtLeast(1)
        val rootPath = normalizeDirectoryPath(remoteApi.path.ifBlank { "/" })
        val buffer = ArrayList<NetworkFile>(safeBatchSize)
        var emitted = 0L

        suspend fun flushBuffer() {
            if (buffer.isEmpty()) return
            onBatch(buffer.toList())
            emitted += buffer.size
            buffer.clear()
        }

        fun mapToNetworkFile(file: WebDavFile): NetworkFile {
            return NetworkFile(
                remoteApi,
                normalizePath(file.path),
                file.name,
                file.isDirectory,
                file.contentLength,
                file.contentType.ifBlank { file.name.getExtension() },
                file.lastModified.ifBlank { file.creationDate }
            )
        }

        suspend fun pushIfMatched(networkFile: NetworkFile) {
            if (filter?.invoke(networkFile) == false) {
                return
            }
            buffer.add(networkFile)
            if (buffer.size >= safeBatchSize) {
                flushBuffer()
            }
        }

        return try {
            if (!recursive) {
                webDavClient = WebDavClient(remoteApi.url, remoteApi.user, remoteApi.passwd)
                val contents = webDavClient.listFiles(rootPath)
                contents.forEach { content ->
                    val normalized = normalizePath(content.path)
                    if (isSamePath(normalized, rootPath)) {
                        return@forEach
                    }
                    pushIfMatched(mapToNetworkFile(content))
                }
            } else {
                val urlWithoutPath = remoteApi.url.replace(Url(remoteApi.url).fullPath, "")
                val baseUrl = urlWithoutPath.ifBlank { remoteApi.url }
                val recursiveClient = WebDavClient(baseUrl, remoteApi.user, remoteApi.passwd)
                val pendingDirs = ArrayDeque<String>()
                val visited = mutableSetOf<String>()
                pendingDirs.add(rootPath)

                while (pendingDirs.isNotEmpty()) {
                    val currentPath = normalizeDirectoryPath(pendingDirs.removeLast())
                    if (!visited.add(currentPath)) {
                        continue
                    }

                    val resources = recursiveClient.listFiles(currentPath)
                    resources.forEach { resource ->
                        val normalized = normalizePath(resource.path)
                        if (isSamePath(normalized, currentPath)) {
                            return@forEach
                        }

                        if (resource.isDirectory) {
                            pendingDirs.add(normalizeDirectoryPath(normalized))
                            return@forEach
                        }

                        pushIfMatched(mapToNetworkFile(resource))
                    }
                }
            }

            flushBuffer()
            Result.success(emitted)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception(e.message ?: "WebDAV stream failed"))
        }
    }

    private fun normalizePath(path: String): String {
        if (path.isBlank()) return "/"
        return if (path.startsWith("/")) path else "/$path"
    }

    private fun normalizeDirectoryPath(path: String): String {
        val normalized = normalizePath(path)
        return if (normalized.length > 1) normalized.trimEnd('/') else normalized
    }

    private fun isSamePath(pathA: String, pathB: String): Boolean {
        return normalizeDirectoryPath(pathA) == normalizeDirectoryPath(pathB)
    }
}
