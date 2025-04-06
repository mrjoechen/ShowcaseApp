package com.alpha.showcase.common.repo

import com.alpha.showcase.api.gitee.FILE_TYPE_DIR
import com.alpha.showcase.api.gitee.GiteeApi
import com.alpha.showcase.api.gitee.GiteeFile
import com.alpha.showcase.common.networkfile.storage.remote.GiteeSource
import com.alpha.showcase.common.networkfile.storage.remote.getOwnerAndRepo

class GiteeFileRepo : SourceRepository<GiteeSource, String> {

    override suspend fun getItem(remoteApi: GiteeSource): Result<String> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: GiteeSource,
        recursive: Boolean,
        filter: ((String) -> Boolean)?
    ): Result<List<String>> {


        return try {
            remoteApi.getOwnerAndRepo()?.run {
                val githubApi = GiteeApi(remoteApi.token)
                val contents = githubApi.getFiles(
                    first,
                    second,
                    remoteApi.path,
                    remoteApi.branchName
                )

                if (contents.isNotEmpty()) {
                    if (recursive) {
                        val recursiveContent = mutableListOf<GiteeFile>()
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
        giteeApi: GiteeApi,
        user: String,
        repo: String,
        path: String,
        branch: String?
    ): List<GiteeFile> {

        val result = mutableListOf<GiteeFile>()
        val files = giteeApi.getFiles(
            user,
            repo,
            path,
            branch
        )

        for (file in files) {
            if (file.type == "dir") {
                result.addAll(traverseDirectory(giteeApi, user, repo, file.path, branch))
            } else {
                result.add(file)
            }
        }

        return result
    }

}

