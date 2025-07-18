package com.alpha.showcase.api.album

import com.alpha.showcase.api.BaseHttpClient
import com.alpha.showcase.repo.album.PlaylistData
import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.decodeFromString

class AlbumApi : BaseHttpClient() {
    val jsonFormat = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }
    suspend fun getPlaylist(
        baseUrl: String,
        type: String = "playlist",
        server: String,
        id: String,
        authorization: String? = null
    ): List<PlaylistData>? {
        return get(baseUrl) {
            url{
                parameters.append("type", type)
                parameters.append("server", server)
                parameters.append("id", id)
            }
            header(HttpHeaders.ContentType, "application/json")
            authorization?.let { header(HttpHeaders.Authorization, it) }
        }
    }
    suspend fun getAppleMusicPlaylistWithKtor(url: String): Result<List<AppleMusicAlbum>> {
        return runCatching {
            val response: HttpResponse = get(url)
            if (!response.status.isSuccess()) {
                throw Exception("Failed to download Apple Music playlist: ${response.status}")
            }

            val htmlContent = response.bodyAsText()
            val jsonString = extractJsonWithKsoup(htmlContent)
                ?: throw Exception("Failed to extract JSON from response body")

            val appleMusicData = jsonFormat.decodeFromString<List<AppleMusicData>>(jsonString)
            val albums = appleMusicData.firstOrNull()?.albumList()
                ?: throw Exception("No valid data found in JSON")
            albums
        }
    }


    private fun extractJsonWithKsoup(htmlContent: String): String? {
        return try {
            val document = Ksoup.parse(htmlContent)
            val scriptElement = document.selectFirst("#serialized-server-data")
            scriptElement?.data()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

}