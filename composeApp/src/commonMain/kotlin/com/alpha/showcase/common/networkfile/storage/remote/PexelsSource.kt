package com.alpha.showcase.common.networkfile.storage.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
@SerialName("Pexels")
open class PexelsSource(
  override val name: String,
  val photoType: String
) : RemoteApi