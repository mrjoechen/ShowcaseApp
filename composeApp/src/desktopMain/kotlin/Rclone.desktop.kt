@file:OptIn(ExperimentalEncodingApi::class)

import com.alpha.COMMAND_ABOUT
import com.alpha.COMMAND_CONFIG
import com.alpha.COMMAND_COPY
import com.alpha.COMMAND_DELETE
import com.alpha.COMMAND_LSJSON
import com.alpha.COMMAND_OBSCURE
import com.alpha.COMMAND_SERVE
import com.alpha.OAUTH_PROCESS_REGEX
import com.alpha.Rclone
import com.alpha.networkfile.model.NetworkFile
import com.alpha.networkfile.storage.ext.toRemote
import com.alpha.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.networkfile.storage.remote.RemoteStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okio.use
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.util.Properties
import java.util.regex.Pattern
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import com.alpha.networkfile.rclone.SERVE_PROTOCOL
import com.alpha.networkfile.rclone.succeeded
import com.alpha.showcase.api.rclone.RcloneFileItem
import com.alpha.showcase.api.rclone.Remote
import com.alpha.showcase.api.rclone.SpaceInfo
import kotlinx.coroutines.asCoroutineDispatcher
import okio.buffer
import okio.source
import java.lang.StringBuilder
import java.security.SecureRandom
import java.util.concurrent.Executors
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import com.alpha.networkfile.rclone.Result
import com.alpha.showcase.api.rclone.About
import com.alpha.showcase.api.rclone.RemoteConfig

const val TAG = "DesktopRclone"
const val WIN_NATIVE_LIB_NAME = "rclone.exe"
const val MAC_NATIVE_LIB_NAME = "macos_rclone"
const val LINUX_NATIVE_LIB_NAME = "linux_rclone.so"

class DesktopRclone: Rclone {

  override val downloadScope = Executors.newFixedThreadPool(3).asCoroutineDispatcher()

  override val rClone = AppConfig.getRclonePath()
  override val rCloneConfig = AppConfig.getConfigDirectory() + "rclone.conf"
  override val logFilePath = AppConfig.getConfigDirectory() + "rclone.log"
  override val serveLogFilePath = AppConfig.getConfigDirectory() + "rclone_serve.log"

  override val cacheDir: String = AppConfig.getCacheDirectory()
  override val loggingEnable: Boolean = true
  override val proxyEnable: Boolean = false
  override val proxyPort: Int = 8899
  override fun logOutPut(log: String) {
    println("$TAG: $log")
  }


  private fun createCommandWithOption(vararg args: String): Array<String> {
    val size = if (loggingEnable) 8 else 7
    val command = Array(size + args.size) {""}
    command[0] = rClone
    command[1] = "--cache-chunk-path"
    command[2] = cacheDir
    command[3] = "--cache-db-path"
    command[4] = cacheDir
    command[5] = "--config"
    command[6] = rCloneConfig
    if (loggingEnable) {
      command[7] = "-vvv"
    }
    var index = size
    args.forEach {
      command[index ++] = it
    }
    return command
  }

  override suspend fun setUpAndWait(rcloneRemoteApi: RcloneRemoteApi): Boolean {

    if (rcloneRemoteApi is RemoteStorage) {
      val createProcess = configCreate(rcloneRemoteApi.genRcloneOption())
      createProcess ?: apply {
        logOutPut("Error create remote !")
        return false
      }

      createProcess?.apply {
        var exitCode: Int
        while (true) {
          try {
            exitCode = waitFor()
            break
          } catch (e: InterruptedException) {
            e.printStackTrace()
            try {
              exitCode = exitValue()
              break
            } catch (ignored: IllegalStateException) {
              ignored.printStackTrace()
            }
          }
        }
        return exitCode == 0
      }
      return false
    }
    return false
  }


