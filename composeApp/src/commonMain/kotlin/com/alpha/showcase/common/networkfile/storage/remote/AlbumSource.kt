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
    data object Netease : MusicPlatform("netease", "网易云音乐")
    data object QQ : MusicPlatform("tencent", "QQ音乐")
    data object Apple : MusicPlatform("apple", "Apple Music")
}

val musicPlatforms = listOf(
    MusicPlatform.QQ,
    MusicPlatform.Netease,
    MusicPlatform.Apple
)