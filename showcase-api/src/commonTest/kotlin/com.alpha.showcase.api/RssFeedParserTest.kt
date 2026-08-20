package com.alpha.showcase.api

import com.alpha.showcase.api.rss.RssFeedParser
import kotlin.test.Test
import kotlin.test.assertEquals

class RssFeedParserTest {

    @Test
    fun rssExtractionSupportsMediaEnclosuresHtmlAndChannelImagesWithoutDuplicates() {
        val urls = RssFeedParser.extractImageUrls(
            xml = """
                <rss xmlns:media="http://search.yahoo.com/mrss/" version="2.0">
                  <channel>
                    <image><url>https://cdn.example.com/channel.png</url></image>
                    <item>
                      <media:content url=" https://cdn.example.com/hero.jpg " type="image/jpeg" />
                      <media:thumbnail url="https://cdn.example.com/hero.jpg" />
                      <enclosure url="https://cdn.example.com/photo.webp" type="image/webp" />
                      <description><![CDATA[
                        <p><img src="/inline.png"></p>
                      ]]></description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent(),
            baseUrl = "https://feeds.example.com/news.xml",
        )

        assertEquals(
            listOf(
                "https://cdn.example.com/channel.png",
                "https://cdn.example.com/hero.jpg",
                "https://cdn.example.com/photo.webp",
                "https://feeds.example.com/inline.png",
            ),
            urls,
        )
    }

    @Test
    fun atomExtractionSupportsImageEnclosuresAndHtmlContent() {
        val urls = RssFeedParser.extractImageUrls(
            xml = """
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/">
                  <entry>
                    <link rel="enclosure" type="image/jpeg" href="https://cdn.example.com/atom.jpg" />
                    <media:thumbnail url="https://cdn.example.com/thumb.jpg" />
                    <content type="html"><![CDATA[<img src="gallery/inside.png">]]></content>
                  </entry>
                </feed>
            """.trimIndent(),
            baseUrl = "https://feeds.example.com/path/feed.xml",
        )

        assertEquals(
            listOf(
                "https://cdn.example.com/atom.jpg",
                "https://cdn.example.com/thumb.jpg",
                "https://feeds.example.com/path/gallery/inside.png",
            ),
            urls,
        )
    }
}
