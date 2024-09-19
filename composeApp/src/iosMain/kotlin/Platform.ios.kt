import com.alpha.showcase.common.components.IOSScreenFeature
import com.alpha.showcase.common.components.ScreenFeature
import com.alpha.showcase.common.networkfile.Data
import com.alpha.showcase.common.networkfile.RService
import com.alpha.showcase.common.networkfile.Rclone
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.Foundation.NSUUID

class IOSPlatform: Platform {
    override val platform: PLATFORM_TYPE = PLATFORM_TYPE.Ios
    override val name: String = "${platform.platformName} ${UIDevice.currentDevice.systemName()} ${UIDevice.currentDevice.systemVersion}"
    override fun openUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: throw IllegalArgumentException("Illegal url: $url")
        UIApplication.sharedApplication.openURL(nsUrl)
    }

    override fun getConfigDirectory(): String {
        return ""
    }
}

actual fun getPlatform(): Platform = IOSPlatform()
actual fun randomUUID(): String = NSUUID().UUIDString()
actual fun rclone(): Rclone = IosRclone()
actual fun rService(): RService = IosRService

object IosRService : RService {
    override suspend fun startRService(
        inputData: Data,
        onProgress: (Data?) -> Unit
    ) {
        println("IosRService startRService")
    }

    override fun stopRService() {
        println("IosRService stopRService")
    }

}
actual fun getScreenFeature(): ScreenFeature = IOSScreenFeature()

