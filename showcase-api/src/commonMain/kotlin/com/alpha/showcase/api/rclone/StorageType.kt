package com.alpha.showcase.api.rclone

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

data object SMB : RemoteStorageNetworkFS("SMB", TYPE_SMB, SMB_DEFAULT_PORT)

data object FTP : RemoteStorageNetworkFS("FTP", TYPE_FTP, FTP_DEFAULT_PORT)

data object SFTP : RemoteStorageNetworkFS("SFTP", TYPE_SFTP, SFTP_DEFAULT_PORT)

data object WEBDAV : RemoteStorageNetworkFS("WebDAV", TYPE_WEBDAV, WEBDAV_DEFAULT_PORT)

data object GOOGLE_DRIVE : RemoteStorageNetworkDrive("Google Drive", TYPE_GOOGLE_DRIVE)

data object ONE_DRIVE : RemoteStorageNetworkDrive("OneDrive", TYPE_ONE_DRIVE)

data object DROP_BOX : RemoteStorageNetworkDrive("DropBox", TYPE_DROPBOX)

data object GOOGLE_PHOTOS : RemoteStorageNetworkDrive("Google Photos", TYPE_GOOGLE_PHOTOS)
