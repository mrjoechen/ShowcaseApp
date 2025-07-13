package com.alpha.showcase.api.immich

import com.alpha.showcase.api.BaseHttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody

class ImmichApi : BaseHttpClient() {

    suspend fun getAlbumsWithApikey(baseUrl: String, apiKey: String): List<Album>? {
        return get(baseUrl + "api/albums") {
            header("x-api-key", apiKey)
        }
    }

    suspend fun getAlbumWithApikey(baseUrl: String, apiKey: String, id: String): Album? {
        return get("$baseUrl/api/albums/$id") {
            header("x-api-key", apiKey)
        }
    }

    suspend fun getAssetWithApikey(baseUrl: String, apiKey: String, id: String): Asset? {
        return get("$baseUrl/api/assets/$id") {
            header("x-api-key", apiKey)
        }
    }
    suspend fun getAssetOriginalWithApikey(baseUrl: String, apiKey: String, id: String): Asset? {
        return get("$baseUrl/api/assets/$id/original") {
            header("x-api-key", apiKey)
        }
    }
    suspend fun getAssetThumbWithApikey(
        baseUrl: String,
        apiKey: String,
        id: String,
        size: String = "preview"
    ): Asset? {
        return get("$baseUrl/api/assets/$id/thumbnail") {
            header("x-api-key", apiKey)
            parameter("size", size)
        }
    }

    suspend fun getAlbumsWithAccessToken(baseUrl: String, accessToken: String): List<Album>? {
        return get("$baseUrl/api/albums") {
            header("Authorization", accessToken)
        }
    }

    suspend fun getAlbumWithAccessToken(baseUrl: String, accessToken: String, id: String): Album? {
        return get("$baseUrl/api/albums/$id") {
            header("Authorization", accessToken)
        }
    }

    suspend fun getAssetWithAccessToken(baseUrl: String, accessToken: String, id: String): Asset? {
        return get("$baseUrl/api/assets/$id") {
            header("Authorization", accessToken)
        }
    }

    suspend fun getAssetOriginalWithAccessToken(baseUrl: String, accessToken: String, id: String): Asset? {
        return get("$baseUrl/api/assets/$id/original") {
            header("Authorization", accessToken)
        }
    }

    suspend fun getAssetThumbWithAccessToken(
        baseUrl: String,
        accessToken: String,
        id: String,
        size: String = "preview"
    ): Asset? {
        return get("$baseUrl/api/assets/$id/thumbnail") {
            header("Authorization", accessToken)
            parameter("size", size)
        }
    }

    suspend fun login(baseUrl: String, loginRequest: LoginRequest): LoginResponse? {
        return post("$baseUrl/api/auth/login") {
            setBody(loginRequest)
        }
    }
}