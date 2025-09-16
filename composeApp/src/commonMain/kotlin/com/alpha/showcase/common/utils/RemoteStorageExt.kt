package com.alpha.showcase.common.utils

import com.alpha.showcase.common.X
import com.alpha.showcase.common.networkfile.storage.TYPE_DROPBOX
import com.alpha.showcase.common.networkfile.storage.TYPE_FTP
import com.alpha.showcase.common.networkfile.storage.TYPE_GOOGLE_DRIVE
import com.alpha.showcase.common.networkfile.storage.TYPE_GOOGLE_PHOTOS
import com.alpha.showcase.common.networkfile.storage.TYPE_LOCAL
import com.alpha.showcase.common.networkfile.storage.TYPE_ONE_DRIVE
import com.alpha.showcase.common.networkfile.storage.TYPE_SFTP
import com.alpha.showcase.common.networkfile.storage.TYPE_SMB
import com.alpha.showcase.common.networkfile.storage.TYPE_UNKNOWN
import com.alpha.showcase.common.networkfile.storage.TYPE_WEBDAV
import com.alpha.showcase.common.networkfile.storage.drive.DropBox
import com.alpha.showcase.common.networkfile.storage.drive.GoogleDrive
import com.alpha.showcase.common.networkfile.storage.drive.GooglePhotos
import com.alpha.showcase.common.networkfile.storage.drive.OneDrive
import com.alpha.showcase.common.networkfile.storage.remote.AlbumSource
import com.alpha.showcase.common.networkfile.storage.remote.GitHubSource
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.storage.remote.TMDBSource
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_GITHUB
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_PEXELS
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_TMDB
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_UNSPLASH
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.networkfile.storage.remote.Ftp
import com.alpha.showcase.common.networkfile.storage.remote.GiteeSource
import com.alpha.showcase.common.networkfile.storage.remote.ImmichSource
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.networkfile.storage.remote.OAuthRcloneApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.Sftp
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_GITEE
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.networkfile.storage.remote.WeiboSource
import org.jetbrains.compose.resources.DrawableResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.*


fun RemoteApi.getIcon(): DrawableResource {
    return when (this) {
        is Local -> {
            Res.drawable.ic_folder
        }

        is Smb -> {
            Res.drawable.ic_smb
        }

        is Ftp -> {
            Res.drawable.ic_ftp
        }

        is WebDav -> {
            Res.drawable.ic_webdav
        }

        is Sftp -> {
            Res.drawable.ic_terminal
        }

        is GoogleDrive -> {
            Res.drawable.ic_google_drive
        }

        is GitHubSource -> {
            Res.drawable.ic_github
        }

        is TMDBSource -> {
            Res.drawable.ic_tmdb
        }

        is UnSplashSource -> {
            Res.drawable.ic_unsplash_symbol
        }

        is GooglePhotos -> {
            Res.drawable.ic_google_photos
        }

        is OneDrive -> {
            Res.drawable.ic_onedrive
        }

        is DropBox -> {
            Res.drawable.ic_dropbox
        }

        is PexelsSource -> {
            Res.drawable.ic_pexels
        }

        is GiteeSource -> {
            Res.drawable.ic_gitee
        }

        is ImmichSource -> {
            Res.drawable.ic_immich
        }

        is WeiboSource -> {
            Res.drawable.ic_weibo_image
        }

        is AlbumSource -> {
            Res.drawable.ic_music_album
        }

        else -> {
            Res.drawable.ic_info
        }
    }
}

fun OAuthRcloneApi.supplyConfig(): Map<String, String>{
    return when(this){
        is GoogleDrive -> {
            genRcloneConfig().toMutableMap().apply {
                this["client_id"] = X.GCP_CLIENT_ID
                this["client_secret"] = X.GCP_SECRET
            }
        }

        is DropBox -> {
            genRcloneConfig().toMutableMap().apply {
                this["client_id"] = X.DROPBOX_APP_KEY
                this["client_secret"] = X.DROPBOX_APP_SECRET
            }
        }

        is OneDrive -> {
            genRcloneConfig().toMutableMap().apply {
                this["client_id"] = X.ONEDRIVE_APP_KEY
                this["client_secret"] = X.ONEDRIVE_APP_SECRET
            }
        }

        is GooglePhotos -> {
            genRcloneConfig().toMutableMap().apply {
                this["client_id"] = X.GCP_CLIENT_ID
                this["client_secret"] = X.GCP_SECRET
            }
        }

        else -> {
            emptyMap()
        }
    }
}