  override suspend fun setUpAndWaitOAuth(
    options: List<String>,
    block: ((String?) -> Unit)?
  ): Boolean {

    return withContext(Dispatchers.IO) {

      suspendCancellableCoroutine{ continuation ->
        var createProcess: Process? = null
        continuation.invokeOnCancellation {
          createProcess?.destroy()
        }
        createProcess = configCreate(options)
        createProcess ?: apply {
          logOutPut("Error create remote !")
          continuation.resume(false)
        }
        try {
          createProcess?.apply {
            thread {
              try {
                val reader = BufferedReader(InputStreamReader(createProcess.errorStream))
                var line: String?
                while (reader.readLine().also {
                    line = it
                  } != null) {

                  line?.let {
                    logOutPut(it)
                    val pattern = Pattern.compile(OAUTH_PROCESS_REGEX, 0)
                    pattern.matcher(it).apply {
                      if (find()) {
                        val url = group(1)
                        if (url != null) {
                          logOutPut("oauth url: $url")
                          block?.invoke(url)
                        }
                      }
                    }
                  }
                }
              }catch (e: Exception){
                e.printStackTrace()
              }
            }

            var exitCode: Int
            while (true) {
              try {
                exitCode = waitFor()
                break
              } catch (e: InterruptedException) {
                e.printStackTrace()
                try {
                  exitCode = exitValue()
                  break
                } catch (ignored: IllegalStateException) {
                  ignored.printStackTrace()
                }
              }
            }
            continuation.resume(exitCode == 0)
          }
        }catch (e: Exception){
          e.printStackTrace()
          try {
            createProcess?.destroy()
          }catch (e: Exception){
            e.printStackTrace()
          }
          continuation.resume(false)
        }
      }
    }
  }

  override fun deleteRemote(remoteName: String): Boolean{
    val strings = createCommandWithOption(COMMAND_CONFIG, COMMAND_DELETE, remoteName)
    val process: Process
    return try {
      process = Runtime.getRuntime().exec(strings)
      process.waitFor()
      process.exitValue() == 0
    } catch (e: IOException) {
      logOutPut("$TAG deleteRemote: error delete remote $e")
      false
    } catch (e: InterruptedException) {
      logOutPut("$TAG deleteRemote: error delete remote $e")
      false
    }
  }

  override fun obscure(pass: String): String? {
    val command = createCommand(COMMAND_OBSCURE, pass)
    val process: Process
    return try {
      process = Runtime.getRuntime().exec(command)
      process.waitFor()
      if (process.exitValue() != 0) {
        return null
      }
      process.inputStream.source().use {
        it.buffer().readUtf8()
      }
    } catch (e: IOException) {
      logOutPut("$TAG obscure: error starting rclone $e")
      null
    } catch (e: InterruptedException) {
      logOutPut("$TAG obscure: error starting rclone $e")
      null
    }
  }

  override fun configCreate(options: List<String>): Process? {
    val command = createCommand(COMMAND_CONFIG, "create")
    val opt = options.toTypedArray()
    val commandWithOptions = Array(command.size + options.size) {""}
    System.arraycopy(command, 0, commandWithOptions, 0, command.size)
    System.arraycopy(opt, 0, commandWithOptions, command.size, opt.size)
    return try {
      Runtime.getRuntime().exec(commandWithOptions)
    } catch (e: IOException) {
      e.printStackTrace()
      logOutPut("$TAG configCreate: error starting rclone $e")
      null
    }
  }

  private fun getConfigEnv(vararg options: String): Array<String> {
    val environmentValues = mutableListOf<String>()

    if (proxyEnable) {
      val noProxy = "localhost"
      val protocol = "http"
      val host = "localhost"
      val url = "$protocol://$host:$proxyPort"
      environmentValues.add("http_proxy=$url")
      environmentValues.add("https_proxy=$url")
      environmentValues.add("no_proxy=$noProxy")
    }

    environmentValues.add("TMPDIR=$cacheDir")
    environmentValues.add("RCLONE_LOCAL_NO_SET_MODTIME=true")

    // Allow the caller to overwrite any option for special cases
    val envVarIterator = environmentValues.iterator()
    while (envVarIterator.hasNext()) {
      val envVar = envVarIterator.next()
      val optionName = envVar.substring(0, envVar.indexOf('='))
      for (overwrite in options) {
        if (overwrite.startsWith(optionName)) {
          envVarIterator.remove()
          environmentValues.add(overwrite)
        }
      }
    }
    return environmentValues.toTypedArray()
  }

