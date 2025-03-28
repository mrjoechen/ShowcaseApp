package com.alpha.showcase.common.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.runtime.*
import com.alpha.showcase.common.ui.view.SwitchItem
import com.alpha.showcase.common.ui.view.TextTitleMedium
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.auto_open_latest_source
import showcaseapp.composeapp.generated.resources.auto_play_full_screen
import showcaseapp.composeapp.generated.resources.contain_video
import showcaseapp.composeapp.generated.resources.recursive_source_folder
import showcaseapp.composeapp.generated.resources.source_preference


/**
 *  - Source Preference
 *      - Sort (Random, Name Asc, Name Desc， Asc Date, Desc Date)
 *      - Recursive Source folder
 *      - Auto open latest source
 *      - Auto Refresh
 *
 */
@Composable
fun SourcePreferenceView(
    settings: Settings = Settings.getDefaultInstance(),
    onSet: (String, Any) -> Unit
) {

    Column {

        TextTitleMedium(text = stringResource(Res.string.source_preference))

        SwitchItem(
            Icons.Outlined.AccountTree,
            check = settings.recursiveDirContent,
            desc = stringResource(Res.string.recursive_source_folder),
            onCheck = {
                onSet(SourcePreferenceItem.RecursiveDir, it)
            })

//        SwitchItem(
//            Icons.Outlined.Refresh,
//            check = settings.autoRefresh,
//            desc = stringResource(Res.string.auto_refresh_source_content),
//            onCheck = {
//                onSet(SourcePreferenceItem.AutoRefresh, it)
//            })
//
//        SwitchItem(
//            Icons.Outlined.VideoFile,
//            check = settings.supportVideo,
//            desc = stringResource(Res.string.contain_video),
//            onCheck = {
//                onSet(SourcePreferenceItem.SupportVideo, it)
//            })

        SwitchItem(
            Icons.Outlined.AutoMode,
            check = settings.autoOpenLatestSource,
            desc = stringResource(Res.string.auto_open_latest_source),
            onCheck = {
                onSet(SourcePreferenceItem.AutoOpenLatestSource, it)
            })

        SwitchItem(
            Icons.Outlined.Fullscreen,
            check = settings.autoFullScreen,
            desc = stringResource(Res.string.auto_play_full_screen),
            onCheck = {
                onSet(SourcePreferenceItem.AutoFullScreen, it)
            })

    }

}

sealed class SourcePreferenceItem {

    companion object {
        const val RecursiveDir: String = "RecursiveDir"
        const val AutoOpenLatestSource: String = "AutoOpenLatestSource"
        const val SupportVideo: String = "SupportVideo"
        const val AutoRefresh: String = "AutoRefresh"
        const val AutoFullScreen: String = "AutoFullScreen"
    }
}