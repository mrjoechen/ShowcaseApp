package com.alpha.showcase.common.ui.play

import com.alpha.showcase.api.mtphoto.MTPhotoFileItem
import com.alpha.showcase.common.mtphoto.MTPhotoAuthManager
import com.alpha.showcase.common.mtphoto.MTPhotoFile
import com.alpha.showcase.common.networkfile.storage.remote.MTPhotoSource
import com.alpha.showcase.common.repo.MTPhotoSourceRepo
import com.alpha.showcase.common.repo.RepoManager
import com.alpha.showcase.common.ui.settings.SortRule
import com.alpha.showcase.common.ui.vm.UiState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MTPhotoPlaybackSortTest {

    @Test
    fun mtPhotoNameSortUsesTheRemoteFileNameWithIdAsATieBreaker() = runTest {
        val viewModel = viewModelWithFiles(
            file(id = 20, fileName = "A-photo.jpg", tokenAt = "2026-03-01"),
            file(id = 3, fileName = "z-photo.jpg", tokenAt = "2024-01-01"),
        )

        assertEquals(listOf(20, 3), viewModel.sortedFileIds(SortRule.NameAsc.value))
        assertEquals(listOf(3, 20), viewModel.sortedFileIds(SortRule.NameDesc.value))
    }

    @Test
    fun mtPhotoDateSortUsesTokenAtInsteadOfObjectRendering() = runTest {
        val viewModel = viewModelWithFiles(
            file(id = 20, fileName = "20.jpg", tokenAt = "2026-03-01"),
            file(id = 3, fileName = "3.jpg", tokenAt = "2024-01-01"),
        )

        assertEquals(listOf(3, 20), viewModel.sortedFileIds(SortRule.DateAsc.value))
        assertEquals(listOf(20, 3), viewModel.sortedFileIds(SortRule.DateDesc.value))
    }

    @Test
    fun mtPhotoPagedPlaybackKeepsTheNonCachedFromListFallback() = runTest {
        val viewModel = viewModelWithFiles(
            file(id = 20, fileName = "20.jpg", tokenAt = "2026-03-01"),
            file(id = 3, fileName = "3.jpg", tokenAt = "2024-01-01"),
        )

        val state = viewModel.getPagedImageFileInfo(
            api = source(),
            sortRule = SortRule.DateAsc.value,
            coroutineScope = backgroundScope,
        )
        val content = assertIs<UiState.Content<*>>(state)
        val paging = assertIs<PagingPlayItems>(content.data)

        assertEquals(2, paging.totalCount)
        assertEquals(3, paging[0].mtPhotoFileId())
        assertEquals(20, paging[1].mtPhotoFileId())
    }

    private suspend fun PlayViewModel.sortedFileIds(sortRule: Int): List<Int> {
        val state = getImageFileInfo(source(), sortRule = sortRule)
        val content = assertIs<UiState.Content<*>>(state)
        return assertIs<List<*>>(content.data).map { item ->
            val typed = assertIs<DataWithType>(item)
            assertIs<MTPhotoFile>(typed.data).fileId
        }
    }

    private fun viewModelWithFiles(vararg files: MTPhotoFileItem): PlayViewModel {
        val repo = MTPhotoSourceRepo(
            authManager = MTPhotoAuthManager(authLoader = { error("not used") }),
            fileLoader = { files.toList() },
        )
        return PlayViewModel().also { viewModel ->
            val delegateField = PlayViewModel::class.java.getDeclaredField("sourceRepo\$delegate")
            delegateField.isAccessible = true
            delegateField.set(
                viewModel,
                lazyOf(
                    RepoManager(
                        mtPhotoSourceRepo = repo,
                        defaultCacheServiceProvider = {
                            error("MTPhoto non-cached fallback must not resolve the cache service")
                        },
                    )
                ),
            )
        }
    }

    private fun Any.mtPhotoFileId(): Int {
        val typed = assertIs<DataWithType>(this)
        return assertIs<MTPhotoFile>(typed.data).fileId
    }

    private fun source() = MTPhotoSource(
        name = "Photos",
        url = "https://photos.example",
        apiKey = "secret",
        albumId = 17,
        albumName = "Travel",
    )

    private fun file(id: Int, fileName: String, tokenAt: String) = MTPhotoFileItem(
        id = id,
        md5 = "md5-$id",
        fileName = fileName,
        tokenAt = tokenAt,
    )
}
