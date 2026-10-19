package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.WebDavClient
import com.alpha.showcase.common.networkfile.WebDavFile
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.utils.decodeUrlPath
import com.alpha.showcase.common.utils.getExtension

class NativeWebdavSourceRepo : SourceRepository<WebDav, NetworkFile>,
    FileDirSource<WebDav, NetworkFile>,
    BatchSourceRepository<WebDav, NetworkFile> {

    override suspend fun getItem(remoteApi: WebDav): Result<NetworkFile> {
        TODO("Not yet implemented")
    }

    // The folder picker navigates with these paths and saves the chosen one as WebDav.path,
    // so they stay relative to the configured URL rather than being raw server hrefs.
    override suspend fun getFileDirItems(remoteApi: WebDav): Result<List<NetworkFile>> {
        return try {
            val contents = createClient(remoteApi).listFiles(webDavServerPath(remoteApi.url, remoteApi.path))
            val resultList = contents.map { file ->
                NetworkFile(
                    remoteApi,
                    webDavRelativePath(remoteApi.url, file.path),
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
            Result.failure(Exception(e.message, e))
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
        val rootPath = normalizeDirectoryPath(webDavServerPath(remoteApi.url, remoteApi.path))
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
            val client = createClient(remoteApi)
            if (!recursive) {
                val contents = client.listFiles(webDavServerPath(remoteApi.url, remoteApi.path))
                contents.forEach { content ->
                    val normalized = normalizePath(content.path)
                    if (isSamePath(normalized, rootPath)) {
                        return@forEach
                    }
                    pushIfMatched(mapToNetworkFile(content))
                }
            } else {
                val pendingDirs = ArrayDeque<String>()
                val visited = mutableSetOf<String>()
                pendingDirs.add(rootPath)

                while (pendingDirs.isNotEmpty()) {
                    val currentPath = normalizeDirectoryPath(pendingDirs.removeLast())
                    if (!visited.add(currentPath)) {
                        continue
                    }

                    val resources = client.listFiles(currentPath)
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
            Result.failure(Exception(e.message ?: "WebDAV stream failed", e))
        }
    }

    private suspend fun createClient(remoteApi: WebDav) =
        WebDavClient(webDavOrigin(remoteApi.url), remoteApi.user, RConfig.decryptAsync(remoteApi.passwd))

    private fun isSamePath(pathA: String, pathB: String): Boolean {
        return normalizeDirectoryPath(pathA) == normalizeDirectoryPath(pathB)
    }
}

// WebDav.path is relative to the configured URL (url ".../dav", path "/photos"), while
// PROPFIND hrefs are server-absolute ("/dav/photos/"). Requests therefore go to the URL's
// origin with its own path prepended, so both forms resolve against the same client.
internal fun webDavOrigin(url: String): String = url.substring(0, webDavAuthorityEnd(url))

internal fun webDavServerPath(url: String, path: String): String =
    webDavBasePath(url) + normalizeDirectoryPath(path)

internal fun webDavRelativePath(url: String, href: String): String {
    val baseSegments = webDavBasePath(url).pathSegments()
    val segments = href.pathSegments()
    val underBase = segments.size >= baseSegments.size &&
        baseSegments.indices.all { decodeSegment(segments[it]) == decodeSegment(baseSegments[it]) }
    return "/" + (if (underBase) segments.drop(baseSegments.size) else segments).joinToString("/")
}

private fun webDavAuthorityEnd(url: String): Int {
    val authorityStart = url.indexOf("://").let { if (it < 0) 0 else it + 3 }
    return url.indexOfAny(charArrayOf('/', '?', '#'), authorityStart).let { if (it < 0) url.length else it }
}

private fun webDavBasePath(url: String): String =
    url.substring(webDavAuthorityEnd(url)).substringBefore('?').substringBefore('#').trimEnd('/')

private fun String.pathSegments(): List<String> = split('/').filter { it.isNotEmpty() }

// Hrefs are percent-encoded while a typed URL may not be, so compare base segments decoded.
private fun decodeSegment(segment: String): String =
    try { decodeUrlPath(segment) } catch (_: Exception) { segment }

private fun normalizePath(path: String): String {
    if (path.isBlank()) return "/"
    return if (path.startsWith("/")) path else "/$path"
}

private fun normalizeDirectoryPath(path: String): String {
    val normalized = normalizePath(path)
    return if (normalized.length > 1) normalized.trimEnd('/') else normalized
}
