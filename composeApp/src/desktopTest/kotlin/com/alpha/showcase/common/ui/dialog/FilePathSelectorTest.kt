package com.alpha.showcase.common.ui.dialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class FilePathSelectorTest {

    private fun unreachableWebDav() = WebDav(
        url = "http://127.0.0.1:1",
        user = "",
        passwd = "",
        name = "test",
    )

    @Test
    fun successfulTestOpensFolderPickerEvenWhenFileApiChangesInSameFrame() = runDesktopComposeUiTest {
        var fileApi by mutableStateOf<RcloneRemoteApi?>(null)
        var signal by mutableStateOf(0)
        setContent {
            MaterialTheme {
                Surface {
                    FilePathSelector(
                        fileApi = fileApi,
                        path = "",
                        onPathChange = {},
                        openDialogSignal = signal,
                    )
                }
            }
        }
        onAllNodesWithText("Choose Folder").assertCountEquals(0)

        // Mirrors the config pages: a successful test sets the api and bumps the signal together.
        runOnIdle {
            fileApi = unreachableWebDav()
            signal++
        }
        waitForIdle()

        onAllNodesWithText("Choose Folder").assertCountEquals(1)
    }

    @Test
    fun signalIsIgnoredWithoutFileApi() = runDesktopComposeUiTest {
        var signal by mutableStateOf(0)
        setContent {
            MaterialTheme {
                Surface {
                    FilePathSelector(fileApi = null, path = "", onPathChange = {}, openDialogSignal = signal)
                }
            }
        }

        runOnIdle { signal++ }
        waitForIdle()

        onAllNodesWithText("Choose Folder").assertCountEquals(0)
    }
}
