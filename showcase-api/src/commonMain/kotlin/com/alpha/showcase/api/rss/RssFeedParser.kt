package com.alpha.showcase.api.rss

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

object RssFeedParser {
    fun extractImageUrls(xml: String, baseUrl: String): List<String> {
        val urls = linkedSetOf<String>()
        val document = Ksoup.parseXml(xml, baseUrl)

        document.getAllElements().forEach { element ->
            val tag = element.tagName().lowercase()
            when {
                tag == "media:content" -> {
                    val type = element.attr("type")
                    if (type.isBlank() || type.startsWith("image/", ignoreCase = true)) {
                        urls.addUrl(element.resolvedAttribute("url"))
                    }
                }

                tag == "media:thumbnail" -> urls.addUrl(element.resolvedAttribute("url"))

                tag == "enclosure" && element.attr("type").startsWith("image/", ignoreCase = true) ->
                    urls.addUrl(element.resolvedAttribute("url"))

                tag == "link" &&
                    element.attr("rel").equals("enclosure", ignoreCase = true) &&
                    element.attr("type").startsWith("image/", ignoreCase = true) ->
                    urls.addUrl(element.resolvedAttribute("href"))

                tag == "itunes:image" || tag == "image" -> {
                    urls.addUrl(element.resolvedAttribute("href"))
                    urls.addUrl(element.resolvedAttribute("url"))
                }

                tag == "url" && element.parent()?.tagName()?.equals("image", ignoreCase = true) == true ->
                    urls.addUrl(element.text())

                tag == "description" || tag == "content:encoded" || tag == "content" || tag == "summary" -> {
                    extractHtmlImages(element.text(), baseUrl).forEach { url -> urls.addUrl(url) }
                    extractHtmlImages(element.html(), baseUrl).forEach { url -> urls.addUrl(url) }
                }
            }
        }

        return urls.toList()
    }

    private fun extractHtmlImages(html: String, baseUrl: String): List<String> {
        if (!html.contains("<img", ignoreCase = true)) return emptyList()
        return Ksoup.parseBodyFragment(html, baseUrl)
            .select("img[src]")
            .mapNotNull { image -> image.resolvedAttribute("src").takeIf { it.isNotBlank() } }
    }

    private fun Element.resolvedAttribute(name: String): String {
        if (!hasAttr(name)) return ""
        return absUrl(name).ifBlank { attr(name) }
    }

    private fun MutableSet<String>.addUrl(rawUrl: String?) {
        val url = rawUrl?.trim().orEmpty()
        if (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)) {
            add(url)
        }
    }
}
