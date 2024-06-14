package com.alpha.networkfile.storage.external

import com.alpha.networkfile.storage.remote.RemoteApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
@SerialName("UnSplash")
open class UnSplashSource(
  override val name: String,
  val contentType: String,
  val language: String,
  val region: String
) : RemoteApi<String>