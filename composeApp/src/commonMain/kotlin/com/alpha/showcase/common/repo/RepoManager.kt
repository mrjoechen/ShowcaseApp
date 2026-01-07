package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.storage.remote.AlbumSource
import com.alpha.showcase.common.networkfile.storage.remote.GitHubSource
import com.alpha.showcase.common.networkfile.storage.remote.GiteeSource
import com.alpha.showcase.common.networkfile.storage.remote.ImmichSource
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.storage.remote.TMDBSource
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.networkfile.storage.remote.WeiboSource

class RepoManager: SourceRepository<RemoteApi, Any> {

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

    private val immichSourceRepo by lazy {
        ImmichSourceRepo()
    }

    private val albumSourceRepo by lazy {
        AlbumSourceRepo()
    }

    override suspend fun getItem(remoteApi: RemoteApi): Result<Any> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: RemoteApi,
        recursive: Boolean,
        filter: ((Any) -> Boolean)?
    ): Result<List<Any>> {

        return when(remoteApi){
            is Local -> {
                localSourceRepo.getItems(remoteApi, recursive, filter)
            }

            is WebDav -> {
                webdavSourceRepo.getItems(remoteApi, recursive, filter)
            }

            is GitHubSource -> {
                githubFileRepo.getItems(remoteApi, recursive, filter)
            }

            is TMDBSource -> {
                tmdbSourceRepo.getItems(remoteApi, recursive)
            }

            is UnSplashSource -> {
                unSplashSourceRepo.getItems(remoteApi)
            }

            is PexelsSource -> {
                pexelsSourceRepo.getItems(remoteApi)
            }

            is GiteeSource -> {
                giteeFileRepo.getItems(remoteApi, recursive, filter)
            }

            is ImmichSource -> {
                immichSourceRepo.getItems(remoteApi, recursive, filter)
            }

            is AlbumSource -> {
                albumSourceRepo.getItems(remoteApi, recursive, filter)
            }

            else -> {
                Result.failure(Exception("Unsupported source!"))
            }
        }
    }


}