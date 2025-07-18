package com.alpha.showcase.common.networkfile.storage.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("Weibo")
data class WeiboSource(
    override val name: String,
    val uid: String,
    val containOriginal: Boolean = true,
    val containRetweet: Boolean = false,
) : RemoteApi