import com.alpha.showcase.common.DEBUG
import com.alpha.showcase.common.networkfile.Data
import com.alpha.showcase.common.networkfile.LOCAL_ADDRESS
import com.alpha.showcase.common.networkfile.RService
import com.alpha.showcase.common.networkfile.R_SERVICE_ACCESS_BASE_URL
import com.alpha.showcase.common.networkfile.R_SERVICE_WORKER_ARG_ALLOW_REMOTE_ACCESS
import com.alpha.showcase.common.networkfile.R_SERVICE_WORKER_ARG_BASE_URL
import com.alpha.showcase.common.networkfile.R_SERVICE_WORKER_ARG_PASSWD
import com.alpha.showcase.common.networkfile.R_SERVICE_WORKER_ARG_PORT
import com.alpha.showcase.common.networkfile.R_SERVICE_WORKER_ARG_REMOTE
import com.alpha.showcase.common.networkfile.R_SERVICE_WORKER_ARG_SERVE_PATH
import com.alpha.showcase.common.networkfile.R_SERVICE_WORKER_ARG_USER
import com.alpha.showcase.common.networkfile.Rclone
import com.alpha.showcase.common.networkfile.WAIT_FOR_SERVE_START
import com.alpha.showcase.common.networkfile.rclone.SERVE_PROTOCOL_HTTP
import com.alpha.showcase.common.storage.objectStoreOf
import com.alpha.showcase.common.utils.Log
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.concurrent.thread


object AndroidRService: RService {

    private val rcx: Rclone by lazy { rclone() }

    private val store = objectStoreOf<String>("rservice")

    @Volatile
    private var terminated = false

    private val workScope = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private var serveProcess: Process? = null

    override suspend fun startRService(inputData: Data, onProgress: (Data?) -> Unit){

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
                remote.apply {
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
                    ) as Process
                    val serveUrl = "$LOCAL_ADDRESS$port/$baseUrl/"
                    delay(WAIT_FOR_SERVE_START)
                    onProgress(Data.dataOf(R_SERVICE_ACCESS_BASE_URL to serveUrl))
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

    override fun stopRService(){
        terminated = true
        serveProcess?.destroy()
    }

}