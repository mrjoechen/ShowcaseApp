package com.alpha.showcase.common.ui.source

import Screen
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.Text
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation.compose.NavHost
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alpha.showcase.common.networkfile.storage.StorageSources
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.ui.vm.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.getString
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.addSource
import showcaseapp.composeapp.generated.resources.delete
import showcaseapp.composeapp.generated.resources.edit
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceListViewMouseTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun slightlyMovingClickNavigatesAgainAfterReturning() = runDesktopComposeUiTest {
        val source = Local(name = "Navigation test source")
        val viewModel = FakeSourceViewModel(source)
        lateinit var navController: NavHostController

        setContent {
            navController = rememberNavController()
            NavHost(navController, startDestination = "sources") {
                composable("sources") {
                    SourceListView(navController, viewModel = viewModel) {
                        // Immediate input callbacks can run on the test injection thread.
                        runOnUiThread { navController.navigate("play") }
                    }
                }
                composable("play") {
                    Text("Playback")
                }
            }
        }

        repeat(2) {
            onNode(
                hasClickAction() and hasAnyDescendant(hasContentDescription(source.name)),
                useUnmergedTree = true,
            ).performMouseInput {
                moveTo(center)
                press()
                moveBy(Offset(1f, 1f))
                release()
            }
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            onNodeWithText("Playback").assertIsDisplayed()
            runOnUiThread { navController.popBackStack() }
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun scrollingWithinFirstRowDismissesContextMenuWithoutOpeningSource() = runDesktopComposeUiTest {
        val sources = Array(30) { Local(name = "Scroll test source $it") }
        val viewModel = FakeSourceViewModel(*sources)
        val openedSources = mutableListOf<String>()
        val deleteLabel = getString(Res.string.delete)

        setContent {
            SourceListView(
                navController = rememberNavController(),
                viewModel = viewModel,
                onClick = { openedSources += it.name },
            )
        }

        val card = onNode(
            hasClickAction() and hasAnyDescendant(hasContentDescription(sources[0].name)),
            useUnmergedTree = true,
        )
        card.performMouseInput {
            moveTo(center)
            press(MouseButton.Secondary)
            release(MouseButton.Secondary)
        }
        waitForIdle()
        onNodeWithContentDescription(deleteLabel).assertIsDisplayed()
        card.performMouseInput { scroll(Offset(0f, 1f)) }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        onNodeWithContentDescription(sources[0].name).assertIsDisplayed()
        onNodeWithContentDescription(deleteLabel).assertDoesNotExist()
        assertEquals(emptyList(), openedSources)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun remoteLongPressOpensActionsOnReleaseAndEditOpensTheEditor() = runDesktopComposeUiTest {
        val source = WebDav(url = "http://nas.local/dav", user = "", passwd = "", name = "Remote edit source")
        val viewModel = FakeSourceViewModel(source)
        val openedSources = mutableListOf<String>()
        val editLabel = getString(Res.string.edit)
        val deleteLabel = getString(Res.string.delete)

        setContent {
            val navController = rememberNavController()
            NavHost(navController, startDestination = "sources") {
                composable("sources") {
                    SourceListView(navController, viewModel = viewModel, remoteNavigation = true) {
                        openedSources += it.name
                    }
                }
                composable("${Screen.Config.route}/{type}?sourceName={sourceName}") {
                    Text("Source editor")
                }
            }
        }

        val card = onNode(
            hasClickAction() and hasAnyDescendant(hasContentDescription(source.name)),
            useUnmergedTree = true,
        )
        card.performSemanticsAction(SemanticsActions.RequestFocus)
        card.performKeyInput { keyDown(Key.DirectionCenter) }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        // The menu waits for the release, which must not open the source either.
        onNode(isDialog()).assertDoesNotExist()
        card.performKeyInput { keyUp(Key.DirectionCenter) }
        waitForIdle()

        onNode(isDialog()).assertExists()
        onNodeWithText(deleteLabel).assertIsDisplayed()
        // The inline action bar is not shown behind the menu.
        onNodeWithContentDescription(deleteLabel).assertDoesNotExist()
        onNodeWithText(editLabel).assertIsFocused()
        onNodeWithText(editLabel).performKeyInput { pressKey(Key.DirectionCenter) }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        onNodeWithText("Source editor").assertIsDisplayed()
        assertEquals(emptyList(), openedSources)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun remoteDeleteActionAsksForConfirmation() = runDesktopComposeUiTest {
        val source = WebDav(url = "http://nas.local/dav", user = "", passwd = "", name = "Remote delete source")
        val viewModel = FakeSourceViewModel(source)
        val deleteLabel = getString(Res.string.delete)

        setContent {
            SourceListView(rememberNavController(), viewModel = viewModel, remoteNavigation = true)
        }

        val card = onNode(
            hasClickAction() and hasAnyDescendant(hasContentDescription(source.name)),
            useUnmergedTree = true,
        )
        card.performSemanticsAction(SemanticsActions.RequestFocus)
        card.performKeyInput { keyDown(Key.DirectionCenter) }
        mainClock.advanceTimeBy(1_000)
        card.performKeyInput { keyUp(Key.DirectionCenter) }
        waitForIdle()

        val delete = onNodeWithText(deleteLabel)
        delete.performSemanticsAction(SemanticsActions.RequestFocus)
        delete.performKeyInput { pressKey(Key.DirectionCenter) }
        waitForIdle()

        // The action menu gave way to the confirmation.
        onAllNodes(isDialog()).assertCountEquals(1)
        onNodeWithText("confirm").assertIsDisplayed()
        onNodeWithText("cancel").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun remoteSelectStillOpensSourceAfterDismissingActions() = runDesktopComposeUiTest {
        val source = Local(name = "Remote reopen source")
        val viewModel = FakeSourceViewModel(source)
        val openedSources = mutableListOf<String>()
        val editLabel = getString(Res.string.edit)
        val deleteLabel = getString(Res.string.delete)

        setContent {
            SourceListView(rememberNavController(), viewModel = viewModel, remoteNavigation = true) {
                openedSources += it.name
            }
        }

        val card = onNode(
            hasClickAction() and hasAnyDescendant(hasContentDescription(source.name)),
            useUnmergedTree = true,
        )
        card.performSemanticsAction(SemanticsActions.RequestFocus)
        card.performKeyInput { keyDown(Key.DirectionCenter) }
        mainClock.advanceTimeBy(1_000)
        card.performKeyInput { keyUp(Key.DirectionCenter) }
        waitForIdle()

        // A local folder has nothing to edit.
        onNodeWithText(editLabel).assertDoesNotExist()
        onNode(isDialog()).performTouchInput { click(Offset(1f, 1f)) }
        waitForIdle()
        onNode(isDialog()).assertDoesNotExist()

        card.performSemanticsAction(SemanticsActions.RequestFocus)
        card.performKeyInput { pressKey(Key.DirectionCenter) }
        waitForIdle()

        assertEquals(listOf(source.name), openedSources)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun singleClickThenExitOpensSource() = runDesktopComposeUiTest {
        val source = Local(name = "Mouse exit test source")
        val viewModel = FakeSourceViewModel(source)
        val openedSources = mutableListOf<String>()

        setContent {
            SourceListView(
                navController = rememberNavController(),
                viewModel = viewModel,
                onClick = { openedSources += it.name },
            )
        }

        val card = onNode(
            hasClickAction() and hasAnyDescendant(hasContentDescription(source.name)),
            useUnmergedTree = true,
        )
        card.performMouseInput {
            moveTo(center)
            press()
            release()
            exit()
        }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        assertEquals(listOf(source.name), openedSources)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun singleClickWithSmallMouseMovementOpensSource() = runDesktopComposeUiTest {
        val source = Local(name = "Mouse movement test source")
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
        ).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(1f, 1f))
            release()
        }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        assertEquals(listOf(source.name), openedSources)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun singleTapOpensSourceWithoutWaitingForDoubleTap() = runDesktopComposeUiTest {
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

        waitForIdle()
        mainClock.autoAdvance = false
        onNode(
            hasClickAction() and hasAnyDescendant(hasContentDescription(source.name)),
            useUnmergedTree = true,
        ).performTouchInput { click() }
        mainClock.advanceTimeByFrame()

        runOnUiThread { assertEquals(listOf(source.name), openedSources) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun singleTapShowsAddDialogWithoutWaitingForDoubleTap() = runDesktopComposeUiTest {
        val viewModel = FakeSourceViewModel()
        val addLabel = getString(Res.string.addSource)

        setContent {
            SourceListView(
                navController = rememberNavController(),
                viewModel = viewModel,
                onClick = {},
            )
        }

        waitForIdle()
        mainClock.autoAdvance = false
        onNode(
            hasClickAction() and hasAnyDescendant(hasContentDescription(addLabel)),
            useUnmergedTree = true,
        ).performTouchInput { click() }
        mainClock.advanceTimeByFrame()

        onNode(isDialog()).assertExists()
    }
}

private class FakeSourceViewModel(vararg sources: RemoteApi) : SourceViewModel() {
    override val sourceListStateFlow: StateFlow<UiState<StorageSources>> = MutableStateFlow(
        UiState.Content(
            StorageSources(
                version = 1,
                versionName = "test",
                id = "test",
                sourceName = "test",
                timeStamp = 0L,
                sources = sources.toMutableList(),
            )
        )
    )
}
