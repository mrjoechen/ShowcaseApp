package com.alpha.showcase.common.ui.source

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.navigation.compose.rememberNavController
import com.alpha.showcase.common.networkfile.storage.StorageSources
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.ui.vm.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceListViewMouseTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun doubleClickOpensSource() = runDesktopComposeUiTest {
        val source = Local(name = "Mouse test source")
        val viewModel = FakeSourceViewModel(source)
        val openedSources = mutableListOf<String>()

        setContent {
            SourceListView(
                navController = rememberNavController(),
                viewModel = viewModel,
                onClick = { openedSources += it.name },
            )
        }

        onNode(
            hasClickAction() and hasAnyDescendant(hasContentDescription(source.name)),
            useUnmergedTree = true,
        ).performMouseInput { doubleClick() }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        assertEquals(listOf(source.name), openedSources)
    }
}

private class FakeSourceViewModel(source: Local) : SourceViewModel() {
    override val sourceListStateFlow: StateFlow<UiState<StorageSources>> = MutableStateFlow(
        UiState.Content(
            StorageSources(
                version = 1,
                versionName = "test",
                id = "test",
                sourceName = "test",
                timeStamp = 0L,
                sources = mutableListOf(source),
            )
        )
    )
}
