package com.alpha.showcase.common.repo

import com.alpha.showcase.api.mtphoto.MTPhotoAlbum
import com.alpha.showcase.api.mtphoto.MTPhotoFileItem
import com.alpha.showcase.common.mtphoto.MTPhotoAuthManager
import com.alpha.showcase.common.mtphoto.MTPhotoFile
import com.alpha.showcase.common.networkfile.storage.remote.MTPhotoSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MTPhotoSourceRepoTest {

    @Test
    fun repositoryMapsAlbumFilesToCoilBackedMediaAndAppliesFilter() = runTest {
        val source = source()
        val repo = MTPhotoSourceRepo(
            authManager = MTPhotoAuthManager(authLoader = { error("not used") }),
            albumLoader = { listOf(MTPhotoAlbum(id = 17, name = "旅行", count = 2)) },
            fileLoader = {
                listOf(
                    file(id = 23, md5 = "image-md5", type = "image/jpeg"),
                    file(id = 24, md5 = "video-md5", type = "video/mp4"),
                )
            },
        )

        val albums = repo.getAlbums(source).getOrThrow()
        val items = repo.getItems(source) { item -> item.type.startsWith("image/") }.getOrThrow()

        assertEquals("旅行", albums.single().name)
        assertEquals(1, items.size)
        val media = items.single().data as MTPhotoFile
        assertEquals(17, media.albumId)
        assertEquals(23, media.fileId)
        assertEquals("image-md5", media.md5)
    }

    @Test
    fun repositoryRequiresSelectedAlbum() = runTest {
        val repo = MTPhotoSourceRepo(
            authManager = MTPhotoAuthManager(authLoader = { error("not used") }),
            fileLoader = { error("must not load") },
        )

        val result = repo.getItems(source().copy(albumId = null, albumName = null))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("album", ignoreCase = true))
    }

    @Test
    fun repositoryManagerRoutesMTPhotoSource() = runTest {
        val repo = MTPhotoSourceRepo(
            authManager = MTPhotoAuthManager(authLoader = { error("not used") }),
            fileLoader = { listOf(file(23, "image-md5", "image/jpeg")) },
        )
        val manager = RepoManager(mtPhotoSourceRepo = repo)

        val result = manager.getItems(source()).getOrThrow()

        assertEquals(23, ((result.single() as com.alpha.showcase.common.ui.play.DataWithType).data as MTPhotoFile).fileId)
    }

    private fun source() = MTPhotoSource(
        name = "Photos",
        url = "https://photos.example",
        apiKey = "secret",
        albumId = 17,
        albumName = "旅行",
    )

    private fun file(id: Int, md5: String, type: String) = MTPhotoFileItem(
        id = id,
        md5 = md5,
        status = 1,
        tokenAt = "2026-01-01",
        fileType = type,
        width = 1600,
        height = 900,
    )
}
