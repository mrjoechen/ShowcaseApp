package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.RssSource
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class S3RssPlaybackTest {

    @Test
    fun s3PlaybackKeepsObjectMetadataWhileRssUsesItsRemoteUrl() {
        val s3 = S3Source("Archive", "s3.amazonaws.com", "access", "secret", "photos", "us-east-1")
        val rss = RssSource("News", "https://example.com/feed.xml")
        val s3File = networkFile(s3, "album/cat.jpg")

        val s3Playback = assertIs<DataWithType>(
            convertNetworkFilesForPlayback(s3, listOf(s3File)).single(),
        )
        assertEquals(s3File, s3Playback.data)
        assertEquals("image/jpeg", s3Playback.type)
        assertEquals(
            listOf("https://cdn.example.com/feed.jpg"),
            convertNetworkFilesForPlayback(rss, listOf(networkFile(rss, "https://cdn.example.com/feed.jpg"))),
        )
    }

    private fun networkFile(
        source: com.alpha.showcase.common.networkfile.storage.remote.RemoteApi,
        path: String,
    ) = NetworkFile(
        remote = source,
        path = path,
        fileName = path.substringAfterLast('/'),
        isDirectory = false,
        size = 0L,
        mimeType = "image/jpeg",
        modTime = "",
    )
}
