package com.alpha.showcase.api.weibo

import com.alpha.showcase.api.BaseHttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders

private const val WEIBO_BASE_URL = "https://m.weibo.cn/"

class WeiboApi() : BaseHttpClient() {

    suspend fun getUserInfo(uid: String): WeiboUserResponse {
        return get(WEIBO_BASE_URL + "api/container/getIndex") {
            header(HttpHeaders.ContentType, "application/json")
            parameter("type", "uid")
            parameter("value", uid)
        }
    }

    suspend fun getUserPosts(
        uid: String,
        containerid: String,
        page: Int? = null,
        sinceId: Long? = null
    ): WeiboPostsResponse {
        return get(WEIBO_BASE_URL + "api/container/getIndex") {
            header(HttpHeaders.ContentType, "application/json")
            parameter("type", "uid")
            parameter("value", uid)
            parameter("containerid", containerid)
            page?.let { parameter("page", it) }
            sinceId?.let { parameter("since_id", it) }
        }
    }
}