package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.WebDavClient
import com.alpha.showcase.common.networkfile.storage.remote.WebDav

class NativeWebdavSourceRepo : SourceRepository<WebDav, String> {

    private lateinit var webDavClient: WebDavClient

    override suspend fun getItem(remoteApi: WebDav): Result<String> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: WebDav,
        recursive: Boolean,
        filter: ((String) -> Boolean)?
    ): Result<List<String>> {
        webDavClient = WebDavClient(remoteApi.url, remoteApi.user, remoteApi.passwd)

        return try {
            val contents = webDavClient.listFiles(remoteApi.path)
            if (contents.isNotEmpty()) {
                if (recursive) {
                    val recursiveContent = mutableListOf<String>()
                    contents.forEach {
                        if (it.isDirectory) {
                            val subFiles = webDavClient.listFiles("${remoteApi.path}/${it.path}")
                            recursiveContent.addAll(subFiles.map { subFile -> subFile.path })
                        } else {
                            recursiveContent.add(it.path)
                        }
                    }
                    Result.success(recursiveContent.run {
                        filter?.let { f ->
                            filter { f(it) }
                        } ?: this
                    }.map { "${remoteApi.url}/$it" })
                } else {
                    val stringList = contents.filter { filter?.invoke(it.path) ?: true }.map { "${remoteApi.url}/${it.path}" }
                    Result.success(stringList)
                }
            } else {
                Result.failure(Exception("No content found."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}