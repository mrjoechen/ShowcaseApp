package com.alpha.showcase.common.networkfile.storage.ext

import com.alpha.showcase.api.rclone.GoogleDriveConfig
import com.alpha.showcase.api.rclone.GooglePhotoConfig
import com.alpha.showcase.api.rclone.Remote
import com.alpha.showcase.api.rclone.RemoteConfig
import com.alpha.showcase.common.networkfile.storage.FTP
import com.alpha.showcase.common.networkfile.storage.SFTP
import com.alpha.showcase.common.networkfile.storage.SMB
import com.alpha.showcase.common.networkfile.storage.UNKNOWN
import com.alpha.showcase.common.networkfile.storage.WEBDAV
import com.alpha.showcase.common.networkfile.storage.drive.GoogleDrive
import com.alpha.showcase.common.networkfile.storage.drive.GooglePhotos
import com.alpha.showcase.common.networkfile.storage.drive.OneDrive
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.*

/**
 * Created by chenqiao on 2022/11/29.
 * e-mail : mrjctech@gmail.com
 */

fun RcloneRemoteApi.toRemote(): Remote {

    return when (this) {
        is Smb -> {
            Remote(name, RemoteConfig(host, RConfig.decrypt(passwd), "$port", SMB.typeName, user = user))
        }

        is Ftp -> {
            Remote(name, RemoteConfig(host, RConfig.decrypt(passwd), "$port", FTP.typeName, user = user))
        }

        is Sftp -> {
            Remote(name, RemoteConfig(host, RConfig.decrypt(passwd), "$port", SFTP.typeName, user = user))
        }

        is WebDav -> {
//      val url = if(!host.contains("http")) "http://$host:$port" else "$host:$port"
            Remote(
                name,
                RemoteConfig(
                    host,
                    RConfig.decrypt(passwd),
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
                    token = RConfig.decrypt(token),
                    root_folder_id = folderId
                )
            )
        }

        is GooglePhotos -> {
            Remote(name, GooglePhotoConfig(client_id = cid, client_secret = sid, token = RConfig.decrypt(token)))
        }

        is OneDrive -> {
            Remote(
                name,
                RemoteConfig(
                    client_id = cid,
                    client_secret = sid,
                    token = RConfig.decrypt(token),
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
      passwd = RConfig.encrypt(remoteConfig.pass),
      name = key,
      path = path
    )
    FTP.typeName -> Ftp(
      host = remoteConfig.host,
      port = if (remoteConfig.port.toInt() == 0) FTP.defaultPort else remoteConfig.port.toInt(),
      user = remoteConfig.user,
      passwd = RConfig.encrypt(remoteConfig.pass),
      name = key,
      path = path
    )
    SFTP.typeName -> Sftp(
      host = remoteConfig.host,
      port = if (remoteConfig.port.toInt() == 0) SFTP.defaultPort else remoteConfig.port.toInt(),
      user = remoteConfig.user,
      passwd = RConfig.encrypt(remoteConfig.pass),
      name = key,
      path = path
    )

    WEBDAV.typeName -> {
      WebDav(
        url = remoteConfig.url,
        user = remoteConfig.user,
        passwd = RConfig.encrypt(remoteConfig.pass),
        name = key,
        path = path
      )
    }
    else -> RemoteStorageImpl(
      UNKNOWN.typeName,
      host = remoteConfig.host,
      port = remoteConfig.port.toInt(),
      user = remoteConfig.user,
      passwd = RConfig.encrypt(remoteConfig.pass),
      name = key, schema = "unknowm"
    )
  }

  return remoteStorage
}
