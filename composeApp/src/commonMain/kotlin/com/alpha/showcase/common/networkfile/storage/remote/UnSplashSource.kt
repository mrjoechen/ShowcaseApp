package com.alpha.showcase.common.networkfile.storage.remote

import com.alpha.showcase.api.unsplash.UnsplashOrientation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
@SerialName("UnSplash")
open class UnSplashSource(
  override val name: String,
  val photoType: String,
  val user: String = "",
  val collectionId: String = "",
  val topic: String = "",
  val orientation: String = UnsplashOrientation.All.storedValue,
  val apiKey: String? = null,
) : RemoteApi
