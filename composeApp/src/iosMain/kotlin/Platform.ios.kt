import com.alpha.Rclone
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.Foundation.NSUUID

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
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
