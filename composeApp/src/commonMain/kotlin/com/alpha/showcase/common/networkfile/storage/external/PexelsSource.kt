package com.alpha.showcase.common.networkfile.storage.external

import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.ui.play.DataWithType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
@SerialName("Pexels")
open class PexelsSource(
  override val name: String,
  val photoType: String
) : RemoteApi<DataWithType>