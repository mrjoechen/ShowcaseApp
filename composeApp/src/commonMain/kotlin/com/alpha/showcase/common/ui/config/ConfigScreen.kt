package com.alpha.showcase.common.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.alpha.networkfile.rclone.succeeded
import com.alpha.networkfile.storage.TYPE_DROPBOX
import com.alpha.networkfile.storage.TYPE_FTP
import com.alpha.networkfile.storage.TYPE_GOOGLE_DRIVE
import com.alpha.networkfile.storage.TYPE_GOOGLE_PHOTOS
import com.alpha.networkfile.storage.TYPE_ONE_DRIVE
import com.alpha.networkfile.storage.TYPE_SFTP
import com.alpha.networkfile.storage.TYPE_SMB
import com.alpha.networkfile.storage.TYPE_UNKNOWN
import com.alpha.networkfile.storage.TYPE_WEBDAV
import com.alpha.networkfile.storage.drive.DropBox
import com.alpha.networkfile.storage.drive.GoogleDrive
import com.alpha.networkfile.storage.drive.GooglePhotos
import com.alpha.networkfile.storage.drive.OneDrive
import com.alpha.networkfile.storage.external.GitHubSource
import com.alpha.networkfile.storage.external.TMDBSource
import com.alpha.networkfile.storage.external.TYPE_GITHUB
import com.alpha.networkfile.storage.external.TYPE_TMDB
import com.alpha.networkfile.storage.external.TYPE_UNSPLASH
import com.alpha.networkfile.storage.getType
import com.alpha.networkfile.storage.remote.Ftp
import com.alpha.networkfile.storage.remote.OAuthRcloneApi
import com.alpha.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.networkfile.storage.remote.RemoteApi
import com.alpha.networkfile.storage.remote.Sftp
import com.alpha.networkfile.storage.remote.Smb
import com.alpha.networkfile.storage.remote.WebDav
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
fun ConfigScreen(type: Int, editSource: RemoteApi<Any>? = null) {
    ConfigScreenTitle(type = type, editMode = editSource != null) {
        ConfigContent(type, editSource)
    }
}

@Composable
fun ConfigScreenTitle(
    type: Int,
    editMode: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val title = "${if (editMode) stringResource(Res.string.edit) else stringResource(Res.string.add)} ${getType(type).typeName} ${stringResource(Res.string.source)}"

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {

        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.ArrowBack,
                contentDescription = "back",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable {

                    }
                    .padding(10.dp))
            TextTitleLarge(text = title)
        }

        content()
    }
}

@Preview
@Composable
fun ConfigContent(
    type: Int = TYPE_UNKNOWN,
    editRemote: RemoteApi<Any>? = null,
    viewModel: SourceViewModel = SourceViewModel
) {
    val editMode = editRemote != null
    val onTestClick: suspend (RemoteApi<Any>) -> Result<Any> = { remoteApi ->
        if (viewModel.checkDuplicateName(remoteApi.name) || editMode) {
            val checkConnection = viewModel.checkConnection(remoteApi)
            if (checkConnection.succeeded) {
                ToastUtil.success(Res.string.connection_successful)
                Result.success((checkConnection as com.alpha.networkfile.rclone.Result.Success).data!!)

            } else {
                ToastUtil.error(Res.string.connection_failed)
                Result.failure(Exception(Res.string.connection_failed.key))
            }
        } else {
            ToastUtil.error(Res.string.source_name_already_exists)
            Result.failure(Exception(Res.string.source_name_already_exists.key))
        }
    }
    val onSaveClick: suspend (RemoteApi<Any>) -> Unit = { remoteApi ->
        val deleteResult = editRemote?.let {
            viewModel.deleteSource(it)
        } ?: true
        if (deleteResult && viewModel.checkDuplicateName(remoteApi.name)) {
            val addSourceList = viewModel.addSourceList(remoteApi)
            if (addSourceList) {
                ToastUtil.success(if (editRemote == null) Res.string.add_success else Res.string.save_success)
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
            val filesItemList = viewModel.getFilesItemList(rcloneRemote, path)
            if (filesItemList.succeeded) {
                Result.success((filesItemList as com.alpha.networkfile.rclone.Result.Success).data!!)
            } else {
                Result.failure(Exception(Res.string.connection_failed.key))
            }
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
            // todo
            ToastUtil.error(Res.string.unsupport_type)
        }

        else -> {
            ToastUtil.error(Res.string.unsupport_type)
            // todo
        }

    }
}