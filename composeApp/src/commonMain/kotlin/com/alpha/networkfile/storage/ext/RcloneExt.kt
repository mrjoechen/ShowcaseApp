package com.alpha.networkfile.storage.ext

import com.alpha.networkfile.storage.FTP
import com.alpha.networkfile.storage.SFTP
import com.alpha.networkfile.storage.SMB
import com.alpha.networkfile.storage.UNKNOWN
import com.alpha.networkfile.storage.WEBDAV
import com.alpha.networkfile.storage.drive.GoogleDrive
import com.alpha.networkfile.storage.drive.GooglePhotos
import com.alpha.networkfile.storage.drive.OneDrive
import com.alpha.networkfile.storage.remote.Ftp
import com.alpha.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.networkfile.storage.remote.RemoteStorage
import com.alpha.networkfile.storage.remote.RemoteStorageImpl
import com.alpha.networkfile.storage.remote.Sftp
import com.alpha.networkfile.storage.remote.Smb
import com.alpha.networkfile.storage.remote.WebDav
import com.alpha.showcase.api.rclone.GoogleDriveConfig
import com.alpha.showcase.api.rclone.GooglePhotoConfig
import com.alpha.showcase.api.rclone.Remote
import com.alpha.showcase.api.rclone.RemoteConfig

/**
 * Created by chenqiao on 2022/11/29.
 * e-mail : mrjctech@gmail.com
 */

fun RcloneRemoteApi.toRemote(): Remote {

    return when (this) {
        is Smb -> {
            Remote(name, RemoteConfig(host, passwd, "$port", SMB.typeName, user = user))
        }

        is Ftp -> {
            Remote(name, RemoteConfig(host, passwd, "$port", FTP.typeName, user = user))
        }

        is Sftp -> {
            Remote(name, RemoteConfig(host, passwd, "$port", SFTP.typeName, user = user))
        }

        is WebDav -> {
//      val url = if(!host.contains("http")) "http://$host:$port" else "$host:$port"
            Remote(
                name,
                RemoteConfig(
                    host,
                    passwd,
                    "$port",
                    WEBDAV.typeName,
                    url = url,
                    user = user,
                    "other"
                )
            )
        }

        is GoogleDrive -> {
            Remote(
                name,
                GoogleDriveConfig(
                    client_id = cid,
                    client_secret = sid,
                    scope = scope,
                    token = token,
                    root_folder_id = folderId
                )
            )
        }

        is GooglePhotos -> {
            Remote(name, GooglePhotoConfig(client_id = cid, client_secret = sid, token = token))
        }

        is OneDrive -> {
            Remote(
                name,
                RemoteConfig(
                    client_id = cid,
                    client_secret = sid,
                    token = token,
                    drive_id = driveId,
                    drive_type = driveType
                )
            )
        }

        else -> {
            Remote(name, RemoteConfig())
        }
    }
}

fun Remote.buildRemoteStorage(path: String = ""): RemoteStorage {

  val remoteStorage = when(remoteConfig.type.uppercase()) {

    SMB.typeName -> Smb(
      host = remoteConfig.host,
      port = if (remoteConfig.port.toInt() == 0) SMB.defaultPort else remoteConfig.port.toInt(),
      user = remoteConfig.user,
      passwd = remoteConfig.pass,
      name = key,
      path = path
    )
    FTP.typeName -> Ftp(
      host = remoteConfig.host,
      port = if (remoteConfig.port.toInt() == 0) FTP.defaultPort else remoteConfig.port.toInt(),
      user = remoteConfig.user,
      passwd = remoteConfig.pass,
      name = key,
      path = path
    )
    SFTP.typeName -> Sftp(
      host = remoteConfig.host,
      port = if (remoteConfig.port.toInt() == 0) SFTP.defaultPort else remoteConfig.port.toInt(),
      user = remoteConfig.user,
      passwd = remoteConfig.pass,
      name = key,
      path = path
    )

    WEBDAV.typeName -> {
      WebDav(
        url = remoteConfig.url,
        user = remoteConfig.user,
        passwd = remoteConfig.pass,
        name = key,
        path = path
      )
    }
    else -> RemoteStorageImpl(
      UNKNOWN.typeName,
      host = remoteConfig.host,
      port = remoteConfig.port.toInt(),
      user = remoteConfig.user,
      passwd = remoteConfig.pass,
      name = key
    )
  }

  return remoteStorage
}