package com.alpha.showcase.common.repo

import com.alpha.showcase.api.mtphoto.MTPhotoAlbum
import com.alpha.showcase.api.mtphoto.MTPhotoFileItem
import com.alpha.showcase.common.mtphoto.MTPhotoAuthManager
import com.alpha.showcase.common.mtphoto.MTPhotoFile
import com.alpha.showcase.common.networkfile.storage.remote.MTPhotoSource
import com.alpha.showcase.common.ui.play.isImage
import com.alpha.showcase.common.ui.play.isVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
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
                    file(id = 23, md5 = "image-md5", fileName = "photo.JPG"),
                    file(id = 24, md5 = "video-md5", fileName = "clip.mp4"),
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
        assertEquals("photo.JPG", media.fileName)
        assertEquals("2026-01-01", media.tokenAt)
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
    fun repositoryMapsAndroidReferenceFieldsWhenFileNameIsAbsent() = runTest {
        val repo = MTPhotoSourceRepo(
            authManager = MTPhotoAuthManager(authLoader = { error("not used") }),
            fileLoader = {
                listOf(
                    MTPhotoFileItem(
                        id = 23,
                        md5 = "image-md5",
                        status = 1,
                        tokenAt = "2026-01-02T10:00:00.000Z",
                        fileType = "image/jpeg",
                        duration = 1.5f,
                        fileSize = "1234",
                        width = 1600,
                        height = 900,
                    )
                )
            },
        )

        val media = repo.getItems(source()).getOrThrow().single().data as MTPhotoFile

        assertEquals("23.jpeg", media.fileName)
        assertEquals("image/jpeg", media.mimeType)
        assertEquals(1600, media.width)
        assertEquals(900, media.height)
        assertEquals(1.5f, media.duration)
        assertEquals("1234", media.fileSize)
    }

    @Test
    fun repositoryKeepsEverySupportedExtendedImageAndVideoType() = runTest {
        val fileNames = listOf(
            "vector.SVG",
            "favicon.ico",
            "camera.avi",
            "archive.wmv",
            "stream.flv",
            "phone.m4v",
            "mobile.3gp",
        )
        val repo = MTPhotoSourceRepo(
            authManager = MTPhotoAuthManager(authLoader = { error("not used") }),
            fileLoader = {
                fileNames.mapIndexed { index, fileName ->
                    file(id = index + 1, md5 = "md5-$index", fileName = fileName)
                }
            },
        )

        val items = repo.getItems(source()).getOrThrow()

        assertEquals(fileNames, items.map { (it.data as MTPhotoFile).fileName })
        assertTrue(items.take(2).all { it.isImage() })
        assertTrue(items.drop(2).all { it.isVideo() })
    }

    @Test
    fun repositoryManagerRoutesMTPhotoSource() = runTest {
        val repo = MTPhotoSourceRepo(
            authManager = MTPhotoAuthManager(authLoader = { error("not used") }),
            fileLoader = { listOf(file(23, "image-md5", "photo.jpg")) },
        )
        val manager = RepoManager(mtPhotoSourceRepo = repo)

        val result = manager.getItems(source()).getOrThrow()

        assertEquals(23, ((result.single() as com.alpha.showcase.common.ui.play.DataWithType).data as MTPhotoFile).fileId)
    }

    @Test
    fun repositoryManagerChecksMTPhotoConnectionWithAlbumsWithoutLoadingFiles() = runTest {
        var albumLoads = 0
        var fileLoads = 0
        val repo = MTPhotoSourceRepo(
            authManager = MTPhotoAuthManager(authLoader = { error("not used") }),
            albumLoader = {
                albumLoads += 1
                emptyList()
            },
            fileLoader = {
                fileLoads += 1
                error("connection probe must not load album files")
            },
        )

        val result = RepoManager(mtPhotoSourceRepo = repo).checkConnection(source())

        assertTrue(result.isSuccess)
        assertEquals(1, albumLoads)
        assertEquals(0, fileLoads)
    }

    @Test
    fun repositoryManagerPropagatesMTPhotoConnectionCancellation() = runTest {
        val cancellation = CancellationException("MTPhoto connection probe cancelled")
        val repo = MTPhotoSourceRepo(
            authManager = MTPhotoAuthManager(authLoader = { error("not used") }),
            albumLoader = { throw cancellation },
            fileLoader = { error("connection probe must not load album files") },
        )

        val thrown = assertFailsWith<CancellationException> {
            RepoManager(mtPhotoSourceRepo = repo).checkConnection(source())
        }

        assertSame(cancellation, thrown)
    }

    private fun source() = MTPhotoSource(
        name = "Photos",
        url = "https://photos.example",
        apiKey = "secret",
        albumId = 17,
        albumName = "旅行",
    )

    private fun file(id: Int, md5: String, fileName: String) = MTPhotoFileItem(
        id = id,
        md5 = md5,
        fileName = fileName,
        tokenAt = "2026-01-01",
    )
}
