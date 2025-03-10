package com.alpha.showcase.common.networkfile.storage

import PLATFORM_TYPE
import com.alpha.showcase.common.networkfile.storage.drive.DropBox
import com.alpha.showcase.common.networkfile.storage.drive.GoogleDrive
import com.alpha.showcase.common.networkfile.storage.drive.GooglePhotos
import com.alpha.showcase.common.networkfile.storage.drive.OneDrive
import com.alpha.showcase.common.networkfile.storage.remote.GITHUB
import com.alpha.showcase.common.networkfile.storage.remote.GitHubSource
import com.alpha.showcase.common.networkfile.storage.remote.TMDB
import com.alpha.showcase.common.networkfile.storage.remote.TMDBSource
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_GITHUB
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_TMDB
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_UNSPLASH
import com.alpha.showcase.common.networkfile.storage.remote.UNSPLASH
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.networkfile.storage.remote.Ftp
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.Sftp
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.storage.remote.Union
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.api.rclone.Remote
import com.alpha.showcase.common.networkfile.storage.remote.PEXELS
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_PEXELS
import getPlatform
import org.jetbrains.compose.resources.DrawableResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.ic_alist
import showcaseapp.composeapp.generated.resources.ic_dropbox
import showcaseapp.composeapp.generated.resources.ic_ftp
import showcaseapp.composeapp.generated.resources.ic_github
import showcaseapp.composeapp.generated.resources.ic_google_drive
import showcaseapp.composeapp.generated.resources.ic_google_photos
import showcaseapp.composeapp.generated.resources.ic_local
import showcaseapp.composeapp.generated.resources.ic_onedrive
import showcaseapp.composeapp.generated.resources.ic_pexels
import showcaseapp.composeapp.generated.resources.ic_smb
import showcaseapp.composeapp.generated.resources.ic_terminal
import showcaseapp.composeapp.generated.resources.ic_tmdb
import showcaseapp.composeapp.generated.resources.ic_union
import showcaseapp.composeapp.generated.resources.ic_unsplash_symbol
import showcaseapp.composeapp.generated.resources.ic_webdav

const val TYPE_UNKNOWN = -1
const val TYPE_LOCAL = 0
const val TYPE_SMB = 1
const val TYPE_FTP = 2
const val TYPE_SFTP = 3
const val TYPE_WEBDAV = 4
const val TYPE_GOOGLE_DRIVE = 5
const val TYPE_ONE_DRIVE = 6
const val TYPE_DROPBOX = 7
const val TYPE_GOOGLE_PHOTOS = 8
const val TYPE_UNION = 9
const val TYPE_ALIST = 10

const val SMB_DEFAULT_PORT = 445
const val FTP_DEFAULT_PORT = 21
const val SFTP_DEFAULT_PORT = 22
const val WEBDAV_DEFAULT_PORT = 5005
const val ALIST_DEFAULT_PORT = 5244


open class StorageType(val typeName: String = "UNKNOWN", val type: Int = TYPE_UNKNOWN)

sealed class RemoteStorageType(typeName: String = "UNKNOWN", type: Int) :
    StorageType(typeName, type)

sealed class RemoteStorageNetworkFS(
    typeName: String = "UNKNOWN_NETWORKFS",
    type: Int = TYPE_UNKNOWN,
    val defaultPort: Int
) : RemoteStorageType(typeName, type)

sealed class RemoteStorageNetworkDrive(
    typeName: String = "UNKNOWN_NETWORKDRIVE",
    type: Int = TYPE_UNKNOWN
) : RemoteStorageType(typeName, type)

object UNKNOWN : StorageType()

object LOCAL : StorageType("Local", TYPE_LOCAL)

object UNION : StorageType("Union", TYPE_UNION)

data object SMB : RemoteStorageNetworkFS("SMB", TYPE_SMB, SMB_DEFAULT_PORT)

data object FTP : RemoteStorageNetworkFS("FTP", TYPE_FTP, FTP_DEFAULT_PORT)

data object SFTP : RemoteStorageNetworkFS("SFTP", TYPE_SFTP, SFTP_DEFAULT_PORT)

data object WEBDAV : RemoteStorageNetworkFS("WebDAV", TYPE_WEBDAV, WEBDAV_DEFAULT_PORT)

data object ALIST : RemoteStorageNetworkFS("Alist", TYPE_ALIST, ALIST_DEFAULT_PORT)

