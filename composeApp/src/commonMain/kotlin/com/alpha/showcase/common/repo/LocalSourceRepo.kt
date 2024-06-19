package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Local


class LocalSourceRepo: SourceRepository<Local, NetworkFile> {
    override suspend fun getItem(remoteApi: Local): Result<NetworkFile> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: Local,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?
    ): Result<List<NetworkFile>> {
        TODO("Not yet implemented")
    }

}
