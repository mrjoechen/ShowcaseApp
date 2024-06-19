package com.alpha.showcase.common.networkfile.storage.external

import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
@SerialName("UnSplash")
open class UnSplashSource(
  override val name: String,
  val photoType: String,
  val user: String = "",
  val collectionId: String = "",
  val topic: String = ""
) : RemoteApi<String>