  //todo
  fun encryptConfigFile(password: String): Boolean {
    val command = createCommand(COMMAND_CONFIG)
    return try {
      val process = Runtime.getRuntime().exec(command)

      val reader = BufferedReader(InputStreamReader(process.inputStream))
      val writer = BufferedWriter(OutputStreamWriter(process.outputStream))

      var setting = false
      var alreadySetPass = false
      var line: String
      while (reader.readLine().also { line = it } != null) {
        logOutPut("Received: $line")

        // 根据输出进行条件判断，然后发送新的命令
        if ("q) Quit config" == line) {
          if (setting) {
            writer.write("q\n")
            writer.flush()
          } else {
            writer.write("s\n")
            writer.flush()
            setting = true
          }
        } else if ("q) Quit to main menu" == line) {
          if (alreadySetPass) {
            writer.write("q\n")
            writer.flush()
          } else {
            writer.write("a\n")
            writer.flush()
          }
        } else if ("Enter NEW configuration password:" == line) {
          writer.write("$password\n")
          writer.flush()
        } else if ("Confirm NEW configuration password:" == line) {
          writer.write("$password\n")
          writer.flush()
          alreadySetPass = true
        }
      }

      process.waitFor()
      true
    } catch (e: IOException) {
      e.printStackTrace()
      logOutPut("$TAG configCreate: error starting rclone $e")
      false
    }

  }


  override fun getDirContent(
    remote: Remote,
    path: String,
    recursive: Boolean,
    startAsRoot: Boolean
  ): Result<List<RcloneFileItem>> {

    var remotePath = remote.key + ":"
    if (startAsRoot) remotePath += ""
    if (remotePath.compareTo("//" + remote.key) != 0) remotePath += path
    val process: Process

    try {
      val command = if (recursive) createCommandWithOption(
        COMMAND_LSJSON,
        remotePath,
        "-R"
      ) else createCommandWithOption(COMMAND_LSJSON, remotePath)
      process = Runtime.getRuntime().exec(command, getConfigEnv())

      val reader = BufferedReader(InputStreamReader(process.inputStream))
      var line: String?
      val output = StringBuilder()
      while (reader.readLine().also { line = it } != null) {
        output.append(line)
      }
      process.waitFor()

      if (process.exitValue() != 0 && (process.exitValue() != 6)) {
        val logErrorOut = logErrorOut(process)
        return Result.Error(logErrorOut)
      }

      val result = output.toString()
      val rCloneFileItemList = Json.decodeFromString<ArrayList<RcloneFileItem>>(result)
      return Result.Success(rCloneFileItemList)
    } catch (ex: InterruptedException) {
      ex.printStackTrace()
      logOutPut(ex.toString())
    } catch (ex: IOException) {
      ex.printStackTrace()
      logOutPut(ex.toString())
    }

    return Result.Error("Error retrieving directory content.")
  }


  override fun logErrorOut(process: Any): String {
    val stringBuilder = java.lang.StringBuilder(100)
    try {
      BufferedReader(InputStreamReader((process as Process).errorStream)).use {reader ->
        var line: String?
        while (reader.readLine().also {line = it} != null) {
          stringBuilder.append(line).append("\n")
        }
      }
    } catch (iioe: InterruptedIOException) {
      iioe.printStackTrace()
      logOutPut("$TAG logErrorOutput: process died while reading. Log may be incomplete.")
    } catch (e: IOException) {
      e.printStackTrace()
      if ("Stream closed" == e.message) {
        logOutPut("$TAG logErrorOutput: could not read stderr, process stream is already closed")
      } else {
        logOutPut("$TAG logErrorOutput: $e")
      }
    }
    val log = stringBuilder.toString()
    logOutPut(log)

    return log
  }

  override fun getRemotes(): Result<List<Remote>> {

    val mutableList = mutableListOf<Remote>()
    val command = createCommand(COMMAND_CONFIG, "dump")
    val output = java.lang.StringBuilder()
    val process: Process

    try {
      process = Runtime.getRuntime().exec(command)
      val reader = BufferedReader(InputStreamReader(process.inputStream))
      var line: String?
      while (reader.readLine().also {line = it} != null) {
        output.append(line)
      }
      process.waitFor()
      if (process.exitValue() != 0) {
        val logErrorOut = logErrorOut(process)
        return Result.Error(logErrorOut)
      }

      val json = Json {ignoreUnknownKeys = true}
      val parseToJsonElement = json.parseToJsonElement(output.toString())

      parseToJsonElement.jsonObject.keys.forEach {
        val jsonElement = parseToJsonElement.jsonObject[it]
        val remoteConfig = json.decodeFromJsonElement<RemoteConfig>(jsonElement !!)
        mutableList.add(Remote(it, remoteConfig))
      }
      return Result.Success(mutableList)

    } catch (e: IOException) {
      e.printStackTrace()
      logOutPut("$TAG  getRemotes: error retrieving remotes $e")
    } catch (e: InterruptedException) {
      e.printStackTrace()
      logOutPut("$TAG  getRemotes: error retrieving remotes $e")
    } catch (e: Exception) {
      e.printStackTrace()
      logOutPut("$TAG  getRemotes: error retrieving remotes $e")
    }

    return Result.Error("Error retrieving remotes.")

  }


