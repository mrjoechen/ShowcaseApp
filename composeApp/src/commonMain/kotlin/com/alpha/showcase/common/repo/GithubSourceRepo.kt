package com.alpha.showcase.common.repo

import com.alpha.showcase.api.github.FILE_TYPE_DIR
import com.alpha.showcase.api.github.GithubApi
import com.alpha.showcase.api.github.GithubFile
import com.alpha.showcase.common.networkfile.storage.external.GitHubSource
import com.alpha.showcase.common.networkfile.storage.external.getOwnerAndRepo

class GithubFileRepo : SourceRepository<GitHubSource, String> {

    override suspend fun getItem(remoteApi: GitHubSource): Result<String> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: GitHubSource,
        recursive: Boolean,
        filter: ((String) -> Boolean)?
    ): Result<List<String>> {


        return try {
            remoteApi.getOwnerAndRepo()?.run {
                val contents = if (remoteApi.token.isBlank()) GithubApi().getFiles(
                    first,
                    second,
                    remoteApi.path,
                    remoteApi.branchName
                ) else GithubApi(remoteApi.token).getFiles(
                    first,
                    second,
                    remoteApi.path,
                    remoteApi.branchName
                )

                if (contents.isNotEmpty()) {
                    if (recursive) {
                        val recursiveContent = mutableListOf<GithubFile>()
                        contents.forEach {
                            if (it.type == FILE_TYPE_DIR) {
                                val subFiles = traverseDirectory(
                                    first,
                                    second,
                                    it.path,
                                    remoteApi.branchName,
                                    "token ${remoteApi.token}"
                                )
                                recursiveContent.addAll(subFiles)
                            } else {
                                recursiveContent.add(it)
                            }
                        }
                        Result.success(recursiveContent.run {
                            map {
                                it.download_url ?: ""
                            }.filter {
                                filter?.invoke(it) ?: true
                            }
                        })
                    } else {
                        Result.success(contents.run {
                            map {
                                it.download_url ?: ""
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
        user: String,
        repo: String,
        path: String,
        branch: String?,
        token: String
    ): List<GithubFile> {

        val result = mutableListOf<GithubFile>()

        val files = GithubApi().getFiles(
            user,
            repo,
            path,
            branch
        )

        for (file in files) {
            if (file.type == "dir") {
                result.addAll(traverseDirectory(user, repo, file.path, branch, token))
            } else {
                result.add(file)
            }
        }

        return result
    }

}

