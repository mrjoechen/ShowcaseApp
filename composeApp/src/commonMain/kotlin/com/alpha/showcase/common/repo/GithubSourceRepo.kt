package com.alpha.showcase.common.repo

import com.alpha.showcase.api.github.FILE_TYPE_DIR
import com.alpha.showcase.api.github.GithubApi
import com.alpha.showcase.api.github.GithubFile
import com.alpha.showcase.common.networkfile.storage.remote.GitHubSource
import com.alpha.showcase.common.networkfile.storage.remote.getOwnerAndRepo
import com.alpha.showcase.common.utils.Supabase

class GithubFileRepo : SourceRepository<GitHubSource, String> {
    override suspend fun getItem(remoteApi: GitHubSource): Result<String> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: GitHubSource,
        recursive: Boolean,
        filter: ((String) -> Boolean)?
    ): Result<List<String>> {

        val githubApi = GithubApi(remoteApi.token)

        return try {
            remoteApi.getOwnerAndRepo()?.run {
                val contents = githubApi.getFiles(
                    first,
                    second,
                    remoteApi.path,
                    remoteApi.branchName
                )

                val proxyPrefix = Supabase.getValue("proxy_config", "proxy_key", "github", "proxy_url") ?: ""

                if (contents.isNotEmpty()) {
                    if (recursive) {
                        val recursiveContent = mutableListOf<GithubFile>()
                        contents.forEach {
                            if (it.type == FILE_TYPE_DIR) {
                                val subFiles = traverseDirectory(
                                    githubApi,
                                    first,
                                    second,
                                    it.path,
                                    remoteApi.branchName
                                )
                                recursiveContent.addAll(subFiles)
                            } else {
                                recursiveContent.add(it)
                            }
                        }
                        Result.success(recursiveContent.run {
                            map {
                                proxyPrefix + it.download_url
                            }.filter {
                                filter?.invoke(it) ?: true
                            }
                        })
                    } else {
                        Result.success(contents.run {
                            map {
                                proxyPrefix + it.download_url
                            }.filter {
                                filter?.invoke(it) ?: true
                            }
                        })
                    }
                } else {
                    Result.failure(Exception("Contents is empty!"))
                }

            } ?: Result.failure(Exception("Url error!"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }

    }



    private suspend fun traverseDirectory(
        githubApi: GithubApi,
        user: String,
        repo: String,
        path: String,
        branch: String?,
    ): List<GithubFile> {

        val result = mutableListOf<GithubFile>()

        val files = githubApi.getFiles(
            user,
            repo,
            path,
            branch
        )

        for (file in files) {
            if (file.type == "dir") {
                result.addAll(traverseDirectory(githubApi, user, repo, file.path, branch))
            } else {
                result.add(file)
            }
        }

        return result
    }

}

