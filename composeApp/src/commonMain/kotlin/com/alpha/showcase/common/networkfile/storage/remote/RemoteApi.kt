package com.alpha.showcase.common.networkfile.storage.remote

import com.alpha.showcase.common.networkfile.model.NetworkFile
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface RemoteApi<out T> {
  val name: String
}

@Serializable
@SerialName("RemoteApi")
open class RemoteApiDefaultImpl(
  override val name: String,
  val apiType: String,
  val params: Map<String, String>,
  val addTime: Long = Clock.System.now().toEpochMilliseconds()
): RemoteApi<Any>


interface RcloneRemoteApi: RemoteApi<NetworkFile> {

  val path: String

  fun genRcloneOption(): List<String>

  fun genRcloneConfig(): Map<String, String>
}

interface OAuthRcloneApi : RcloneRemoteApi {
    var token: String
}
