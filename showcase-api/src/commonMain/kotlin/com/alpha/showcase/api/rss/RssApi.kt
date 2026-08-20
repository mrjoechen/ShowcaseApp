package com.alpha.showcase.api.rss

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

class RssApi(
    private val client: HttpClient = HttpClient { expectSuccess = true },
) {
    suspend fun getImageUrls(url: String): List<String> {
        val xml = client.get(url).bodyAsText()
        return RssFeedParser.extractImageUrls(xml, baseUrl = url)
    }
}
