package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.WebDavClient
import com.alpha.showcase.common.networkfile.WebDavFile
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import io.ktor.http.Url
import io.ktor.http.fullPath
import io.ktor.http.hostWithPort

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
                    val urlWithoutPath = remoteApi.url.replace(Url(remoteApi.url).fullPath, "")
                    val subClient = WebDavClient(urlWithoutPath, remoteApi.user, remoteApi.passwd)
                    contents.forEach {
                        if (it.isDirectory) {
                            val subFiles = traverseDirectory(subClient, it.path)
                            recursiveContent.addAll(subFiles.map { subFile -> subFile.path })
                        } else {
                            recursiveContent.add(it.path)
                        }
                    }
                    Result.success(recursiveContent.run {
                        filter?.let { f ->
                            filter { f(it) }
                        } ?: this
                    }.map { "${remoteApi.url.replace(Url(remoteApi.url).fullPath, "")}/$it" })
                } else {
                    val stringList = contents.filter { filter?.invoke(it.path) ?: true }
                        .map { "${remoteApi.url.replace(Url(remoteApi.url).fullPath, "")}/${it.path}" }
                    if (stringList.isEmpty()) {
                        Result.failure(Exception("No content found."))
                    } else Result.success(stringList)
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