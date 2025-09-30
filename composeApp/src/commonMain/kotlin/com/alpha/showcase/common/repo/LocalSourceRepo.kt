package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.ext.toRemote
import com.alpha.showcase.common.networkfile.storage.remote.Local
import getPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class LocalSourceRepo: SourceRepository<Local, NetworkFile> {
    override suspend fun getItem(remoteApi: Local): Result<NetworkFile> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: Local,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?
    ): Result<List<NetworkFile>> {
        return withContext(Dispatchers.Default){
            getPlatform().listFiles(remoteApi.path).map {
                NetworkFile(
                    remoteApi,
                    it.path,
                    it.fileName,
                    it.isDirectory,
                    it.size,
                    it.mimeType,
                    it.modTime
                )
            }.let { fileList ->
                Result.success(
                    filter?.let {
                        fileList.filter { filter.invoke(it) }
                    } ?: fileList
                )
            }
        }
    }

}
