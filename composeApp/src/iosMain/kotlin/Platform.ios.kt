@file:OptIn(ExperimentalTime::class)

import com.alpha.showcase.common.components.IOSScreenFeature
import com.alpha.showcase.common.components.ScreenFeature
import com.alpha.showcase.common.networkfile.RService
import com.alpha.showcase.common.networkfile.Rclone
import com.alpha.showcase.common.networkfile.model.LocalFile
import com.alpha.showcase.common.storage.cacheDir
import com.alpha.showcase.common.storage.storageDir
import com.alpha.showcase.common.utils.Analytics
import com.alpha.showcase.common.utils.Device
import com.alpha.showcase.common.versionHash
import com.alpha.showcase.common.versionName
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.Foundation.NSLocale
import platform.Foundation.NSUUID
import platform.posix.*
import kotlinx.cinterop.*
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import kotlin.time.ExperimentalTime

object IOSPlatform: Platform {

//    init{
//        println("IOSPlatform storageDir $storageDir")
//        println("IOSPlatform cachesUrl $cacheDir")
//    }
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

    @OptIn(ExperimentalForeignApi::class)
    override fun getDevice(): Device {
        memScoped { // 创建一个内存作用域，用于分配 C 结构体
            val systemInfo = alloc<utsname>() // 分配 utsname 结构体内存
            uname(systemInfo.ptr) // 调用 uname 系统函数填充结构体
            // 从结构体中读取 machine 字段，并转换为 Kotlin String
            val machineIdentifier = systemInfo.machine.toKString()
            println("IOSPlatform machineIdentifier: $machineIdentifier")
            val device = Device(
                id = Analytics.getInstance().deviceId,
                name = UIDevice.currentDevice.name,
                model = UIDevice.currentDevice.model,
                oemName = machineIdentifier,
                osName = UIDevice.currentDevice.systemName,
                osVersion = UIDevice.currentDevice.systemVersion,
                locale = NSLocale.currentLocale.localeIdentifier,
                appVersion = versionName,
                appNameSpace = "",
                appBuild = versionHash,
                buildType = "debug",
                osApi = "",
                buildId = "",
                timezoneOffset = "${TimeZone.currentSystemDefault().offsetAt(Clock.System.now()).totalSeconds}",
                cpuArch = ""
            )
            return device
        }

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

