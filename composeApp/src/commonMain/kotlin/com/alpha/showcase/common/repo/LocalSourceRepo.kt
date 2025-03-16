package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.ext.toRemote
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.utils.getExtension
import getPlatform


class LocalSourceRepo: SourceRepository<Local, NetworkFile> {
    override suspend fun getItem(remoteApi: Local): Result<NetworkFile> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: Local,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?
    ): Result<List<NetworkFile>> {
        return getPlatform().listFiles(remoteApi.path).map {
            NetworkFile(
                remoteApi.toRemote(),
                it.toString(),
                it.name,
                it.isRoot,
                0,
                it.name.getExtension(),
                it.toString()
            )
        }.let {
            Result.success(it)
        }
    }

}
