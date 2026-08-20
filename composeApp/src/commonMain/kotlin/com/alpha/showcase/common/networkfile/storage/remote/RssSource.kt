package com.alpha.showcase.common.networkfile.storage.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("Rss")
data class RssSource(
    override val name: String,
    val url: String,
    val refreshInterval: Long = DEFAULT_RSS_REFRESH_INTERVAL,
) : RemoteApi

const val DEFAULT_RSS_REFRESH_INTERVAL = 3_600_000L
