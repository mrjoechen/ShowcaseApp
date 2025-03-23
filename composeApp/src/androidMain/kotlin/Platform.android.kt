import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Environment
import com.alpha.showcase.common.components.AndroidScreenFeature
import com.alpha.showcase.common.components.ScreenFeature
import com.alpha.showcase.common.networkfile.RService
import com.alpha.showcase.common.networkfile.Rclone
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import okio.FileSystem
import okio.Path.Companion.toPath
import androidx.core.net.toUri
import com.alpha.showcase.common.networkfile.model.LocalFile


lateinit var AndroidApp: Application
var currentActivity: androidx.activity.ComponentActivity? = null

class AndroidPlatform : Platform {
    override val platform: PLATFORM_TYPE = PLATFORM_TYPE.Android
    override val name: String = "${platform.platformName} ${Build.VERSION.SDK_INT}"
    override fun openUrl(url: String) {
        val uri = url.toUri()
        val intent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        AndroidApp.startActivity(intent)
    }

    override fun getConfigDirectory(): String = AndroidApp.filesDir.absolutePath
    override fun init() {
        FileKit.init(currentActivity!!)
    }

    override fun destroy() {

    }

    override fun listFiles(path: String): List<LocalFile> {
        return FileSystem.SYSTEM.list(path.replace("/tree/primary:", Environment.getExternalStorageDirectory().path).toPath()).map {
            val file = it.toFile()
            LocalFile(
                file.toString(),
                file.name,
                file.isDirectory,
                file.length(),
                file.extension,
                file.lastModified().toString()
            )
        }
    }
}

actual fun getPlatform(): Platform = AndroidPlatform()
actual fun randomUUID(): String = java.util.UUID.randomUUID().toString()
actual fun rclone(): Rclone? = AndroidRclone(AndroidApp)
actual fun rService(): RService? = AndroidRService
actual fun getScreenFeature(): ScreenFeature = AndroidScreenFeature(currentActivity!!)