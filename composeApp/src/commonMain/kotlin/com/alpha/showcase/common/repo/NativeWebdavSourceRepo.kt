package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.WebDavClient
import com.alpha.showcase.common.networkfile.WebDavFile
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.utils.getExtension
import io.ktor.http.Url
import io.ktor.http.fullPath
import io.ktor.http.hostWithPort

class NativeWebdavSourceRepo : SourceRepository<WebDav, NetworkFile> {

    private lateinit var webDavClient: WebDavClient

    override suspend fun getItem(remoteApi: WebDav): Result<NetworkFile> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: WebDav,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?
    ): Result<List<NetworkFile>> {
        webDavClient = WebDavClient(remoteApi.url, remoteApi.user, remoteApi.passwd)

        return try {
            val contents = webDavClient.listFiles(remoteApi.path)
            if (contents.isNotEmpty()) {
                if (recursive) {
                    val recursiveContent = mutableListOf<NetworkFile>()
                    val urlWithoutPath = remoteApi.url.replace(Url(remoteApi.url).fullPath, "")
                    val subClient = WebDavClient(urlWithoutPath, remoteApi.user, remoteApi.passwd)
                    contents.forEach { content ->
                        if (content.isDirectory) {
                            val subFiles = traverseDirectory(subClient, content.path)
                            recursiveContent.addAll(subFiles.map { subFile ->
                                NetworkFile(
                                    remoteApi,
                                    if (subFile.path.startsWith("/")) subFile.path else "/${subFile.path}",
                                    subFile.name,
                                    subFile.contentLength == 0L,
                                    subFile.contentLength,
                                    subFile.name.getExtension(),
                                    subFile.creationDate
                                )
                            })
                        } else {
                            recursiveContent.add(
                                NetworkFile(
                                    remoteApi,
                                    if (content.path.startsWith("/")) content.path else "/${content.path}",
                                    content.name,
                                    content.contentLength == 0L,
                                    content.contentLength,
                                    content.name.getExtension(),
                                    content.creationDate
                                )
                            )
                        }
                    }
                    Result.success(recursiveContent.run {
                        filter?.let { f ->
                            filter { f(it) }
                        } ?: this
                    })
                } else {
                    val resultList = contents
                        .map {
                            NetworkFile(
                                remoteApi,
                                if (it.path.startsWith("/")) it.path else "/${it.path}",
                                it.name,
                                it.contentLength == 0L,
                                it.contentLength,
                                it.name.getExtension(),
                                it.creationDate
                            )
                        }.filter { (filter?.invoke(it) ?: true) && it.path != remoteApi.path && it.path != "/${remoteApi.path}/" }
                    if (resultList.isEmpty()) {
                        Result.failure(Exception("No content found."))
                    } else Result.success(resultList)
                }
            } else {
                Result.failure(Exception("No content found."))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception(e.message))
        }
    }

    private suspend fun traverseDirectory(webDavClient: WebDavClient, path: String): List<WebDavFile> {
        val result = mutableListOf<WebDavFile>()
        val resources: List<WebDavFile> = webDavClient.listFiles(path)
        resources.forEach {
            if (it.path != path) {
                if (it.isDirectory) {
                    result.addAll(traverseDirectory(webDavClient, it.path))
                } else {
                    result.add(it)
                }
            }

        }
        return result
    }
}