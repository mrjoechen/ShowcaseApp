package com.alpha.showcase.common.repo

import com.alpha.showcase.api.album.AlbumApi
import com.alpha.showcase.common.networkfile.storage.remote.AlbumSource
import com.alpha.showcase.common.networkfile.storage.remote.MusicPlatform
import com.alpha.showcase.common.ui.play.DataWithType
import com.alpha.showcase.common.utils.Supabase
import io.ktor.http.Url

class AlbumSourceRepo: SourceRepository<AlbumSource, DataWithType> {
    private val api by lazy {
        AlbumApi()
    }

    companion object {
        private var _music_api_url: String? = null
        private var _api_auth: String? = null
        suspend fun getMusicApiUrl(): String {
            return _music_api_url ?: Supabase.getConfigValue("music_api_baseurl")?.also {
                _music_api_url = it
            }?:""
        }

        suspend fun getApiAuth(): String? {
            return _api_auth ?: Supabase.getConfigValue("music_api_auth")?.also {
                _api_auth = it
            }
        }
    }

    override suspend fun getItem(remoteApi: AlbumSource): Result<DataWithType> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: AlbumSource,
        recursive: Boolean,
        filter: ((DataWithType) -> Boolean)?
    ): Result<List<DataWithType>> {
        return try {
            val playlistInfo = extractPlayListTypeAndId(remoteApi.playlistUrl)
                ?: return Result.failure(Exception("Invalid playlist URL or ID"))

            if (playlistInfo.first == MusicPlatform.Apple.key) {
                val musicPlayListString = api.getAppleMusicPlaylistWithKtor(remoteApi.playlistUrl)
                musicPlayListString.map {
                    it.map { song ->
                        DataWithType(
                            data = song.artworkUrl,
                            type = "image/webp"
                        )
                    }
                }

            }else {
                val response = api.getPlaylist(
                    baseUrl = getMusicApiUrl(),
                    server = playlistInfo.first,
                    id = playlistInfo.second,
                    authorization = if (getApiAuth().isNullOrEmpty()) null else "Basic ${getApiAuth()}"
                )
                if (!response.isNullOrEmpty()) {
                    val songs = response.filter {
                            song -> !song.pic.isNullOrEmpty() && !Url("${song.pic}").parameters["id"].isNullOrEmpty()
                    }.map { song ->
                        DataWithType(
                            data = song.pic!!,
                            type = "image/jpeg",
                            extra = if (getApiAuth().isNullOrEmpty()) null else mapOf("Authorization" to "Basic ${getApiAuth()}")
                        )
                    }

                    Result.success(songs)
                } else {
                    Result.failure(Exception("Failed to fetch playlist: PlayList is Empty or error"))
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            Result.failure(ex)
        }
    }

}

fun extractPlayListTypeAndId(urlString: String): Pair<String, String>? {

    //tencent: https://y.qq.com/n/ryqq/playlist/2040302853?a=1&g_f=playctrl
    //or https://i.y.qq.com/n2/m/share/details/taoge.html?platform=11&appshare=android_qq&appversion=14060008&hosteuin=7Kvi7eCFoevl&id=2040302853&ADTAG=wxfshare
    val url = Url(urlString)
    if (url.host.contains("y.qq.com")) {
        val pathSegments = url.rawSegments
        if (pathSegments.contains("playlist")) {
            val index = pathSegments.indexOf("playlist")
            if (index != -1 && index + 1 < pathSegments.size) {
                val id = pathSegments[index + 1]
                id.toLongOrNull()?.let {
                    return Pair(MusicPlatform.QQ.key, it.toString())
                }
            }
        }

        url.parameters["id"]?.let { id ->
            id.toLongOrNull()?.let {
                return Pair(MusicPlatform.QQ.key, it.toString())
            }
        }
    }

    //neteasy: https://music.163.com/m/playlist?id=132368073
    if (url.host.contains("music.163.com")) {
        if (url.rawSegments.contains("playlist")) {
            url.parameters["id"]?.let { id ->
                id.toLongOrNull()?.let {
                    return Pair(MusicPlatform.Netease.key, it.toString())
                }
            }
        }
    }

    // apple music：https://music.apple.com/cn/playlist/mine/pl.u-2aoq8yYIGpRMeq
    // https://music.apple.com/cn/album/born-pink/1654968769
    if (url.host.contains("music.apple.com")) {
        val pathSegments = url.rawSegments
        if (pathSegments.contains("playlist")) {
            val index = pathSegments.indexOf("playlist")
            if (index != -1 && index + 1 < pathSegments.size) {
                val id = pathSegments[index + 1]
                return Pair(MusicPlatform.Apple.key, id)
            }
        }

        if (pathSegments.contains("album")) {
            val index = pathSegments.indexOf("album")
            if (index != -1 && index + 1 < pathSegments.size) {
                val id = pathSegments[index + 1]
                return Pair(MusicPlatform.Apple.key, id)
            }
        }
    }

    return null

}