package com.alpha.showcase.common.networkfile.storage.remote

import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import randomUUID

@Serializable
@SerialName("LOCAL")
class Local(
  override val id: String = randomUUID(),
  override val name: String,
  override val path: String = "/",
  override val isCrypt: Boolean = false,
  override val description: String = "",
  override val addTime: Long = Clock.System.now().toEpochMilliseconds(),
  override val lock: String = "",
  val platform: String = ""
): RemoteStorage() {

  @Transient
  override val host: String = ""
  @Transient
  override val port: Int = -1
  @Transient
  override val user: String = ""
  @Transient
  override val passwd: String = ""

  override fun genRcloneOption(): List<String> {

    val options = ArrayList<String>()
    options.add(name)
    options.add("local")
    return options
  }

  override fun genRcloneConfig(): Map<String, String> {
    val config = mutableMapOf<String, String>()
    config["type"] = "local"
    return config
  }
}