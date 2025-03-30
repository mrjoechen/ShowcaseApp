import com.alpha.showcase.common.components.IOSScreenFeature
import com.alpha.showcase.common.components.ScreenFeature
import com.alpha.showcase.common.networkfile.RService
import com.alpha.showcase.common.networkfile.Rclone
import com.alpha.showcase.common.networkfile.model.LocalFile
import com.alpha.showcase.common.storage.cacheDir
import com.alpha.showcase.common.storage.storageDir
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.Foundation.NSUUID

object IOSPlatform: Platform {

    init{
        println("IOSPlatform storageDir $storageDir")
        println("IOSPlatform cachesUrl $cacheDir")
    }
    override val platform: PLATFORM_TYPE = PLATFORM_TYPE.Ios
    override val name: String = "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}"
    override fun openUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: throw IllegalArgumentException("Illegal url: $url")
        UIApplication.sharedApplication.openURL(nsUrl)
    }

    override fun getConfigDirectory(): String = storageDir
    override fun getCacheDirectory(): String = cacheDir
    override fun init() {
    }

    override fun destroy() {

    }

    override fun listFiles(path: String): List<LocalFile> {
        TODO("Not yet implemented")
    }
}

actual fun getPlatform(): Platform = IOSPlatform
actual fun randomUUID(): String = NSUUID().UUIDString()
actual fun rclone(): Rclone? = null
actual fun rService(): RService? = null

actual fun getScreenFeature(): ScreenFeature = IOSScreenFeature