  override fun serve(
    serveProtocol: SERVE_PROTOCOL,
    port: Int,
    allowRemoteAccess: Boolean,
    user: String?,
    passwd: String?,
    remote: String,
    servePath: String?,
    baseUrl: String?
  ): Process? {

//    val localRemotePath = if (remote.isType(LOCAL)) getLocalPathPrefix(remote) + "/" else ""
    val localRemotePath = ""

    val path =
      if (servePath == null || servePath.compareTo("//$remote") == 0)
        "$remote:$localRemotePath"
      else
        "$remote:$localRemotePath$servePath"
    val address = if (allowRemoteAccess) ":$port" else "127.0.0.1:$port"
    val protocol = serveProtocol.name
    val params =
      createCommandWithOption(
        COMMAND_SERVE,
        protocol,
        "--addr",
        address,
        path,
        "--vfs-cache-mode",
        "writes",
        "--no-modtime"
      ).toMutableList()
    user?.apply {
      params.add("--user")
      params.add(this)
    }
    passwd?.apply {
      params.add("--pass")
      params.add(this)
    }
    baseUrl?.apply {
      params.add("--baseurl")
      params.add(this)
    }

    if (loggingEnable && File(serveLogFilePath).exists()) {
      params.add("--log-file")
      params.add(serveLogFilePath)
    }
    val command = params.toTypedArray()
    return try {
      Runtime.getRuntime().exec(command, getConfigEnv())
    } catch (exception: IOException) {
      logOutPut("$TAG  serve error: $exception")
      null
    }
  }

  fun downloadFile(remote: Remote, path: String, file: RcloneFileItem, downloadToLocalPath: String): Result<File> {
    val command: Array<String>
    val remoteFilePath = "${remote.key}:$path/${file.path}"
    var localFilePath = if (file.isDir) {
      downloadToLocalPath + "/" + file.name
    } else {
      downloadToLocalPath
    }

    localFilePath = encodePath(localFilePath)

    command = createCommandWithOption(
      COMMAND_COPY,
      remoteFilePath,
      localFilePath,
      "--transfers",
      "1",
      "--stats=1s",
      "--stats-log-level",
      "NOTICE"
    )

    val env: Array<String> = getConfigEnv()
    return try {
      val downloadProcess = Runtime.getRuntime().exec(command, env)
      downloadProcess.apply {
        try {
          waitFor()
          return if (exitValue() == 0) {
            val readOnlyFile = File("$downloadToLocalPath/${file.name}")
            readOnlyFile.setReadOnly()
            Result.Success(readOnlyFile)
          } else {
            val logErrorOut = logErrorOut(this)
            Result.Error(logErrorOut)
          }
        } catch (e: InterruptedException) {
          e.printStackTrace()
          logOutPut(e.toString())
        }
      }
      Result.Error("DownloadFile: error")
    } catch (e: IOException) {
      logOutPut("$TAG downloadFile: error starting rclone $e")
      Result.Error("DownloadFile: error")
    }
  }

  override fun getFileInfo(remote: Remote, path: String): Result<RcloneFileItem>{
    var remotePath = remote.key + ":"
    if (remotePath.compareTo("//" + remote.key) != 0) remotePath += path
    val process: Process

    try {
      val command = createCommandWithOption(COMMAND_LSJSON, remotePath, "--stat")
      process = Runtime.getRuntime().exec(command, getConfigEnv())

      val reader = BufferedReader(InputStreamReader(process.inputStream))
      var line: String?
      val output = StringBuilder()
      while (reader.readLine().also {line = it} != null) {
        output.append(line)
      }
      process.waitFor()

      if (process.exitValue() != 0 && (process.exitValue() != 6)) {
        val logErrorOut = logErrorOut(process)
        return Result.Error(logErrorOut)
      }

      val result = output.toString()
      val rCloneFileItemList = Json.decodeFromString<RcloneFileItem>(result)
      return Result.Success(rCloneFileItemList)
    } catch (ex: InterruptedException) {
      ex.printStackTrace()
      logOutPut(ex.toString())
    } catch (ex: IOException) {
      ex.printStackTrace()
      logOutPut(ex.toString())
    }

    return Result.Error("Error retrieving directory content.")

  }

