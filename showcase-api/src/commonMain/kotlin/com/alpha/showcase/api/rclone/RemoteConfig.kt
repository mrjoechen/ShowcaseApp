package com.alpha.showcase.api.rclone

import kotlinx.serialization.Serializable

/**
 *
 * {
 *   "catsmb": {
 *     "host": "home.chenqiao.tech",
 *     "pass": "a2NZNS5v1WXsPmN",
 *     "port": "445",
 *     "type": "smb",
 *     "user": "user"
 *   },
 *   "catwebdav": {
 *   "pass": "14SruUYckhWwyuIRIYJg",
 *   "type": "webdav",
 *   "url": "http://home.chenqiao.tech:5905",
 *   "user": "user"
 *   },
 *   "eeo_mac": {
 *   "host": "10.254.34.174",
 *   "pass": "Q9074iVNIN616V",
 *   "port": "21",
 *   "type": "ftp",
 *   "user": "user"
 *   }
 * }
 *
 * 对应 Rclone ini config
 *
 * [catsmb]
 * type = smb
 * host = home.chenqiao.tech
 * user = user
 * port = 9445
 * pass = abcabc
 *
 *
 * [catwebdav]
 * type = webdav
 * url = http://home.chenqiao.tech:5905
 * vendor = other
 * user = user
 * pass = abc
 *
 * [google]
 * type = drive
 * client_id = aaa
 * client_secret = aaa
 * scope = drive.readonly
 * token = {"access_token":"aa","token_type":"Bearer","refresh_token":"aa","expiry":"2023-08-04T20:55:29.169061+08:00"}
 * team_drive =
 *
 *
 * [photo]
 * type = google photos
 * client_id = abc
 * client_secret = abc
 * token = {"access_token":"aaaa","token_type":"Bearer","refresh_token":"bbb","expiry":"2023-08-04T15:05:46.617928+08:00"}
 *
 * [onedrive]
 * type = onedrive
 * client_id = abc
 * client_secret = abc
 * token = {"access_token":"aa","token_type":"Bearer","refresh_token":"bb","expiry":"2023-07-18T11:12:17.149846+08:00"}
 * drive_id = 5dc11a5d51f7bfc9
 * drive_type = personal
 *
 *
 * Created by chenqiao on 2022/11/29.
 * e-mail : mrjctech@gmail.com
 */
@Serializable
open class RemoteConfig(
    // SMB, FTP
    val host: String = "",
    val pass: String = "",
    val port: String = "0",
    val type: String = "",
    val url: String = "",
    val user: String = "",
    //webdav
    val vendor: String = "",
    // Google Drive, OneDrive
    val client_id: String = "",
    val client_secret: String = "",
    val scope: String = "",
    val token: String = "",
    val root_folder_id: String = "",
    // Google photos
    val read_only: String = "true",
    // OneDrive
    val drive_id: String = "",
    val drive_type: String = ""
)


class GoogleDriveConfig(client_id: String, client_secret: String, scope: String, token: String, root_folder_id: String) :
    RemoteConfig(client_id = client_id, client_secret = client_secret, scope = scope, token = token, root_folder_id = root_folder_id)

class GooglePhotoConfig(client_id: String, client_secret: String, token: String, read_only: String = "true") :
    RemoteConfig(client_id = client_id, client_secret = client_secret, token = token, read_only = read_only)

class OneDriveConfig(
    client_id: String,
    client_secret: String,
    token: String,
    drive_id: String,
    drive_type: String
) : RemoteConfig(
    client_id = client_id,
    client_secret = client_secret,
    token = token,
    drive_id = drive_id,
    drive_type = drive_type
)

class DropboxConfig(client_id: String, client_secret: String, token: String) :
    RemoteConfig(client_id = client_id, client_secret = client_secret, token = token)

fun RemoteConfig.toGoogleDriveConfig() = GoogleDriveConfig(
    client_id = client_id,
    client_secret = client_secret,
    scope = scope,
    token = token,
    root_folder_id = root_folder_id,
)

fun RemoteConfig.toGooglePhotoConfig() = GooglePhotoConfig(
    client_id = client_id,
    client_secret = client_secret,
    token = token
)

fun RemoteConfig.toOneDriveConfig() = OneDriveConfig(
    client_id = client_id,
    client_secret = client_secret,
    token = token,
    drive_id = drive_id,
    drive_type = drive_type
)

fun RemoteConfig.toDropboxConfig() = DropboxConfig(
    client_id = client_id,
    client_secret = client_secret,
    token = token
)