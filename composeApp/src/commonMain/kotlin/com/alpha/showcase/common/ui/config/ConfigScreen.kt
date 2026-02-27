package com.alpha.showcase.common.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.networkfile.storage.TYPE_FTP
import com.alpha.showcase.common.networkfile.storage.TYPE_SFTP
import com.alpha.showcase.common.networkfile.storage.TYPE_SMB
import com.alpha.showcase.common.networkfile.storage.TYPE_UNKNOWN
import com.alpha.showcase.common.networkfile.storage.TYPE_WEBDAV
import com.alpha.showcase.common.networkfile.storage.remote.GitHubSource
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.storage.remote.TMDBSource
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_GITHUB
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_PEXELS
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_TMDB
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_UNSPLASH
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.networkfile.storage.getType
import com.alpha.showcase.common.networkfile.storage.remote.AlbumSource
import com.alpha.showcase.common.networkfile.storage.remote.Ftp
import com.alpha.showcase.common.networkfile.storage.remote.GiteeSource
import com.alpha.showcase.common.networkfile.storage.remote.ImmichSource
import com.alpha.showcase.common.networkfile.storage.remote.OAuthRcloneApi
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.Sftp
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_ALBUM
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_GITEE
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_IMMICH
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import showcaseapp.composeapp.generated.resources.Res
import com.alpha.showcase.common.ui.source.SourceViewModel
import com.alpha.showcase.common.ui.view.TextTitleLarge
import com.alpha.showcase.common.utils.ToastUtil
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.add
import showcaseapp.composeapp.generated.resources.add_success
import showcaseapp.composeapp.generated.resources.connection_failed
import showcaseapp.composeapp.generated.resources.connection_successful
import showcaseapp.composeapp.generated.resources.edit
import showcaseapp.composeapp.generated.resources.save_success
import showcaseapp.composeapp.generated.resources.source
import showcaseapp.composeapp.generated.resources.source_name_already_exists
import showcaseapp.composeapp.generated.resources.unsupport_type

@Composable
fun ConfigScreen(type: Int, editSource: RemoteApi? = null, onSave: (() -> Unit)? = null) {
    ConfigScreenTitle(type = type, editMode = editSource != null, onBack = onSave) {
        ConfigContent(type, editSource, onSave)
    }
}

@Composable
fun ConfigScreenTitle(
    type: Int,
    editMode: Boolean = false,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val title = "${if (editMode) stringResource(Res.string.edit) else stringResource(Res.string.add)} ${getType(type).typeName} ${stringResource(Res.string.source)}"

    Surface(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 36.dp, 16.dp, 0.dp)
            ) {
                IconButton(
                    onClick = { onBack?.invoke() },
                    modifier = Modifier.align(Alignment.CenterStart).size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextTitleLarge(text = title)
                }
            }

            content()
        }
    }
}

@Composable
fun ConfigContent(
    type: Int = TYPE_UNKNOWN,
    editRemote: RemoteApi? = null,
    onSave: (() -> Unit)? = null,
    viewModel: SourceViewModel = SourceViewModel
) {
    val editMode = editRemote != null
    val onTestClick: suspend (RemoteApi) -> Result<Any> = { remoteApi ->
        if (viewModel.checkDuplicateName(remoteApi.name) || editMode) {
            val checkConnection = viewModel.checkConnection(remoteApi)
            if (checkConnection.isSuccess) {
                ToastUtil.success(Res.string.connection_successful)
                Result.success(checkConnection.getOrNull()!!)

            } else {
                ToastUtil.error(Res.string.connection_failed)
                Result.failure(Exception(Res.string.connection_failed.key))
            }
        } else {
            ToastUtil.error(Res.string.source_name_already_exists)
            Result.failure(Exception(Res.string.source_name_already_exists.key))
        }
    }
    val onSaveClick: suspend (RemoteApi) -> Unit = { remoteApi ->
        val deleteResult = editRemote?.let {
            viewModel.deleteSource(it)
        } ?: true
        if (deleteResult && viewModel.checkDuplicateName(remoteApi.name)) {
            val addSourceList = viewModel.addSourceList(remoteApi)
            if (addSourceList) {
                ToastUtil.success(if (editRemote == null) Res.string.add_success else Res.string.save_success)
                onSave?.invoke()
            }
        } else {
            ToastUtil.error(Res.string.source_name_already_exists)
        }
    }

    val uriHandler = LocalUriHandler.current


    val onSelectPath: suspend (RcloneRemoteApi, String) -> Result<Any>? =
        { rcloneRemote, path ->
            viewModel.getFilesItemList(rcloneRemote, path)
        }

    when (type) {
        TYPE_SMB -> {
            SmbConfigPage(
                editRemote as Smb?,
                onTestClick = onTestClick,
                onSaveClick = onSaveClick,
                onSelectPath = onSelectPath
            )
        }

        TYPE_FTP -> {
            FtpConfigPage(
                editRemote as Ftp?,
                onTestClick = onTestClick,
                onSaveClick = onSaveClick,
                onSelectPath = onSelectPath
            )
        }

        TYPE_SFTP -> {
            SftpConfigPage(
                editRemote as Sftp?,
                onTestClick = onTestClick,
                onSaveClick = onSaveClick,
                onSelectPath = onSelectPath
            )
        }

        TYPE_WEBDAV -> {
            WebdavConfigPage(
                editRemote as WebDav?,
                onTestClick = onTestClick,
                onSaveClick = onSaveClick
            )
        }

        TYPE_GITHUB -> {
            GithubConfigPage(
                editRemote as GitHubSource?,
                onTestClick = onTestClick,
                onSaveClick = onSaveClick
            )
        }

        TYPE_TMDB -> {
            TMDBConfigPage(
                editRemote as TMDBSource?,
                onTestClick = onTestClick,
                onSaveClick = onSaveClick
            )
        }


        TYPE_UNSPLASH -> {
            UnsplashConfigPage(
                unsplashSource = editRemote as UnSplashSource?,
                onTestClick = onTestClick,
                onSaveClick = onSaveClick
            )
        }

        TYPE_PEXELS ->{
            PexelsConfigPage(
                pexelsSource = editRemote as PexelsSource?,
                onTestClick = onTestClick,
                onSaveClick = onSaveClick
            )
        }

        TYPE_GITEE -> {
            GiteeConfigPage(
                giteeSource = editRemote as GiteeSource?,
                onTestClick = onTestClick,
                onSaveClick = onSaveClick,
            )
        }

        TYPE_IMMICH -> {
            ImmichConfigPage(
                immichSource = editRemote as ImmichSource?,
                onTestClick = onTestClick,
                onSaveClick = onSaveClick,
            )
        }

        TYPE_ALBUM -> {
            AlbumConfigPage(
                albumSource = editRemote as AlbumSource?,
                onTestClick = onTestClick,
                onSaveClick = onSaveClick
            )
        }

        else -> {

            LaunchedEffect(Unit){
                ToastUtil.error(Res.string.unsupport_type)
                // todo
            }

        }

    }
}