  override suspend fun suspendGetFileInfo(remote: Remote, path: String) =
    suspendCancellableCoroutine { continuation ->
      var remotePath = remote.key + ":"
      if (remotePath.compareTo("//" + remote.key) != 0) remotePath += path
      var process: Process? = null
      continuation.invokeOnCancellation {
        process?.destroy()
      }
      try {
        val command = createCommandWithOption(COMMAND_LSJSON, remotePath, "--stat")
        process = Runtime.getRuntime().exec(command, getConfigEnv())
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        val output = StringBuilder()
        while (reader.readLine().also { line = it } != null) {
          output.append(line)
        }
        process.waitFor()

        if (process.exitValue() != 0 && (process.exitValue() != 6)) {
          val logErrorOut = logErrorOut(process)
          continuation.resume(Result.Error(logErrorOut))
        } else {
          val result = output.toString()
          val rCloneFileItemList = Json.decodeFromString<RcloneFileItem>(result)
          continuation.resume(Result.Success(rCloneFileItemList))
        }
      } catch (ex: InterruptedException) {
        ex.printStackTrace()
        logOutPut(ex.toString())
        continuation.resume(Result.Error("Error retrieving directory content."))
      } catch (ex: IOException) {
        ex.printStackTrace()
        logOutPut(ex.toString())
        continuation.resume(Result.Error("Error retrieving directory content."))
      }
    }


  override fun aboutRemote(remote: Remote): SpaceInfo {
    val remoteName: String = remote.key + ':'
    val command = createCommand(COMMAND_ABOUT, "--json", remoteName)
    val output = java.lang.StringBuilder()
    val process: Process
    try {
      process = Runtime.getRuntime().exec(command, getConfigEnv())
      BufferedReader(InputStreamReader(process.inputStream)).use {reader ->
        var line: String?
        while (reader.readLine().also {line = it} != null) {
          output.append(line)
        }
      }
      process.waitFor()
      if (0 != process.exitValue()) {
        logOutPut("$TAG aboutRemote: rclone error, exit(%d) ${process.exitValue()}")
        logOutPut("$TAG aboutRemote: $output")
        logErrorOut(process)
        return SpaceInfo()
      }
    } catch (e: IOException) {
      logOutPut("$TAG aboutRemote: unexpected error $e")
    } catch (e: InterruptedException) {
      logOutPut("$TAG aboutRemote: unexpected error $e")
    }
    return try {
      val about = Json.decodeFromString<About>(output.toString())
      SpaceInfo(about.used, about.free, about.total, about.trashed)
    } catch (e: Exception) {
      logOutPut("$TAG aboutRemote: JSON format error $e")
      return SpaceInfo()
    }
  }

  override fun encodePath(localFilePath: String): String {
    if (localFilePath.indexOf('\u0000') < 0) {
      return localFilePath
    }
    val localPathBuilder = java.lang.StringBuilder(localFilePath.length)
    for (c in localFilePath.toCharArray()) {
      if (c == '\u0000') {
        localPathBuilder.append('\u2400')
      } else {
        localPathBuilder.append(c)
      }
    }
    return localPathBuilder.toString()
  }


  @OptIn(ExperimentalEncodingApi::class)
  override fun genServeAuthPath(): String{
    val secureRandom = SecureRandom()
    val value = ByteArray(16)
    secureRandom.nextBytes(value)
    return Base64.encode(value, 0, value.size)
  }


  override fun allocatePort(port: Int, allocateFallback: Boolean): Int {
    try {
      ServerSocket(port).use {serverSocket ->
        serverSocket.reuseAddress = true
        return serverSocket.localPort
      }
    } catch (e: IOException) {
      if (allocateFallback) {
        return allocatePort(0, false)
      }
    }
    throw java.lang.IllegalStateException("No port available")
  }

