package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi

interface SourceRepository<T: RemoteApi, out R> {
  suspend fun getItem(remoteApi: T): Result<R>
  suspend fun getItems(remoteApi: T, recursive: Boolean = false, filter: ((R) -> Boolean)? = null): Result<List<R>>
}

interface FileDirSource<T: RcloneRemoteApi, out R>{
  suspend fun getFileDirItems(remoteApi: T): Result<List<R>>
}

interface BatchSourceRepository<T : RemoteApi, out R> {
  suspend fun streamItems(
    remoteApi: T,
    recursive: Boolean = false,
    filter: ((R) -> Boolean)? = null,
    batchSize: Int = 200,
    onBatch: suspend (List<R>) -> Unit
  ): Result<Long>
}
