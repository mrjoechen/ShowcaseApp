package com.alpha.showcase.api.album

import com.alpha.showcase.api.BaseHttpClient
import com.alpha.showcase.api.weibo.WeiboPostsResponse
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders

class AlbumApi : BaseHttpClient() {

    suspend fun getPlaylist(
        baseUrl: String,
        type: String = "playlist",
        server: String,
        id: String,
        authorization: String? = null
    ): List<WeiboPostsResponse>? {
        return get(baseUrl) {
            header(HttpHeaders.ContentType, "application/json")
            authorization?.let { header(HttpHeaders.Authorization, it) }
            parameter("type", type)
            parameter("server", server)
            parameter("id", id)
        }
    }

}