  suspend fun getFiles(storage: RemoteStorage, filterMime: String? = null): Result<List<File>> {
    val fileList = mutableListOf<File>()
    return withContext(Dispatchers.IO){
      val remote = storage.toRemote()
      val result = getDirContent(remote, storage.path, false)
      if (result is Result.Success) {
        result.data?.run {
          if (filterMime != null) filter {
            it.mimeType == filterMime
          } else this
        }?.forEach {
          getFile(storage, it).apply {
            if (this is Result.Success && this.succeeded){
              fileList.add(data!!)
            }
          }
        }
        Result.Success(fileList)
      } else {
        Result.Error("Error Connect.")
      }
    }
  }


  override suspend fun getFileDirItems(
    storage: RcloneRemoteApi,
    path: String,
    recursive: Boolean
  ): Result<List<NetworkFile>> {
    val fileList = mutableListOf<NetworkFile>()
    return withContext(Dispatchers.IO) {
      val remote = storage.toRemote()
      val result = getDirContent(remote, path, recursive)
      if (result is Result.Success) {
        result.data?.forEach {
          if (!it.isDir) return@forEach
          fileList.add(
            NetworkFile(
              remote,
              it.path,
              it.name,
              it.isDir,
              it.size,
              it.mimeType,
              it.modTime,
              it.isBucket
            )
          )
        }
        Result.Success(fileList)
      } else {
        Result.Error("Error Connect.")
      }
    }
  }


  override suspend fun getFileItems(
    storage: RcloneRemoteApi,
    recursive: Boolean,
    onlyDir: Boolean,
    filterMime: String?
  ): Result<List<NetworkFile>> {
    val fileList = mutableListOf<NetworkFile>()
    return withContext(Dispatchers.IO) {
      val remote = storage.toRemote()
      val result = getDirContent(remote, storage.path, recursive)
      if (result is Result.Success) {
        result.data?.run {
          if (filterMime != null) filter {
            it.mimeType == filterMime
          } else this
        }?.forEach {

          if (onlyDir && !it.isDir) return@forEach
          fileList.add(
            NetworkFile(
              remote,
              it.path,
              it.name,
              it.isDir,
              it.size,
              it.mimeType,
              it.modTime,
              it.isBucket
            )
          )
        }
        Result.Success(fileList)
      } else {
        Result.Error("Error Connect.")
      }
    }
  }

  override suspend fun getFileItems(
    storage: RcloneRemoteApi,
    recursive: Boolean,
    filter: ((NetworkFile) -> Boolean)?
  ): Result<List<NetworkFile>> {
    val fileList = mutableListOf<NetworkFile>()
    return withContext(Dispatchers.IO) {
      val remote = storage.toRemote()
      val result = getDirContent(remote, storage.path, recursive)
      if (result is Result.Success) {
        result.data?.forEach {
          fileList.add(
            NetworkFile(
              remote,
              it.path,
              it.name,
              it.isDir,
              it.size,
              it.mimeType,
              it.modTime,
              it.isBucket
            )
          )
        }
        val filtered = fileList.filter {
          filter?.invoke(it) ?: true
        }
        Result.Success(filtered)
      } else {
        Result.Error("Error Connect.")
      }
    }
  }

  override suspend fun getFileInfo(storage: RcloneRemoteApi): Result<NetworkFile>{
    return withContext(Dispatchers.IO){
      val remote = storage.toRemote()
      val result = suspendGetFileInfo(remote, storage.path)
      if (result is Result.Success) {
        val info = result.data
        if (info != null) {
          Result.Success(
            NetworkFile(
              remote,
              storage.path,
              info.name,
              info.isDir,
              info.size,
              info.mimeType,
              info.modTime,
              info.isBucket
            )
          )
        } else {
          Result.Error("Empty info.")
        }
      } else {
        Result.Error("Error Connect.")
      }
    }
  }

  override suspend fun getFileInfo(rcloneRemoteApi: RcloneRemoteApi, path: String): Result<NetworkFile> {
    return withContext(Dispatchers.IO) {
      val remote = rcloneRemoteApi.toRemote()
      val result = suspendGetFileInfo(remote, path)
      if (result is Result.Success) {
        val info = result.data
        if (info != null) {
          Result.Success(
            NetworkFile(
              remote,
              path,
              info.name,
              info.isDir,
              info.size,
              info.mimeType,
              info.modTime,
              info.isBucket
            )
          )
        } else {
          Result.Error("Empty info.")
        }
      } else {
        Result.Error("Error Connect.")
      }
    }
  }

