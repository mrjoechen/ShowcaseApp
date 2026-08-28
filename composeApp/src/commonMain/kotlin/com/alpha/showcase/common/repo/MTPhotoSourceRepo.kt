package com.alpha.showcase.common.repo

import com.alpha.showcase.api.mtphoto.MTPhotoAlbum
import com.alpha.showcase.api.mtphoto.MTPhotoApi
import com.alpha.showcase.api.mtphoto.MTPhotoFileItem
import com.alpha.showcase.common.mtphoto.MTPhotoAuthManager
import com.alpha.showcase.common.mtphoto.MTPhotoFile
import com.alpha.showcase.common.mtphoto.MTPhotoRuntime
import com.alpha.showcase.common.networkfile.storage.remote.MTPhotoSource
import com.alpha.showcase.common.ui.play.DataWithType
import com.alpha.showcase.common.utils.getMimeType
import kotlinx.coroutines.CancellationException

class MTPhotoSourceRepo(
    private val authManager: MTPhotoAuthManager = MTPhotoRuntime.authManager,
    private val api: MTPhotoApi = MTPhotoApi(),
    private val albumLoader: (suspend (MTPhotoSource) -> List<MTPhotoAlbum>)? = null,
    private val fileLoader: (suspend (MTPhotoSource) -> List<MTPhotoFileItem>)? = null,
) : SourceRepository<MTPhotoSource, DataWithType> {

    override suspend fun getItem(remoteApi: MTPhotoSource): Result<DataWithType> {
        return Result.failure(UnsupportedOperationException("MTPhoto single-item loading is not supported"))
    }

    suspend fun getAlbums(remoteApi: MTPhotoSource): Result<List<MTPhotoAlbum>> = captureResult {
        authManager.register(remoteApi)
        albumLoader?.invoke(remoteApi) ?: loadAlbums(remoteApi)
    }

    suspend fun checkConnection(remoteApi: MTPhotoSource): Result<Unit> =
        getAlbums(remoteApi).map { }

    override suspend fun getItems(
        remoteApi: MTPhotoSource,
        recursive: Boolean,
        filter: ((DataWithType) -> Boolean)?,
    ): Result<List<DataWithType>> = captureResult {
        val albumId = requireNotNull(remoteApi.albumId) { "MTPhoto album must be selected" }
        val sourceKey = authManager.register(remoteApi)
        val files = fileLoader?.invoke(remoteApi) ?: loadFiles(remoteApi, albumId)
        files.map { file ->
            val fileName = file.fileName.ifBlank { file.defaultFileName() }
            val mimeType = file.fileType.takeIf { it.contains('/') } ?: getMimeType(fileName)
            DataWithType(
                data = MTPhotoFile(
                    sourceKey = sourceKey,
                    albumId = albumId,
                    fileId = file.id,
                    md5 = file.md5,
                    fileName = fileName,
                    tokenAt = file.tokenAt,
                    mimeType = mimeType,
                    width = file.width,
                    height = file.height,
                    duration = file.duration,
                    fileSize = file.fileSize,
                ),
                type = mimeType,
            )
        }.filter { filter?.invoke(it) ?: true }
    }

    private suspend fun loadAlbums(source: MTPhotoSource): List<MTPhotoAlbum> {
        val sourceKey = authManager.register(source)
        val auth = authManager.getAuthForKey(sourceKey)
        return api.getAlbums(source.url, auth.headerName, auth.headerValue)
    }

    private suspend fun loadFiles(source: MTPhotoSource, albumId: Int): List<MTPhotoFileItem> {
        val sourceKey = authManager.register(source)
        val auth = authManager.getAuthForKey(sourceKey)
        return api.getAlbumFiles(
            baseUrl = source.url,
            albumId = albumId,
            headerName = auth.headerName,
            headerValue = auth.headerValue,
        )
    }
}

private fun MTPhotoFileItem.defaultFileName(): String {
    val extension = fileType.substringAfterLast('/').trim().trimStart('.')
    return if (extension.isBlank()) id.toString() else "$id.$extension"
}

private suspend inline fun <T> captureResult(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
