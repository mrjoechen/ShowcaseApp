package com.alpha.showcase.common.networkfile.storage.remote

import kotlinx.serialization.Serializable

@Serializable
sealed interface RemoteApi {
  val name: String
}

interface RcloneRemoteApi: RemoteApi {

  val path: String

  fun genRcloneOption(): List<String>

  fun genRcloneConfig(): Map<String, String>
}

interface OAuthRcloneApi : RcloneRemoteApi {
    var token: String
}
