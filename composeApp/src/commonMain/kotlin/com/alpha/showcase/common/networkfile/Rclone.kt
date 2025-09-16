package com.alpha.showcase.common.networkfile

import com.alpha.showcase.api.rclone.RcloneFileItem
import com.alpha.showcase.api.rclone.Remote
import com.alpha.showcase.api.rclone.SpaceInfo
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.rclone.SERVE_PROTOCOL
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import kotlinx.coroutines.CoroutineDispatcher

private const val TAG = "Rclone:"

const val PROXY_PORT = 8899

const val DEFAULT_SERVE_PORT = 12121

const val COMMAND_LSJSON = "lsjson"
const val COMMAND_ABOUT = "about"
const val COMMAND_CONFIG = "config"
const val COMMAND_DELETE = "delete"
const val COMMAND_OBSCURE = "obscure"
const val COMMAND_COPY = "copy"
const val COMMAND_SERVE = "serve"
const val COMMAND_VERSION = "version"

const val OAUTH_PROCESS_REGEX = "go to the following link: ([^\\s]+)"


interface Rclone {

  val rClone: String

  val rCloneConfig: String

  val cacheDir: String

  val logFilePath: String

  val serveLogFilePath: String

  val downloadScope: CoroutineDispatcher

  fun createCommand(vararg args: String): Array<String>

  fun createCommandWithOption(vararg args: String): Array<String>

  val loggingEnable: Boolean

  val proxyEnable: Boolean

  val proxyPort: Int

  fun logOutPut(log: String)

  suspend fun setUpAndWait(rcloneRemoteApi: RcloneRemoteApi): Boolean


  suspend fun setUpAndWaitOAuth(
    options: List<String>,
    block: ((String?) -> Unit)? = null
  ): Boolean

  fun deleteRemote(remoteName: String): Boolean

  fun obscure(pass: String): String?

  fun configCreate(options: List<String>): Any?

  fun getConfigEnv(vararg options: String): Array<String>
  


  fun getDirContent(
    remote: Remote,
    path: String = "",
    recursive: Boolean = false,
    startAsRoot: Boolean = false
  ): Result<List<RcloneFileItem>>


  fun logErrorOut(process: Any): String

  fun getRemotes(): Result<List<Remote>>

  fun getRemote(key: String, block: ((Remote?) -> Unit)?)


  fun serve(
    serveProtocol: SERVE_PROTOCOL,
    port: Int,
    allowRemoteAccess: Boolean,
    user: String? = null,
    passwd: String? = null,
    remote: String,
    servePath: String? = null,
    baseUrl: String? = null,
    openRC: Boolean = false,
    openWebGui: Boolean = false,
    rcUser: String? = null,
    rcPasswd: String? = null
  ): Any?

  fun getFileInfo(remote: Remote, path: String): Result<RcloneFileItem>

  suspend fun suspendGetFileInfo(remote: Remote, path: String): Result<RcloneFileItem>

  fun aboutRemote(remote: Remote): SpaceInfo

  fun rcloneVersion(): String

  fun encodePath(localFilePath: String): String

  fun genServeAuthPath(): String


  fun allocatePort(port: Int, allocateFallback: Boolean): Int


  suspend fun getFileDirItems(
    storage: RcloneRemoteApi,
    path: String,
    recursive: Boolean = false
  ): Result<List<NetworkFile>>


  suspend fun getFileItems(
    storage: RcloneRemoteApi,
    recursive: Boolean = false,
    onlyDir: Boolean = false,
    filterMime: String? = null
  ): Result<List<NetworkFile>>

  suspend fun getFileItems(
    storage: RcloneRemoteApi,
    recursive: Boolean = false,
    filter: ((NetworkFile) -> Boolean)?
  ): Result<List<NetworkFile>>

  suspend fun getFileInfo(storage: RcloneRemoteApi): Result<NetworkFile>

  suspend fun getFileInfo(rcloneRemoteApi: RcloneRemoteApi, path: String): Result<NetworkFile>

  suspend fun parseConfig(remote: String, key: String): String?

  suspend fun updateConfig(remote: String, key: String, value: String): Boolean

}
