package com.alpha.showcase.common.networkfile.storage.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("MusicAlbum")
data class AlbumSource(
    override val name: String,
    val playlistUrl: String
) : RemoteApi


sealed class MusicPlatform(val key: String, val platformName: String) {
    data object Netease : MusicPlatform("netease", "Netease Cloud Music")
    data object QQ : MusicPlatform("tencent", "QQ Music")
    data object Apple : MusicPlatform("apple", "Apple Music")
}

val musicPlatforms = listOf(
    MusicPlatform.QQ,
    MusicPlatform.Netease,
    MusicPlatform.Apple
)
