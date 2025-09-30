import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.view.Display
import android.view.Surface
import androidx.core.content.ContextCompat
import com.alpha.showcase.common.components.AndroidScreenFeature
import com.alpha.showcase.common.components.ScreenFeature
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import okio.FileSystem
import okio.Path.Companion.toPath
import androidx.core.net.toUri
import com.alpha.showcase.common.networkfile.model.LocalFile
import com.alpha.showcase.common.utils.Analytics
import com.alpha.showcase.common.utils.Device
import com.alpha.showcase.common.versionHash
import com.alpha.showcase.common.versionName
import java.util.Locale
import java.util.TimeZone


lateinit var AndroidApp: Application
var currentActivity: androidx.activity.ComponentActivity? = null

object AndroidPlatform : Platform {
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

    override fun getCacheDirectory(): String = AndroidApp.cacheDir.absolutePath

    override fun init() {
        FileKit.init(currentActivity!!)
    }

    override fun destroy() {

    }

    override fun getDevice(): Device?{
        return Device(
            id = Analytics.getInstance().deviceId,
            model = Build.MODEL,
            osName = "Android",
            name = getDeviceName(),
            oemName = Build.MANUFACTURER,
            osVersion = Build.VERSION.RELEASE,
            locale = Locale.getDefault().toString(),
            screenSize = getScreenSize(AndroidApp),
            appVersion = versionName,
            buildType = "release",
            appNameSpace = AndroidApp.packageName,
            appBuild = versionHash,
            osApi = Build.VERSION.SDK_INT.toString(),
            buildId = Build.ID,
            timezoneOffset = (TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000).toString(),
            carrierCountry = try {
                val telephonyManager =
                    AndroidApp.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val networkCountryIso = telephonyManager.networkCountryIso
                if (!TextUtils.isEmpty(networkCountryIso)) {
                    networkCountryIso
                }else {
                    null
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                null
            },
            cpuArch = System.getProperty("os.arch"),
            carrierName = try {
                val telephonyManager =
                    AndroidApp.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val networkOperatorName = telephonyManager.networkOperatorName
                if (!TextUtils.isEmpty(networkOperatorName)) {
                    networkOperatorName
                }else {
                    null
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                null
            }
        )
    }
    override fun listFiles(path: String): List<LocalFile> {
        if (ContextCompat.checkSelfPermission(
                currentActivity!!,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED ){
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
        }else {
            currentActivity?.requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                1000
            )
            return emptyList()
        }
    }

    private fun getScreenSize(context: Context): String {
        /* Guess resolution based on the natural device orientation */

        val screenWidth: Int
        val screenHeight: Int
        val defaultDisplay: Display
        val size = Point()

        /* Use DeviceManager to avoid android.os.strictmode.IncorrectContextUseViolation when StrictMode is enabled on API 30. */
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val displayMetrics = context.resources.displayMetrics
        size.x = displayMetrics.widthPixels
        size.y = displayMetrics.heightPixels
        when (defaultDisplay.rotation) {
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                screenHeight = size.x
                screenWidth = size.y
            }

            else -> {
                screenWidth = size.x
                screenHeight = size.y
            }
        }

        /* Serialize screen resolution */
        return screenWidth.toString() + "x" + screenHeight
    }

    fun getDeviceName(): String {
        var deviceName = try {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S){
                Settings.Secure.getString(AndroidApp.contentResolver, "bluetooth_name")
            }else {
                Build.MODEL
            }
        } catch (e: Exception) {
            Build.MODEL
        }
        if (TextUtils.isEmpty(deviceName)) {
            deviceName = Build.MODEL
        }
        return deviceName
    }
}

actual fun getPlatform(): Platform = AndroidPlatform
actual fun randomUUID(): String = java.util.UUID.randomUUID().toString(
actual fun getScreenFeature(): ScreenFeature = AndroidScreenFeature(currentActivity!!)