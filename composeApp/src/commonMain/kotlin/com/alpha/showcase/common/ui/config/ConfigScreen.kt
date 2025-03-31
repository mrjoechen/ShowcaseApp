package com.alpha.showcase.common.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.networkfile.storage.TYPE_DROPBOX
import com.alpha.showcase.common.networkfile.storage.TYPE_FTP
import com.alpha.showcase.common.networkfile.storage.TYPE_GOOGLE_DRIVE
import com.alpha.showcase.common.networkfile.storage.TYPE_GOOGLE_PHOTOS
import com.alpha.showcase.common.networkfile.storage.TYPE_ONE_DRIVE
import com.alpha.showcase.common.networkfile.storage.TYPE_SFTP
import com.alpha.showcase.common.networkfile.storage.TYPE_SMB
import com.alpha.showcase.common.networkfile.storage.TYPE_UNKNOWN
import com.alpha.showcase.common.networkfile.storage.TYPE_WEBDAV
import com.alpha.showcase.common.networkfile.storage.drive.DropBox
import com.alpha.showcase.common.networkfile.storage.drive.GoogleDrive
import com.alpha.showcase.common.networkfile.storage.drive.GooglePhotos
import com.alpha.showcase.common.networkfile.storage.drive.OneDrive
import com.alpha.showcase.common.networkfile.storage.remote.GitHubSource
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.storage.remote.TMDBSource
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_GITHUB
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_PEXELS
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_TMDB
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_UNSPLASH
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.networkfile.storage.getType
import com.alpha.showcase.common.networkfile.storage.remote.Ftp
import com.alpha.showcase.common.networkfile.storage.remote.GiteeSource
import com.alpha.showcase.common.networkfile.storage.remote.OAuthRcloneApi
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.Sftp
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_GITEE
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import showcaseapp.composeapp.generated.resources.Res
import com.alpha.showcase.common.ui.source.SourceViewModel
import com.alpha.showcase.common.ui.view.TextTitleLarge
import com.alpha.showcase.common.utils.ToastUtil
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import showcaseapp.composeapp.generated.resources.add
import showcaseapp.composeapp.generated.resources.add_success
import showcaseapp.composeapp.generated.resources.connection_failed
import showcaseapp.composeapp.generated.resources.connection_successful
import showcaseapp.composeapp.generated.resources.connection_tiemout
import showcaseapp.composeapp.generated.resources.edit
import showcaseapp.composeapp.generated.resources.save_success
import showcaseapp.composeapp.generated.resources.source
import showcaseapp.composeapp.generated.resources.source_name_already_exists
import showcaseapp.composeapp.generated.resources.unsupport_type

@Composable
fun ConfigScreen(type: Int, editSource: RemoteApi? = null, onSave: (() -> Unit)? = null) {
    ConfigScreenTitle(type = type, editMode = editSource != null) {
        ConfigContent(type, editSource, onSave)
    }
}

@Composable
fun ConfigScreenTitle(
    type: Int,
    editMode: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val title = "${if (editMode) stringResource(Res.string.edit) else stringResource(Res.string.add)} ${getType(type).typeName} ${stringResource(Res.string.source)}"

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextTitleLarge(text = title)
        }

        content()
    }
}

@Preview
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

    val onLinkClick: suspend (OAuthRcloneApi) -> OAuthRcloneApi? = { remoteApi ->
        if (viewModel.checkDuplicateName(remoteApi.name)) {

            try {
                withTimeout(60000) {
                    val linkOAuthResult = viewModel.linkOAuth(remoteApi) {
                        try {
                            it?.apply {
                                uriHandler.openUri(it)
                            } ?: {
                                ToastUtil.error(Res.string.connection_tiemout)
                            }
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                            // todo gen qrcode and show
                        }
                    }
                    if (linkOAuthResult != null) {
                        ToastUtil.success(Res.string.connection_successful)
                        linkOAuthResult
                    } else {
                        ToastUtil.error(Res.string.connection_failed)
                        null
                    }
                }

            } catch (timeout: TimeoutCancellationException) {
                ToastUtil.error(Res.string.connection_tiemout)
                null
            }
        } else {
            ToastUtil.error(Res.string.source_name_already_exists)
            null
        }
    }

    val onSelectPath: suspend (RcloneRemoteApi, String) -> Result<Any>? =
        { rcloneRemote, path ->
            viewModel.configSource(rcloneRemote)
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
                onSaveClick = onSaveClick,
                onSelectPath = onSelectPath
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

        TYPE_GOOGLE_DRIVE -> {
            GDriveConfigPage(
                googleDrive = editRemote as GoogleDrive?,
                onTestClick = onTestClick,
                onSaveClick = onSaveClick,
                onLinkClick = onLinkClick,
                onSelectPath = onSelectPath
            )
        }

        TYPE_GOOGLE_PHOTOS -> {
            GPhotosConfigPage(
                googlePhotos = editRemote as GooglePhotos?,
                onTestClick = onTestClick,
                onSaveClick = onSaveClick,
                onLinkClick = onLinkClick,
                onSelectPath = onSelectPath
            )
        }

        TYPE_ONE_DRIVE -> {
            OneDriveConfigPage(
                oneDrive = editRemote as OneDrive?,
                onTestClick = onTestClick,
                onSaveClick = onSaveClick,
                onLinkClick = onLinkClick,
                onSelectPath = onSelectPath
            )
        }

        TYPE_DROPBOX -> {
            DropboxConfigPage(
                dropbox = editRemote as DropBox?,
                onTestClick = onTestClick,
                onSaveClick = onSaveClick,
                onLinkClick = onLinkClick,
                onSelectPath = onSelectPath
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


        else -> {

            LaunchedEffect(Unit){
                ToastUtil.error(Res.string.unsupport_type)
                // todo
            }

        }

    }
}