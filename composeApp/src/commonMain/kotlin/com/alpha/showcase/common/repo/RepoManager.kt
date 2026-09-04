package com.alpha.showcase.common.repo

import com.alpha.showcase.common.cache.CacheSyncResult
import com.alpha.showcase.common.cache.NetworkFileCacheService
import com.alpha.showcase.common.networkfile.model.NetworkFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import com.alpha.showcase.common.networkfile.storage.remote.AlbumSource
import com.alpha.showcase.common.networkfile.storage.remote.Ftp
import com.alpha.showcase.common.networkfile.storage.remote.GitHubSource
import com.alpha.showcase.common.networkfile.storage.remote.GiteeSource
import com.alpha.showcase.common.networkfile.storage.remote.GallerySource
import com.alpha.showcase.common.networkfile.storage.remote.ImmichSource
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.networkfile.storage.remote.MTPhotoSource
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteStorage
import com.alpha.showcase.common.networkfile.storage.remote.RssSource
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import com.alpha.showcase.common.networkfile.storage.remote.Sftp
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.storage.remote.TMDBSource
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.networkfile.storage.remote.WebDav

/**
 * Info needed for paged loading from cache. [syncCompletion] resolves when the
 * background sync started by this [ensureCacheReady] call finishes — scoped to
 * this one sync, so awaiting it can never pick up another source's event.
 */
data class CachedSourceInfo(
    val sourceType: String,
    val sourceKey: String,
    val remoteApi: RemoteApi,
    val syncCompletion: CompletableDeferred<CacheSyncResult>,
    // True when returned while the first sync is still running with no media yet:
    // the caller should show loading and recover via [syncCompletion].
    val syncPending: Boolean = false,
    // The committed sync_version this read session is pinned to. All count/page
    // reads use it so an in-flight background re-sync (which writes a new version)
    // can't surface a mix of old + new rows. Null = not version-pinned.
    val committedSyncVersion: Long? = null,
    // True when [committedSyncVersion] is a still-growing FIRST sync's version:
    // page reads use append-stable insertion order instead of a content sort so
    // OFFSET windows can't repeat/skip rows as new batches arrive.
    val initialSnapshot: Boolean = false,
)

