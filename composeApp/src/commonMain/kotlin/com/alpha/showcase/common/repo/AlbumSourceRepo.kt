package com.alpha.showcase.common.repo

import com.alpha.showcase.api.album.AlbumApi
import com.alpha.showcase.common.networkfile.storage.remote.AlbumSource
import com.alpha.showcase.common.networkfile.storage.remote.MusicPlatform
import com.alpha.showcase.common.ui.play.DataWithType
import com.alpha.showcase.common.utils.Supabase
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException

class AlbumSourceRepo: SourceRepository<AlbumSource, DataWithType> {
    private val api by lazy {
        AlbumApi()
    }

    companion object {
        private var _music_api_url: String? = null
        private var _api_auth: String? = null
        suspend fun getMusicApiUrl(): String {
            _music_api_url?.let { return it }
            val configuredUrl = try {
                Supabase.getConfigValue("music_api_baseurl")
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
            return configuredUrl?.takeIf { it.isNotBlank() }
                ?.also { _music_api_url = it }
                ?: throw IllegalStateException("music_api_baseurl is not configured")
        }

        suspend fun getApiAuth(): String? {
            _api_auth?.let { return it }
            val configuredAuth = try {
                Supabase.getConfigValue("music_api_auth")
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
            return configuredAuth?.takeIf { it.isNotBlank() }
                ?.also { _api_auth = it }
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

            when (
                val backend = resolveAlbumRequestBackend(
                    platform = playlistInfo.first,
                    configuredUrl = ::getMusicApiUrl,
                    configuredAuth = ::getApiAuth,
                )
            ) {
                AlbumRequestBackend.AppleMusic -> {
                    val musicPlayListString = api.getAppleMusicPlaylistWithKtor(remoteApi.playlistUrl)
                    musicPlayListString.map {
                        it.map { song ->
                            DataWithType(
                                data = song.artworkUrl,
                                type = "image/webp"
                            )
                        }
                    }
                }

                is AlbumRequestBackend.MusicApi -> {
                    val response = api.getPlaylist(
                        baseUrl = backend.baseUrl,
                        server = playlistInfo.first,
                        id = playlistInfo.second,
                        authorization = backend.authorization,
                    )
                    if (!response.isNullOrEmpty()) {
                        val songs = response.filter {
                                song -> !song.pic.isNullOrEmpty() && !Url("${song.pic}").parameters["id"].isNullOrEmpty()
                        }.map { song ->
                            DataWithType(
                                data = song.pic!!,
                                type = "image/jpeg",
                                extra = backend.authorization?.let { mapOf("Authorization" to it) }
                            )
                        }

                        Result.success(songs)
                    } else {
                        Result.failure(Exception("Failed to fetch playlist: PlayList is Empty or error"))
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: AlbumPlatformUnavailableException) {
            Result.failure(error)
        } catch (error: Throwable) {
            Result.failure(Exception("Failed to fetch album items", error))
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
