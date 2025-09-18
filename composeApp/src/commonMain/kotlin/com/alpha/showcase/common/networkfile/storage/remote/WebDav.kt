@file:OptIn(ExperimentalTime::class)

package com.alpha.showcase.common.networkfile.storage.remote

import com.alpha.showcase.common.networkfile.storage.WEBDAV
import com.alpha.showcase.common.networkfile.util.RConfig
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.set
import kotlin.time.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import randomUUID
import kotlin.time.ExperimentalTime

@Serializable
@SerialName("WEBDAV")
data class WebDav(
  override val id: String = randomUUID(),
  val url: String,
  override val user: String,
  override val passwd: String,
  override val name: String,
  override val path: String = "/",
  override val isCrypt: Boolean = false,
  override val description: String = "",
  override val addTime: Long = Clock.System.now().toEpochMilliseconds(),
  override val lock: String = ""
): RemoteStorage() {

  override val host: String
    get() = Url(prefixProtocolUrl()).host

  override val port: Int
    get() {
      val p = Url(prefixProtocolUrl()).port
      return if(p == -1) WEBDAV.defaultPort else p
    }

  override fun genRcloneOption(): List<String> {

    val options = ArrayList<String>()
    options.add(name)
    options.add("webdav")
    options.add("url")
    options.add(getRealUrl())
    options.add("vendor")
    options.add("other")
    options.add("user")
    options.add(user)
    options.add("pass")
    options.add(RConfig.decrypt(passwd))
    return options
  }

  override fun genRcloneConfig(): Map<String, String> {
    val config = mutableMapOf<String, String>()
//    config["type"] = "webdav"
//    config["url"] = getRealUrl()
//    config["vendor"] = "other"
//    config["user"] = user
//    config["pass"] = RConfig.decrypt(passwd)
    return config
  }


  private fun getRealUrl(): String{
    val protocol = if (url.contains("https")) URLProtocol.HTTPS else URLProtocol.HTTP

    val urlBuilder = URLBuilder().apply {
      this.protocol = protocol
      this.host = this@WebDav.host
      this.port = this@WebDav.port
      this.pathSegments = Url(prefixProtocolUrl()).rawSegments
    }
    return urlBuilder.buildString()
  }

  private fun prefixProtocolUrl(): String{
    return if (url.contains("http://") || url.contains("http://")){
      url
    }else {
      "http://$url"
    }
  }
}