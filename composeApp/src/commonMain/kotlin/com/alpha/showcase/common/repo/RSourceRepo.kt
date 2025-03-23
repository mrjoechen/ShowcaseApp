package com.alpha.showcase.common.repo

import rclone
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi

class RSourceRepo: SourceRepository<RcloneRemoteApi, NetworkFile> {
  private val rclone by lazy {
    rclone()!!
  }

  override suspend fun getItem(remoteApi: RcloneRemoteApi): Result<NetworkFile> {
    val fileInfo = rclone.getFileInfo(remoteApi)
    return fileInfo
  }

  override suspend fun getItems(
    remoteApi: RcloneRemoteApi,
    recursive: Boolean,
    filter: ((NetworkFile) -> Boolean)?
  ): Result<List<NetworkFile>> {

    val fileItems = rclone.getFileItems(remoteApi, recursive, filter)
    return fileItems
  }

}