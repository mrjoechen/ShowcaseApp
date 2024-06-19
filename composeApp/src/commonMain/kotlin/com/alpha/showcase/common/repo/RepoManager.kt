package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.storage.external.GitHubSource
import com.alpha.showcase.common.networkfile.storage.external.PexelsSource
import com.alpha.showcase.common.networkfile.storage.external.TMDBSource
import com.alpha.showcase.common.networkfile.storage.external.UnSplashSource
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi


class RepoManager: SourceRepository<RemoteApi<Any>, Any> {

    private val localSourceRepo by lazy {
        LocalSourceRepo()
    }

    private val rSourceRepo by lazy {
        RSourceRepo()
    }

    private val githubFileRepo by lazy {
        GithubFileRepo()
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

    override suspend fun getItem(remoteApi: RemoteApi<Any>): Result<Any> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: RemoteApi<Any>,
        recursive: Boolean,
        filter: ((Any) -> Boolean)?
    ): Result<List<Any>> {

        return when(remoteApi){
            is Local -> {
                localSourceRepo.getItems(remoteApi, recursive, filter)
            }

            is RcloneRemoteApi -> {
                rSourceRepo.getItems(remoteApi, recursive, filter)
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

            else -> {
                Result.failure(Exception("Unsupported source!"))
            }
        }
    }


}