package com.alpha.networkfile.storage

import com.alpha.networkfile.storage.drive.DropBox
import com.alpha.networkfile.storage.drive.GoogleDrive
import com.alpha.networkfile.storage.drive.GooglePhotos
import com.alpha.networkfile.storage.drive.OneDrive
import com.alpha.networkfile.storage.external.GITHUB
import com.alpha.networkfile.storage.external.GitHubSource
import com.alpha.networkfile.storage.external.TMDB
import com.alpha.networkfile.storage.external.TMDBSource
import com.alpha.networkfile.storage.external.TYPE_GITHUB
import com.alpha.networkfile.storage.external.TYPE_TMDB
import com.alpha.networkfile.storage.external.TYPE_UNSPLASH
import com.alpha.networkfile.storage.external.UNSPLASH
import com.alpha.networkfile.storage.external.UnSplashSource
import com.alpha.networkfile.storage.remote.Ftp
import com.alpha.networkfile.storage.remote.Local
import com.alpha.networkfile.storage.remote.RemoteApi
import com.alpha.networkfile.storage.remote.Sftp
import com.alpha.networkfile.storage.remote.Smb
import com.alpha.networkfile.storage.remote.Union
import com.alpha.networkfile.storage.remote.WebDav
import com.alpha.showcase.api.rclone.Remote

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

const val SMB_DEFAULT_PORT = 445
const val FTP_DEFAULT_PORT = 21
const val SFTP_DEFAULT_PORT = 22
const val WEBDAV_DEFAULT_PORT = 5005

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

object SMB : RemoteStorageNetworkFS("SMB", TYPE_SMB, SMB_DEFAULT_PORT)

object FTP : RemoteStorageNetworkFS("FTP", TYPE_FTP, FTP_DEFAULT_PORT)

object SFTP : RemoteStorageNetworkFS("SFTP", TYPE_SFTP, SFTP_DEFAULT_PORT)

object WEBDAV : RemoteStorageNetworkFS("WebDAV", TYPE_WEBDAV, WEBDAV_DEFAULT_PORT)

object GOOGLE_DRIVE : RemoteStorageNetworkDrive("Google Drive", TYPE_GOOGLE_DRIVE)

object ONE_DRIVE : RemoteStorageNetworkDrive("OneDrive", TYPE_ONE_DRIVE)

object DROP_BOX : RemoteStorageNetworkDrive("DropBox", TYPE_DROPBOX)

object GOOGLE_PHOTOS : RemoteStorageNetworkDrive("Google Photos", TYPE_GOOGLE_PHOTOS)


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
        else -> UNKNOWN
    }
}

fun RemoteApi<Any>.getType() {
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
        else -> TYPE_UNKNOWN
    }
}

fun Remote.isType(storageType: StorageType) = remoteConfig.type.uppercase() == storageType.typeName