  suspend fun getFiles(
    storage: RemoteStorage,
    onSuccess: ((File) -> Unit)?,
    recursive: Boolean = false,
    filterMime: String? = null
  ) {
    withContext(Dispatchers.IO) {
      val result = getDirContent(storage.toRemote(), storage.path, recursive)
      if (result is Result.Success) {
        result.data?.run {
          if (filterMime != null) {
            filter {
              it.mimeType == filterMime
            }
          } else {
            this
          }
        }?.forEach {
          getFile(storage, it).apply {
            if (this.succeeded) {
              onSuccess?.invoke((this as Result.Success).data!!)
            }
          }
        }
      }
    }
  }

  suspend fun getFile(storage: RemoteStorage, file: RcloneFileItem): Result<File> {
    return withContext(downloadScope) {
      val savePath = "$cacheDir/${storage.name}/${storage.path}/"
      downloadFile(storage.toRemote(), storage.path, file, savePath)
    }
  }

  override suspend fun parseConfig(remote: String, key: String): String? {
    return withContext(Dispatchers.IO) {
      val configFile = File(rCloneConfig)
      if (configFile.exists()) {
        val reader = configFile.inputStream().reader()

        val properties = Properties()
        lateinit var section: String

        reader.readLines().forEach { line ->
          when {
            line.isEmpty() || line.startsWith(";") -> {
              // 忽略空行和注释行
            }
            line.startsWith("[") && line.endsWith("]") -> {
              // 解析 section
              section = line.substring(1, line.length - 1)
            }
            else -> {
              // 解析 key-value pair
              val (k, v) = line.split("=").map { it.trim() }
              properties.setProperty("$section.$k", v)
            }
          }
        }
        properties.getProperty("$remote.$key")?.let {
          it
        }
      }else {
        null
      }
    }
  }

  override suspend fun updateConfig(remote: String, key: String, value: String): Boolean{
    return withContext(Dispatchers.IO) {
      val configFile = File(rCloneConfig)
      if (configFile.exists()) {
        val lines = configFile.inputStream().reader().readLines()
        // Create a map to store sections and properties
        val map = LinkedHashMap<String, LinkedHashMap<String, String>>()
        var currentSection = ""

        // Loop through lines and parse sections and properties
        for (line in lines) {
          if (line.startsWith("[")) {
            // Found new section
            currentSection = line.substring(1, line.lastIndexOf("]"))
            map[currentSection] = LinkedHashMap()
          } else if (line.contains("=")) {
            // Found property
            val keyValuePair = line.split("=")
            val k = keyValuePair[0].trim()
            val v = keyValuePair[1].trim()
            map[currentSection]?.put(k, v)
          }
        }

        // Update property value
        map[remote]?.put(key, value)

        // Convert map back to INI string
        val sb = StringBuilder()
        for ((section, properties) in map) {
          sb.append("[$section]\n")
          for ((k, v) in properties) {
            sb.append("$k = $v\n")
          }
          sb.append("\n")
        }

        try {
          configFile.outputStream().writer().use {
            it.write(sb.toString())
          }
          true
        }catch (e: Exception){
          e.printStackTrace()
          false
        }
      }else {
        false
      }
    }
  }

}

object AppConfig {
  fun getConfigDirectory(): String {
    val os = getPlatformName()
    return when {
      os.contains("win") -> System.getenv("APPDATA") + "\\Showcase\\"
      os.contains("mac") -> System.getProperty("user.home") + "/Library/Application Support/Showcase/"
      else -> System.getProperty("user.home") + "/.config/Showcase/"
    }
  }

  fun getRclonePath(): String{
    val os = System.getProperty("os.name").lowercase()
    return when {
      os.contains("win") -> getConfigDirectory() + WIN_NATIVE_LIB_NAME
      os.contains("mac") -> getConfigDirectory() + MAC_NATIVE_LIB_NAME
      else -> getConfigDirectory() + LINUX_NATIVE_LIB_NAME
    }
  }

  fun getCacheDirectory(): String {
    return getConfigDirectory() + "cache/"
  }

  fun isWindows(): Boolean {
    return getPlatformName().contains("win")
  }

  fun isMac(): Boolean {
    return getPlatformName().contains("mac")
  }

  fun getPlatformName(): String {
    return System.getProperty("os.name").lowercase()
  }
}
