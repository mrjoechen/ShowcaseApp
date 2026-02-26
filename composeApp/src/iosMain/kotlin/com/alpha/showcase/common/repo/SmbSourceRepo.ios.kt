@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.utils.getMimeType
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.posix.dlsym
import platform.posix.free

private const val SMB_BRIDGE_SYMBOL = "ShowcaseSmbInvoke"

actual fun createSmbSourceRepo(): SmbSourceRepo? = IosSmbSourceRepo()

private class IosSmbSourceRepo : SmbSourceRepo {

    private val bridgeJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val bridgeFn: CPointer<CFunction<(CPointer<ByteVar>?) -> CPointer<ByteVar>?>> by lazy {
        val symbol = dlsym(null, SMB_BRIDGE_SYMBOL)
            ?: throw IllegalStateException(
                "SMB bridge symbol '$SMB_BRIDGE_SYMBOL' was not found. " +
                    "Ensure iosApp links SMB bridge implementation."
            )
        symbol.reinterpret<CFunction<(CPointer<ByteVar>?) -> CPointer<ByteVar>?>>()
    }

    override suspend fun getItem(remoteApi: Smb): Result<NetworkFile> {
        return Result.failure(UnsupportedOperationException("Not implemented"))
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
    ): Result<Long> = withContext(Dispatchers.Default) {
        val safeBatchSize = batchSize.coerceAtLeast(1)
        val (shareName, directoryPath) = extractShareAndDirectory(remoteApi.path)

        if (shareName.isBlank()) {
            return@withContext Result.success(0L)
        }

        val buffer = ArrayList<NetworkFile>(safeBatchSize)
        var emitted = 0L

        fun buildNetworkFile(relativePath: String, entry: BridgeEntry): NetworkFile {
            val smbPath = "smb://${remoteApi.host}:${remoteApi.port}/$shareName/$relativePath"
            return NetworkFile(
                remote = remoteApi,
                path = smbPath,
                fileName = entry.name,
                isDirectory = false,
                size = entry.size,
                mimeType = getMimeType(entry.name),
                modTime = entry.lastWriteTimeMillis.toString(),
            )
        }

        suspend fun flushBuffer() {
            if (buffer.isEmpty()) return
            onBatch(buffer.toList())
            emitted += buffer.size
            buffer.clear()
        }

        fun addFileIfMatched(relativePath: String, entry: BridgeEntry) {
            if (entry.isDirectory) return
            val networkFile = buildNetworkFile(relativePath, entry)
            if (filter?.invoke(networkFile) == false) return
            buffer.add(networkFile)
        }

        return@withContext try {
            withSession(remoteApi) { sessionId ->
                if (!recursive) {
                    listDirectory(sessionId, shareName, directoryPath).forEach { entry ->
                        if (entry.name == "." || entry.name == "..") return@forEach
                        val relativePath = joinPath(directoryPath, entry.name)
                        addFileIfMatched(relativePath, entry)
                        if (buffer.size >= safeBatchSize) {
                            flushBuffer()
                        }
                    }
                } else {
                    val pending = ArrayDeque<String>()
                    pending.add(directoryPath)

                    while (pending.isNotEmpty()) {
                        val currentDir = pending.removeLast()
                        listDirectory(sessionId, shareName, currentDir).forEach { entry ->
                            if (entry.name == "." || entry.name == "..") return@forEach

                            val relativePath = joinPath(currentDir, entry.name)
                            if (entry.isDirectory) {
                                pending.add(relativePath)
                                return@forEach
                            }

                            addFileIfMatched(relativePath, entry)
                            if (buffer.size >= safeBatchSize) {
                                flushBuffer()
                            }
                        }
                    }
                }
            }

            flushBuffer()
            Result.success(emitted)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun getFileDirItems(remoteApi: Smb): Result<List<NetworkFile>> = withContext(Dispatchers.Default) {
        try {
            val (shareName, directoryPath) = extractShareAndDirectory(remoteApi.path)
            val files = withSession(remoteApi) { sessionId ->
                if (shareName.isBlank()) {
                    listShares(sessionId).map { share ->
                        NetworkFile(
                            remote = remoteApi,
                            path = share.name,
                            fileName = share.name,
                            isDirectory = true,
                            size = 0,
                            mimeType = "",
                            modTime = "",
                            isBucket = true,
                        )
                    }
                } else {
                    listDirectory(sessionId, shareName, directoryPath)
                        .filterNot { it.name == "." || it.name == ".." }
                        .map { entry ->
                            val relativePath = joinPath(directoryPath, entry.name)
                            val fullPath = "$shareName/$relativePath"
                            val path = if (entry.isDirectory) {
                                fullPath
                            } else {
                                "smb://${remoteApi.host}:${remoteApi.port}/$fullPath"
                            }

                            NetworkFile(
                                remote = remoteApi,
                                path = path,
                                fileName = entry.name,
                                isDirectory = entry.isDirectory,
                                size = entry.size,
                                mimeType = getMimeType(entry.name),
                                modTime = entry.lastWriteTimeMillis.toString(),
                            )
                        }
                }
            }

            Result.success(files)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun <T> withSession(remoteApi: Smb, block: suspend (sessionId: String) -> T): T {
        val sessionId = openSession(remoteApi)
        return try {
            block(sessionId)
        } finally {
            closeSession(sessionId)
        }
    }

    private fun openSession(remoteApi: Smb): String {
        val response = invokeBridge(
            BridgeRequest(
                action = "open",
                host = remoteApi.host,
                port = remoteApi.port,
                user = remoteApi.user,
                password = RConfig.decrypt(remoteApi.passwd),
            )
        )

        val sessionId = response.sessionId
        if (sessionId.isNullOrBlank()) {
            throw IllegalStateException("SMB bridge open response missing sessionId")
        }
        return sessionId
    }

    private fun closeSession(sessionId: String) {
        runCatching {
            invokeBridge(
                BridgeRequest(
                    action = "close",
                    sessionId = sessionId,
                )
            )
        }
    }

    private fun listShares(sessionId: String): List<BridgeShare> {
        val response = invokeBridge(
            BridgeRequest(
                action = "listShares",
                sessionId = sessionId,
            )
        )
        return response.shares
    }

    private fun listDirectory(sessionId: String, share: String, path: String): List<BridgeEntry> {
        val response = invokeBridge(
            BridgeRequest(
                action = "listDirectory",
                sessionId = sessionId,
                share = share,
                path = path,
            )
        )
        return response.entries
    }

    private fun invokeBridge(request: BridgeRequest): BridgeResponse {
        val requestJson = bridgeJson.encodeToString(request)

        return memScoped {
            val requestPtr = requestJson.cstr.ptr
            val responsePtr = bridgeFn.invoke(requestPtr)
                ?: throw IllegalStateException("SMB bridge returned null response")
            try {
                val responseJson = responsePtr.toKString()
                val response = bridgeJson.decodeFromString<BridgeResponse>(responseJson)
                if (!response.ok) {
                    throw IllegalStateException(response.error ?: "SMB bridge request failed")
                }
                response
            } finally {
                free(responsePtr)
            }
        }
    }

    private fun extractShareAndDirectory(rawPath: String): Pair<String, String> {
        val pathWithoutScheme = if (rawPath.startsWith("smb://", ignoreCase = true)) {
            rawPath.removePrefix("smb://").substringAfter('/', "")
        } else {
            rawPath
        }

        val normalized = pathWithoutScheme
            .trim()
            .trim('/')

        if (normalized.isBlank()) {
            return "" to ""
        }

        val share = normalized.substringBefore('/')
        val directory = normalized.substringAfter('/', "")
        return share to directory
    }

    private fun joinPath(base: String, name: String): String {
        if (base.isBlank()) return name
        val normalizedBase = base.trim('/').ifBlank { return name }
        return "$normalizedBase/$name"
    }
}

@Serializable
private data class BridgeRequest(
    val action: String,
    val host: String? = null,
    val port: Int? = null,
    val user: String? = null,
    val password: String? = null,
    val sessionId: String? = null,
    val share: String? = null,
    val path: String? = null,
)

@Serializable
private data class BridgeResponse(
    val ok: Boolean,
    val error: String? = null,
    val sessionId: String? = null,
    val shares: List<BridgeShare> = emptyList(),
    val entries: List<BridgeEntry> = emptyList(),
)

@Serializable
private data class BridgeShare(
    val name: String,
)

@Serializable
private data class BridgeEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastWriteTimeMillis: Long,
)
