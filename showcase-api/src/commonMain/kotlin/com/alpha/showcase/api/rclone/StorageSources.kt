package com.alpha.showcase.api.rclone

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class StorageSources(
  val version: Int,
  val versionName: String,
  val id: String,
  val sourceName: String,
  val timeStamp: Long,
  val sources: MutableList<RemoteApi<@Contextual Any>>
) {
  override fun equals(other: Any?): Boolean {
    return super.equals(other) && sources === (other as StorageSources).sources
  }
}


fun StorageSources.add(remoteApi: RemoteApi<Any>): StorageSources {
  sources.add(remoteApi)
  return this
}

interface RemoteApi<out T> {

  val name: String

  fun connect(): Boolean

  fun getList(params: Map<String, Any>): List<T>

}