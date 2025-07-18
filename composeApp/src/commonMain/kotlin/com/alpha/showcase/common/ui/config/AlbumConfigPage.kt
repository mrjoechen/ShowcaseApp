package com.alpha.showcase.common.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.networkfile.storage.remote.AlbumSource
import com.alpha.showcase.common.repo.extractPlayListTypeAndId
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.ui.view.LargeDropdownMenu
import com.alpha.showcase.common.ui.view.TextWithHyperlink
import com.alpha.showcase.common.utils.decodeName
import com.alpha.showcase.common.utils.encodeName
import io.ktor.http.Url
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.choose_type
import showcaseapp.composeapp.generated.resources.confirm
import showcaseapp.composeapp.generated.resources.help
import showcaseapp.composeapp.generated.resources.music_album_link
import showcaseapp.composeapp.generated.resources.name_require_hint
import showcaseapp.composeapp.generated.resources.platforms_supported
import showcaseapp.composeapp.generated.resources.save
import showcaseapp.composeapp.generated.resources.test_connection
import showcaseapp.composeapp.generated.resources.tips_music_play_list_need_to_pubic

@Composable
fun AlbumConfigPage(
    albumSource: AlbumSource? = null,
    onTestClick: suspend (AlbumSource) -> Result<Any>?,
    onSaveClick: suspend (AlbumSource) -> Unit
) {
    var name by rememberSaveable(key = "name") {
        mutableStateOf(albumSource?.name?.decodeName() ?: "")
    }
    
    var playlistUrl by rememberSaveable(key = "playlistUrl") {
        mutableStateOf(albumSource?.playlistUrl ?: "")
    }
    
    var playlistUrlValid by rememberSaveable(key = "playlistUrlValid") {
        mutableStateOf(true)
    }
    
    var showHelpDialog by rememberSaveable(key = "showHelpDialog") {
        mutableStateOf(false)
    }
    
    val focusRequester = remember { FocusRequester() }
    val editMode = albumSource != null
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        var checkingState by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            value = name,
            onValueChange = {
                name = it
            },
            singleLine = true,
            label = { Text(stringResource(Res.string.name_require_hint)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
        )

        var selectedTypeIndex by rememberSaveable(key = "selectedTypeIndex") {
            mutableIntStateOf(
                musicPlatforms.find {
                    it.key == extractPlayListTypeAndId(albumSource?.playlistUrl?:"")?.first
                }?.let {
                    musicPlatforms.indexOf(it)
                } ?: 0
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        LargeDropdownMenu(
            label = stringResource(Res.string.choose_type),
            items = musicPlatforms.map { it.platformName },
            selectedIndex = selectedTypeIndex,
            onItemSelected = { index, _ ->
                selectedTypeIndex = index
                if (playlistUrl.isEmpty()
                    || playlistUrl == "https://y.qq.com/n/ryqq/playlist/"
                    || playlistUrl == "https://music.163.com/m/playlist?id="
                    || playlistUrl == "https://music.apple.com/cn/playlist/"){
                    when (musicPlatforms[index]) {
                        is MusicPlatform.QQ -> playlistUrl = "https://y.qq.com/n/ryqq/playlist/"
                        is MusicPlatform.Netease -> playlistUrl = "https://music.163.com/m/playlist?id="
                        is MusicPlatform.Apple -> playlistUrl = "https://music.apple.com/cn/playlist/"
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = playlistUrl,
            onValueChange = {
                playlistUrl = it
                val validPlaylistUrl = isValidPlaylistUrl(it)

                if (name.isEmpty()) {
                    if (it.isNotBlank() && validPlaylistUrl) {
                        name = extractPlaylistName(it)
                    }
                }

                if (validPlaylistUrl){
                    extractPlayListTypeAndId(it)?.let {
                        when (it.first) {
                            MusicPlatform.QQ.key -> selectedTypeIndex = 0
                            MusicPlatform.Netease .key -> selectedTypeIndex = 1
                            MusicPlatform.Apple.key -> selectedTypeIndex = 2
                            else -> {

                            }
                        }
                    }
                }
                playlistUrlValid = validPlaylistUrl
            },
            isError = !playlistUrlValid,
            label = { Text(stringResource(Res.string.music_album_link)) },
            trailingIcon = {
                IconButton(onClick = {
                    showHelpDialog = true
                }) {
                    Icon(Icons.Outlined.Info, contentDescription = stringResource(Res.string.help))
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            )
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedButton(
                onClick = {
                    if (!checkingState && playlistUrlValid && playlistUrl.isNotBlank()) {
                        scope.launch {
                            checkingState = true
                            onTestClick.invoke(
                                AlbumSource(
                                    name.encodeName(),
                                    playlistUrl
                                )
                            )
                            checkingState = false
                        }
                    }
                },
                modifier = Modifier.padding(10.dp)
            ) {
                if (checkingState) {
                    Box {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(5.dp)
                                .align(Alignment.Center)
                                .size(Dimen.spaceL),
                            strokeWidth = 2.dp
                        )
                    }
                }
                Text(text = stringResource(Res.string.test_connection))
            }
            
            ElevatedButton(
                onClick = {
                    scope.launch {
                        if (playlistUrlValid && playlistUrl.isNotBlank() && name.isNotBlank()) {
                            onSaveClick.invoke(
                                AlbumSource(
                                    name.encodeName(),
                                    playlistUrl
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.padding(10.dp)
            ) {
                Text(text = stringResource(Res.string.save), maxLines = 1)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
    
    if (showHelpDialog) {
        AlbumHelpDialog {
            showHelpDialog = false
        }
    }
    
    if (!editMode) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
fun AlbumHelpDialog(onDismiss: () -> Unit = {}) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.platforms_supported),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                Modifier
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                HorizontalDivider(color = Color.Gray.copy(0.3f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "• 网易云音乐 (music.163.com)")
//                Text(text = "  示例：https://music.163.com/m/playlist?id=13965019918")
                MaterialTheme {
                    TextWithHyperlink(
                        fullText = "  示例：https://music.163.com/m/playlist?id=13965019918",
                        linkText = "https://music.163.com/m/playlist?id=13965019918",
                        url = "https://music.163.com/m/playlist?id=13965019918"
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "• QQ 音乐 (y.qq.com)")
//                Text(text = "  示例：https://y.qq.com/n/ryqq/playlist/9533705141")
                MaterialTheme {
                    TextWithHyperlink(
                        fullText = "  示例：https://y.qq.com/n/ryqq/playlist/9533705141",
                        linkText = "https://y.qq.com/n/ryqq/playlist/9533705141",
                        url = "https://y.qq.com/n/ryqq/playlist/9533705141"
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "• Apple Music (music.apple.com)")
//                Text(text = "  示例：https://music.apple.com/cn/playlist/showcase/pl.u-kv9l2jlTJ0zm6e")
                MaterialTheme {
                    TextWithHyperlink(
                        fullText = "  示例：https://music.apple.com/cn/playlist/showcase/pl.u-kv9l2jlTJ0zm6e",
                        linkText = "https://music.apple.com/cn/playlist/showcase/pl.u-kv9l2jlTJ0zm6e",
                        url = "https://music.apple.com/cn/playlist/showcase/pl.u-kv9l2jlTJ0zm6e"
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(Res.string.tips_music_play_list_need_to_pubic))
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.Gray.copy(0.3f), thickness = 0.5.dp)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                }
            ) {
                Text(text = stringResource(Res.string.confirm))
            }
        }
    )
}

private fun isValidPlaylistUrl(url: String): Boolean {
    if (url.isBlank()) return false
    return try {
        extractPlayListTypeAndId(url) != null
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun extractPlaylistName(url: String): String {
    return try {
        val uri = Url(url)
        val host = uri.host
        
        when {
            host.contains("music.163.com") -> {
                val id = uri.parameters["id"]
                "网易云歌单_$id"
            }
            host.contains("y.qq.com") -> {
                val pathSegments = uri.rawSegments
                if (pathSegments.contains("playlist")) {
                    val index = pathSegments.indexOf("playlist")
                    if (index != -1 && index + 1 < pathSegments.size) {
                        val id = pathSegments[index + 1]
                        "QQ音乐_$id"
                    } else "QQ音乐歌单"
                } else {
                    val id = uri.parameters["id"]
                    "QQ音乐_$id"
                }
            }
            
            host.contains("music.apple.com") -> {
                val pathSegments = uri.rawSegments
                if (pathSegments.contains("playlist")) {
                    val index = pathSegments.indexOf("playlist")
                    if (index != -1 && index + 1 < pathSegments.size) {
                        val playlistName = pathSegments[index + 1]
                        "AppleMusic_$playlistName"
                    } else "AppleMusic歌单"
                } else {
                    val id = uri.parameters["id"]
                    "AppleMusic_$id"
                }
            }
            else -> "音乐歌单"
        }
    } catch (e: Exception) {
        e.printStackTrace()
        "音乐歌单"
    }
}


sealed class MusicPlatform(val key: String, val platformName: String) {
    data object Netease : MusicPlatform("netease", "网易云音乐")
    data object QQ : MusicPlatform("tencent", "QQ音乐")
    data object Apple : MusicPlatform("apple", "Apple Music")
}

val musicPlatforms = listOf(
    MusicPlatform.QQ,
    MusicPlatform.Netease,
    MusicPlatform.Apple
)