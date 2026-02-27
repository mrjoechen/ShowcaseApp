@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.io.encoding.ExperimentalEncodingApi::class
)

package com.alpha.showcase.common

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.utils.decodeUrlPath
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import okio.Buffer
import platform.posix.dlsym
import platform.posix.free

private const val SMB_BRIDGE_SYMBOL = "ShowcaseSmbInvoke"

internal class NetworkFileFetcher(
    private val networkFile: NetworkFile,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        return networkFetchSemaphore.withPermit {
            val bytes = SmbBridgeFileReader.readBytes(networkFile)
            SourceFetchResult(
                source = ImageSource(
                    source = Buffer().write(bytes),
                    fileSystem = options.fileSystem,
                ),
                mimeType = networkFile.mimeType.takeIf { it.isNotBlank() },
                dataSource = DataSource.NETWORK,
            )
        }
    }

    class Factory : Fetcher.Factory<NetworkFile> {
        override fun create(
            data: NetworkFile,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            return if (isSupportedPath(data.path) && data.remote is Smb) {
                NetworkFileFetcher(data, options)
            } else {
                null
            }
        }

        private fun isSupportedPath(path: String): Boolean {
            return path.lowercase().startsWith("smb://")
        }
    }

    companion object {
        // Limit concurrent SMB downloads for image decoding to avoid pressure on iOS memory.
        private val networkFetchSemaphore = Semaphore(3)
    }
}

internal class NetworkFileKeyer : Keyer<NetworkFile> {
    override fun key(data: NetworkFile, options: Options): String {
        val remoteId = data.remote.id.ifBlank {
            "${data.remote.schema}://${data.remote.host}:${data.remote.port}/${data.remote.path}"
        }
        val modTime = data.modTime.ifBlank { "unknown" }
        return "network-file:$remoteId:${data.path}:$modTime:${data.size}"
    }
}

private object SmbBridgeFileReader {

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

    suspend fun readBytes(networkFile: NetworkFile): ByteArray = withContext(Dispatchers.Default) {
        val remote = networkFile.remote as? Smb
            ?: throw IllegalArgumentException("NetworkFile remote must be Smb for SMB protocol")

        val (shareName, filePath) = extractShareAndFilePath(networkFile.path)
        if (shareName.isBlank() || filePath.isBlank()) {
            throw IllegalArgumentException("Invalid SMB file path: ${networkFile.path}")
        }

        withSession(remote) { sessionId ->
            val response = invokeBridge(
                BridgeRequest(
                    action = "readFile",
                    sessionId = sessionId,
                    share = shareName,
                    path = filePath,
                )
            )

            val encoded = response.dataBase64
                ?: throw IllegalStateException("SMB bridge readFile response missing data")
            Base64.decode(encoded)
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

    private fun extractShareAndFilePath(rawPath: String): Pair<String, String> {
        val pathWithoutScheme = rawPath.substringAfter("://", rawPath)
        val pathWithoutHost = if (rawPath.contains("://")) {
            pathWithoutScheme.substringAfter('/', "")
        } else {
            pathWithoutScheme
        }
        val normalized = pathWithoutHost.trim().trim('/')
        if (normalized.isBlank()) {
            return "" to ""
        }

        val share = normalized.substringBefore('/')
        val path = normalized.substringAfter('/', "")
        val decodedPath = try {
            decodeUrlPath(path)
        } catch (_: Exception) {
            path
        }

        return share to decodedPath
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
    val dataBase64: String? = null,
)