data object GOOGLE_DRIVE : RemoteStorageNetworkDrive("Google Drive", TYPE_GOOGLE_DRIVE)

data object ONE_DRIVE : RemoteStorageNetworkDrive("OneDrive", TYPE_ONE_DRIVE)

data object DROP_BOX : RemoteStorageNetworkDrive("DropBox", TYPE_DROPBOX)

data object GOOGLE_PHOTOS : RemoteStorageNetworkDrive("Google Photos", TYPE_GOOGLE_PHOTOS)


fun getType(type: Int): StorageType {
    return when (type) {
        TYPE_LOCAL -> LOCAL
        TYPE_SMB -> SMB
        TYPE_FTP -> FTP
        TYPE_SFTP -> SFTP
        TYPE_WEBDAV -> WEBDAV
        TYPE_GOOGLE_DRIVE -> GOOGLE_DRIVE
        TYPE_ONE_DRIVE -> ONE_DRIVE
        TYPE_DROPBOX -> DROP_BOX
        TYPE_TMDB -> TMDB
        TYPE_GITHUB -> GITHUB
        TYPE_GOOGLE_PHOTOS -> GOOGLE_PHOTOS
        TYPE_UNSPLASH -> UNSPLASH
        TYPE_UNION -> UNION
        TYPE_PEXELS -> PEXELS
        TYPE_ALIST -> ALIST
        else -> UNKNOWN
    }
}

fun RemoteApi.getType() {
    when (this) {
        is Ftp -> TYPE_FTP
        is Smb -> TYPE_SMB
        is WebDav -> TYPE_WEBDAV
        is Sftp -> TYPE_SFTP
        is GitHubSource -> TYPE_GITHUB
        is TMDBSource -> TYPE_TMDB
        is GooglePhotos -> TYPE_GOOGLE_PHOTOS
        is GoogleDrive -> TYPE_GOOGLE_DRIVE
        is OneDrive -> TYPE_ONE_DRIVE
        is DropBox -> TYPE_DROPBOX
        is Local -> TYPE_LOCAL
        is UnSplashSource -> TYPE_UNSPLASH
        is Union -> TYPE_UNION
        is PexelsSource -> TYPE_PEXELS
        else -> TYPE_UNKNOWN
    }
}


val SUPPORT_LIST = listOf(
    LOCAL to Res.drawable.ic_local,
    SMB to Res.drawable.ic_smb,
    FTP to Res.drawable.ic_ftp,
    SFTP to Res.drawable.ic_terminal,
    WEBDAV to Res.drawable.ic_webdav,
    TMDB to Res.drawable.ic_tmdb,
    GITHUB to Res.drawable.ic_github,
    GOOGLE_PHOTOS to Res.drawable.ic_google_photos,
    GOOGLE_DRIVE to Res.drawable.ic_google_drive,
    ONE_DRIVE to Res.drawable.ic_onedrive,
    DROP_BOX to Res.drawable.ic_dropbox,
    UNION to Res.drawable.ic_union,
    UNSPLASH to Res.drawable.ic_unsplash_symbol,
    PEXELS to Res.drawable.ic_pexels,
    ALIST to Res.drawable.ic_alist
)

val COLOR_ICON_STORAGE = listOf(
    Res.drawable.ic_google_drive,
    Res.drawable.ic_onedrive,
    Res.drawable.ic_google_photos,
    Res.drawable.ic_dropbox,
    Res.drawable.ic_tmdb,
    Res.drawable.ic_alist
)

fun Remote.isType(storageType: StorageType) = remoteConfig.type.uppercase() == storageType.typeName

fun getCurrentPlatformSupportTypes(): List<Pair<StorageType, DrawableResource>> {
    return when (getPlatform().platform) {
        PLATFORM_TYPE.Android -> SUPPORT_LIST
        PLATFORM_TYPE.Ios -> SUPPORT_LIST.filter { it.first in listOf(WEBDAV, TMDB, GITHUB, UNSPLASH, PEXELS, ALIST) }
        PLATFORM_TYPE.Web, PLATFORM_TYPE.WebWasm, PLATFORM_TYPE.WebJS -> SUPPORT_LIST.filter { it.first in listOf(WEBDAV, TMDB, GITHUB, UNSPLASH, PEXELS, ALIST) }
        PLATFORM_TYPE.Desktop, PLATFORM_TYPE.Windows -> SUPPORT_LIST
        else -> emptyList()
    }
}