class RepoManager(
    private val s3SourceRepo: S3SourceRepo = S3SourceRepo(),
    private val rssSourceRepo: RssSourceRepo = RssSourceRepo(),
    private val mtPhotoSourceRepo: MTPhotoSourceRepo = MTPhotoSourceRepo(),
    // Production callers share one process-wide cache service so per-source sync
    // ownership cannot split across RepoManager instances. Tests may inject an
    // isolated in-memory database-backed service.
    cacheService: NetworkFileCacheService? = null,
    private val unSplashSourceRepo: UnsplashRepo = UnsplashRepo(),
    private val pexelsSourceRepo: PexelsSourceRepo = PexelsSourceRepo(),
    private val defaultCacheServiceProvider: () -> NetworkFileCacheService = {
        NetworkFileCacheService.shared
    },
) : SourceRepository<RemoteApi, Any> {

    private val injectedCacheService = cacheService
    private val cacheService by lazy {
        injectedCacheService ?: defaultCacheServiceProvider()
    }

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

    private val webdavSourceRepo by lazy {
        NativeWebdavSourceRepo()
    }

    private val smbSourceRepo by lazy {
        createSmbSourceRepo()
    }

    private val ftpSourceRepo by lazy {
        createFtpSourceRepo()
    }

    private val sftpSourceRepo by lazy {
        createSftpSourceRepo()
    }

    private val immichSourceRepo by lazy {
        ImmichSourceRepo()
    }

    private val albumSourceRepo by lazy {
        AlbumSourceRepo()
    }

    private val gallerySourceRepo by lazy {
        GallerySourceRepo()
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

            is Ftp -> {
                val networkFilter: ((NetworkFile) -> Boolean)? = filter?.let { anyFilter ->
                    { file: NetworkFile -> anyFilter(file) }
                }
                ftpSourceRepo?.getItems(remoteApi, recursive, networkFilter)?.asAnyList()
                    ?: Result.failure(Exception("FTP source is not supported on this platform"))
            }

            is Sftp -> {
                val networkFilter: ((NetworkFile) -> Boolean)? = filter?.let { anyFilter ->
                    { file: NetworkFile -> anyFilter(file) }
                }
                sftpSourceRepo?.getItems(remoteApi, recursive, networkFilter)?.asAnyList()
                    ?: Result.failure(Exception("SFTP source is not supported on this platform"))
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

            is GallerySource -> {
                gallerySourceRepo.getItems(remoteApi, recursive, filter).asAnyList()
            }

            is S3Source -> {
                val networkFilter: ((NetworkFile) -> Boolean)? = filter?.let { anyFilter ->
                    { file: NetworkFile -> anyFilter(file) }
                }
                s3SourceRepo.getItems(remoteApi, recursive, networkFilter).asAnyList()
            }

            is RssSource -> {
                val networkFilter: ((NetworkFile) -> Boolean)? = filter?.let { anyFilter ->
                    { file: NetworkFile -> anyFilter(file) }
                }
                rssSourceRepo.getItems(remoteApi, recursive, networkFilter).asAnyList()
            }

            is MTPhotoSource -> {
                val mediaFilter: ((com.alpha.showcase.common.ui.play.DataWithType) -> Boolean)? =
                    filter?.let { anyFilter ->
                        { item -> anyFilter(item) }
                    }
                mtPhotoSourceRepo.getItems(remoteApi, recursive, mediaFilter).asAnyList()
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

            is Ftp -> {
                ftpSourceRepo?.getFileDirItems(remoteApi.copy(path = path))?.asAnyList()
                    ?: Result.failure(Exception("FTP source is not supported on this platform"))
            }

            is Sftp -> {
                sftpSourceRepo?.getFileDirItems(remoteApi.copy(path = path))?.asAnyList()
                    ?: Result.failure(Exception("SFTP source is not supported on this platform"))
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
                is Ftp -> ftpSourceRepo?.getItems(remoteApi, false, null)?.asAnyList()
                    ?: Result.failure(Exception("FTP source is not supported on this platform"))
                is Sftp -> sftpSourceRepo?.getItems(remoteApi, false, null)?.asAnyList()
                    ?: Result.failure(Exception("SFTP source is not supported on this platform"))
                is UnSplashSource -> unSplashSourceRepo.checkConnection(remoteApi)
                is PexelsSource -> pexelsSourceRepo.checkConnection(remoteApi)
                is S3Source -> s3SourceRepo.checkConnection(remoteApi)
                is MTPhotoSource -> mtPhotoSourceRepo.checkConnection(remoteApi)
                else -> getItems(remoteApi, false)
            }

            if (items.isSuccess) {
                Result.success(true)
            } else {
                Result.failure(items.exceptionOrNull() ?: Exception("Connection failed"))
            }
        } catch (e: CancellationException) {
            throw e
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

    /**
     * Ensures cache is ready and returns info needed for paged loading.
     * Only works for cached sources.
     * Returns null for non-cached sources.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun ensureCacheReady(
        remoteApi: RemoteApi,
        recursive: Boolean,
        supportVideo: Boolean = false,
    ): Result<CachedSourceInfo?> {
        return when (remoteApi) {
            is WebDav -> ensureCachedSourceReady(remoteApi, recursive, webdavSourceRepo, supportVideo)
            is Smb -> smbSourceRepo?.let { ensureCachedSourceReady(remoteApi, recursive, it, supportVideo) }
                ?: Result.failure(Exception("SMB source is not supported on this platform"))
            is UnSplashSource -> ensureCachedSourceReady(remoteApi, recursive, unSplashSourceRepo, supportVideo)
            is PexelsSource -> ensureCachedSourceReady(remoteApi, recursive, pexelsSourceRepo, supportVideo)
            is TMDBSource -> ensureCachedSourceReady(remoteApi, recursive, tmdbSourceRepo, supportVideo)
            is S3Source -> ensureCachedSourceReady(remoteApi, recursive, s3SourceRepo, supportVideo)
            is RssSource -> ensureCachedSourceReady(remoteApi, recursive, rssSourceRepo, supportVideo)
            else -> Result.success(null)
        }
    }

    private suspend fun <T : RemoteApi> ensureCachedSourceReady(
        remoteApi: T,
        recursive: Boolean,
        sourceRepo: BatchSourceRepository<T, NetworkFile>,
        supportVideo: Boolean,
    ): Result<CachedSourceInfo?> {
        return cacheService.ensureCacheReady(
            remoteApi = remoteApi,
            recursive = recursive,
            repository = sourceRepo,
            supportVideo = supportVideo,
        ).map { ready ->
            CachedSourceInfo(
                sourceType = ready.sourceType,
                sourceKey = ready.sourceKey,
                remoteApi = remoteApi,
                syncCompletion = ready.completion,
                syncPending = ready.pending,
                committedSyncVersion = ready.committedSyncVersion,
                initialSnapshot = ready.initialSnapshot,
            )
        }
    }

    /**
     * Count media items in cache.
     */
    suspend fun countMedia(
        cachedSourceInfo: CachedSourceInfo,
        supportVideo: Boolean,
    ): Int {
        return cacheService.countMedia(
            cachedSourceInfo.sourceType,
            cachedSourceInfo.sourceKey,
            supportVideo,
            cachedSourceInfo.committedSyncVersion,
        )
    }

    /**
     * Resolve the current committed sync_version for a source, for re-pinning a read
     * session after a background sync completes.
     */
    suspend fun resolveCommittedSyncVersion(cachedSourceInfo: CachedSourceInfo): Long? {
        return cacheService.resolveCommittedSyncVersion(
            cachedSourceInfo.sourceType,
            cachedSourceInfo.sourceKey,
        )
    }

    /**
     * Resolve the CURRENT index of the media at [path] under a session's pinned
     * version and ordering — identity re-anchoring after a refresh. Null when the
     * session is unversioned or the item no longer exists.
     */
    suspend fun locateMediaIndex(
        cachedSourceInfo: CachedSourceInfo,
        supportVideo: Boolean,
        sortRule: Int,
        path: String,
    ): Int? {
        val version = cachedSourceInfo.committedSyncVersion ?: return null
        return cacheService.locateMediaIndex(
            sourceType = cachedSourceInfo.sourceType,
            sourceKey = cachedSourceInfo.sourceKey,
            supportVideo = supportVideo,
            sortRule = sortRule,
            syncVersion = version,
            insertionOrder = cachedSourceInfo.initialSnapshot,
            path = path,
        )
    }

    /**
     * Load a page of media files from cache and return as NetworkFile list.
     */
    suspend fun loadMediaPage(
        cachedSourceInfo: CachedSourceInfo,
        supportVideo: Boolean,
        sortRule: Int,
        offset: Int,
        limit: Int,
    ): List<NetworkFile> {
        return cacheService.loadMediaPage(
            remoteApi = cachedSourceInfo.remoteApi,
            sourceType = cachedSourceInfo.sourceType,
            sourceKey = cachedSourceInfo.sourceKey,
            supportVideo = supportVideo,
            sortRule = sortRule,
            offset = offset,
            limit = limit,
            syncVersion = cachedSourceInfo.committedSyncVersion,
            insertionOrder = cachedSourceInfo.initialSnapshot,
        )
    }

    private fun <T> Result<List<T>>.asAnyList(): Result<List<Any>> {
        return this.map { list -> list.map { it as Any } }
    }
}
