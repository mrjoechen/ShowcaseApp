@file:OptIn(ExperimentalTime::class)

import com.alpha.showcase.common.components.AppleScreenFeature
import com.alpha.showcase.common.components.ScreenFeature
import com.alpha.showcase.common.storage.cacheDir
import com.alpha.showcase.common.storage.storageDir
import com.alpha.showcase.common.utils.Device
import com.alpha.showcase.common.versionHash
import com.alpha.showcase.common.versionName
import kotlin.time.Clock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname
import kotlin.experimental.ExperimentalNativeApi
import kotlin.time.ExperimentalTime

/** Behaviour shared by the UIKit-based iOS and tvOS platforms. */
abstract class ApplePlatform : Platform {

    override val isTestFlight: Boolean
        get() = NSBundle.mainBundle.objectForInfoDictionaryKey("ShowcaseIsTestFlight") == "YES"

    override val name: String = "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}"

    override fun openUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: throw IllegalArgumentException("Illegal url: $url")
        UIApplication.sharedApplication.openURL(nsUrl)
    }

    override fun getConfigDirectory(): String = storageDir
    override fun getCacheDirectory(): String = cacheDir

    override fun destroy() {}

    @OptIn(ExperimentalForeignApi::class)
    override fun getDevice(): Device = memScoped {
        val systemInfo = alloc<utsname>()
        uname(systemInfo.ptr)
        val machineIdentifier = systemInfo.machine.toKString()
        Device(
            name = UIDevice.currentDevice.name,
            model = UIDevice.currentDevice.model,
            oemName = machineIdentifier,
            osName = UIDevice.currentDevice.systemName,
            osVersion = UIDevice.currentDevice.systemVersion,
            locale = NSLocale.currentLocale.localeIdentifier,
            appVersion = versionName,
            appNameSpace = "",
            appBuild = versionHash,
            buildType = if (isDebug) "debug" else "release",
            osApi = "",
            buildId = "",
            timezoneOffset = "${TimeZone.currentSystemDefault().offsetAt(Clock.System.now()).totalSeconds}",
            cpuArch = ""
        )
    }
}

@OptIn(ExperimentalNativeApi::class)
actual val isDebug: Boolean = kotlin.native.Platform.isDebugBinary

actual fun randomUUID(): String = NSUUID().UUIDString()

actual fun getScreenFeature(): ScreenFeature = AppleScreenFeature
