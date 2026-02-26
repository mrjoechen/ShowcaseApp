package com.alpha.showcase.common.repo

import com.alpha.showcase.common.cache.NetworkFileCacheService
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.AlbumSource
import com.alpha.showcase.common.networkfile.storage.remote.GitHubSource
import com.alpha.showcase.common.networkfile.storage.remote.GiteeSource
import com.alpha.showcase.common.networkfile.storage.remote.ImmichSource
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteStorage
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.storage.remote.TMDBSource
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.networkfile.storage.remote.WebDav

class RepoManager : SourceRepository<RemoteApi, Any> {

    private val localSourceRepo by lazy {
        LocalSourceRepo()
    }

    private val githubFileRepo by lazy {
        GithubFileRepo()
    }

    private val giteeFileRepo by lazy {
        GiteeFileRepo()
    }

    private val tmdbSourceRepo by lazy {
        TmdbSourceRepo()
    }

    private val unSplashSourceRepo by lazy {
        UnsplashRepo()
    }

    private val pexelsSourceRepo by lazy {
        PexelsSourceRepo()
    }

    private val webdavSourceRepo by lazy {
        NativeWebdavSourceRepo()
    }

    private val smbSourceRepo by lazy {
        createSmbSourceRepo()
    }

    private val cacheService by lazy {
        NetworkFileCacheService()
    }

    private val immichSourceRepo by lazy {
        ImmichSourceRepo()
    }

    private val albumSourceRepo by lazy {
        AlbumSourceRepo()
    }

    override suspend fun getItem(remoteApi: RemoteApi): Result<Any> {
        TODO("Not yet implemented")
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun getItems(
        remoteApi: RemoteApi,
        recursive: Boolean,
        filter: ((Any) -> Boolean)?
    ): Result<List<Any>> {

        return when (remoteApi) {
            is Local -> {
                localSourceRepo.getItems(remoteApi, recursive, filter).asAnyList()
            }

            is WebDav -> {
                getCachedRemoteStorageItems(
                    remoteApi = remoteApi,
                    recursive = recursive,
                    filter = filter,
                    sourceRepo = webdavSourceRepo,
                )
            }

            is Smb -> {
                smbSourceRepo?.let { smbRepo ->
                    getCachedRemoteStorageItems(
                        remoteApi = remoteApi,
                        recursive = recursive,
                        filter = filter,
                        sourceRepo = smbRepo,
                    )
                } ?: Result.failure(Exception("SMB source is not supported on this platform"))
            }

            is GitHubSource -> {
                githubFileRepo.getItems(remoteApi, recursive, filter).asAnyList()
            }

            is TMDBSource -> {
                tmdbSourceRepo.getItems(remoteApi, recursive).asAnyList()
            }

            is UnSplashSource -> {
                unSplashSourceRepo.getItems(remoteApi).asAnyList()
            }

            is PexelsSource -> {
                pexelsSourceRepo.getItems(remoteApi).asAnyList()
            }

            is GiteeSource -> {
                giteeFileRepo.getItems(remoteApi, recursive, filter).asAnyList()
            }

            is ImmichSource -> {
                immichSourceRepo.getItems(remoteApi, recursive, filter).asAnyList()
            }

            is AlbumSource -> {
                albumSourceRepo.getItems(remoteApi, recursive, filter).asAnyList()
            }

            else -> {
                Result.failure(Exception("Unsupported source!"))
            }
        }
    }

    suspend fun getFileDirItems(remoteApi: RemoteApi, path: String): Result<List<Any>> {
        return when (remoteApi) {
            is WebDav -> {
                webdavSourceRepo.getFileDirItems(remoteApi.copy(path = path)).asAnyList()
            }

            is Smb -> {
                smbSourceRepo?.getFileDirItems(remoteApi.copy(path = path))?.asAnyList()
                    ?: Result.failure(Exception("SMB source is not supported on this platform"))
            }

            else -> {
                Result.failure(Exception("Unsupported source type for file dir items"))
            }
        }
    }

    suspend fun checkConnection(remoteApi: RemoteApi): Result<Any> {
        return try {
            val items = when (remoteApi) {
                is WebDav -> webdavSourceRepo.getItems(remoteApi, false, null).asAnyList()
                is Smb -> smbSourceRepo?.getItems(remoteApi, false, null)?.asAnyList()
                    ?: Result.failure(Exception("SMB source is not supported on this platform"))
                else -> getItems(remoteApi, false)
            }

            if (items.isSuccess) {
                Result.success(true)
            } else {
                Result.failure(Exception("Connection failed"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun <T : RemoteStorage> getCachedRemoteStorageItems(
        remoteApi: T,
        recursive: Boolean,
        filter: ((Any) -> Boolean)?,
        sourceRepo: BatchSourceRepository<T, NetworkFile>,
    ): Result<List<Any>> {
        val networkFilter: ((NetworkFile) -> Boolean)? = filter?.let { anyFilter ->
            { file: NetworkFile -> anyFilter(file) }
        }

        return cacheService.getOrLoad(
            remoteApi = remoteApi,
            recursive = recursive,
            filter = networkFilter,
            repository = sourceRepo,
        ).asAnyList()
    }

    private fun <T> Result<List<T>>.asAnyList(): Result<List<Any>> {
        return this.map { list -> list.map { it as Any } }
    }
}
