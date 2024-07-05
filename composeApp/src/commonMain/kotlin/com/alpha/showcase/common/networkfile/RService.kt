package com.alpha.showcase.common.networkfile

import com.alpha.showcase.common.DEBUG
import com.alpha.showcase.common.networkfile.rclone.SERVE_PROTOCOL_HTTP
import com.alpha.showcase.common.storage.objectStoreOf
import com.alpha.showcase.common.utils.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rclone
import kotlin.concurrent.Volatile


const val R_SERVICE_WORKER_ARG_PORT = "port"
const val R_SERVICE_WORKER_ARG_ALLOW_REMOTE_ACCESS = "allowRemoteAccess"
const val R_SERVICE_WORKER_ARG_USER = "user"
const val R_SERVICE_WORKER_ARG_PASSWD = "passwd"
const val R_SERVICE_WORKER_ARG_SERVE_PATH = "servePath"
const val R_SERVICE_WORKER_ARG_BASE_URL = "baseUrl"
const val R_SERVICE_WORKER_ARG_REMOTE = "remote"
const val R_SERVICE_WORKER_NOTIFICATION_ID = 12121
const val R_SERVICE_WORKER_NOTIFICATION_NAME = "ShowCase Running"

const val R_SERVICE_ACCESS_BASE_URL = "access_url"

const val WAIT_FOR_SERVE_START = 2500L
const val LOCAL_ADDRESS = "http://localhost:"
object RService {

    val rcx: Rclone by lazy { rclone() }

    private val store = objectStoreOf<String>("rservice")

    @Volatile
    private var terminated = false

    private val workScope = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private var serveProcess: Process? = null

    suspend fun startRService(inputData: Data, onProgress: (Map<String, String>?) -> Unit){

        var cachePort: Int? = null
        var cacheBaseUrl: String? = null
        store.get()?.apply {
            try {
                val cachePortAndPath = split(":")
                cachePort = cachePortAndPath[0].toInt()
                cacheBaseUrl = cachePortAndPath[1]
            }catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val port = inputData.getInt(R_SERVICE_WORKER_ARG_PORT, cachePort?:rcx.allocatePort(0, true))
        val allowRemoteAccess =
            inputData.getBoolean(R_SERVICE_WORKER_ARG_ALLOW_REMOTE_ACCESS, false)
        val user = inputData.getString(R_SERVICE_WORKER_ARG_USER)
        val passwd = inputData.getString(R_SERVICE_WORKER_ARG_PASSWD)
        val servePath = inputData.getString(R_SERVICE_WORKER_ARG_SERVE_PATH)
        val baseUrl =
            inputData.getString(R_SERVICE_WORKER_ARG_BASE_URL) ?: (cacheBaseUrl?: rcx.genServeAuthPath())
        val remote = inputData.getString(R_SERVICE_WORKER_ARG_REMOTE)

        if (port != cachePort || baseUrl != cacheBaseUrl){
            store.set("$port:$baseUrl")
        }

        Log.d("$this $port $allowRemoteAccess $user, $passwd, $remote, $servePath, $baseUrl")
        stopRService()
        terminated = false
        withContext(workScope){
            while (!terminated){
                remote?.apply {
                    serveProcess = rcx.serve(
                        SERVE_PROTOCOL_HTTP,
                        port,
                        allowRemoteAccess,
                        user,
                        passwd,
                        remote,
                        servePath,
                        baseUrl,
                        DEBUG,
                        DEBUG,
//                    user,
//                    passwd
                    )
                    val serveUrl = "$LOCAL_ADDRESS$port/$baseUrl/"
                    delay(WAIT_FOR_SERVE_START)
                    onProgress(mapOf(R_SERVICE_ACCESS_BASE_URL to serveUrl))
                    Log.d(serveUrl)
                    serveProcess?.let {
                        if (DEBUG) {
                            thread {
                                rcx.logErrorOut(it)
                            }
                        }
                        it.waitFor()
                    }
                }
            }
        }
    }

    fun stopRService(){
        terminated = true
        serveProcess?.destroy()
    }

}