package com.alpha.networkfile.storage.remote

import kotlinx.serialization.Serializable

/**
 *  Remote + Path
 */
@Serializable
sealed class RemoteStorage: RcloneRemoteApi {
  abstract val id: String
  abstract val host: String
  abstract val port: Int
  abstract val user: String
  abstract val passwd: String
  abstract override val path: String
  abstract val isCrypt: Boolean
  abstract val description: String
  abstract val addTime: Long
  abstract val lock: String
}