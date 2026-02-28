import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
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
        val normalizedPath = path.trim()
        if (normalizedPath.isEmpty()) {
            return emptyList()
        }

        return if (normalizedPath.startsWith("content://", ignoreCase = true)) {
            listDocumentDirectory(normalizedPath)
        } else {
            ensureLegacyStoragePermissionIfNeeded()
            val localPath = normalizeLegacyLocalPath(normalizedPath)
            FileSystem.SYSTEM.list(localPath.toPath()).map {
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

    private fun listDocumentDirectory(uriString: String): List<LocalFile> {
        val documentUri = Uri.parse(uriString)
        val treeUri = documentUri.toTreeUriIfNeeded()

        persistUriPermissionIfPossible(documentUri)
        persistUriPermissionIfPossible(treeUri)

        val documentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrElse {
            runCatching { DocumentsContract.getDocumentId(documentUri) }.getOrNull()
        }
            ?: return emptyList()

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        return runCatching {
            AndroidApp.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeTypeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modTimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                buildList {
                    while (cursor.moveToNext()) {
                        val childId = cursor.getStringOrNull(idIndex) ?: continue
                        val childUri =
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        val mimeType = cursor.getStringOrNull(mimeTypeIndex).orEmpty()
                        val isDirectory =
                            mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                        val childPath = childUri.toString()
                        val childName = cursor.getStringOrNull(nameIndex)
                            ?: childPath.substringAfterLast('/')

                        add(
                            LocalFile(
                                path = childPath,
                                fileName = childName,
                                isDirectory = isDirectory,
                                size = if (isDirectory) 0L else cursor.getLongOrDefault(sizeIndex),
                                mimeType = mimeType,
                                modTime = cursor.getLongOrDefault(modTimeIndex).toString(),
                            )
                        )
                    }
                }
            } ?: emptyList()
        }.getOrElse {
            emptyList()
        }
    }

    private fun ensureLegacyStoragePermissionIfNeeded() {
        val activity = currentActivity ?: return

        val (required, requestCode) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            ) to 1101
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE) to 1100
        }

        val allGranted = required.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) return

        activity.runOnUiThread {
            runCatching {
                activity.requestPermissions(required.toTypedArray(), requestCode)
            }
        }
    }

    private fun normalizeLegacyLocalPath(path: String): String {
        if (path.startsWith("file://", ignoreCase = true)) {
            return Uri.parse(path).path ?: path
        }

        if (path.startsWith("/tree/primary:", ignoreCase = true)) {
            val relative = Uri.decode(path.removePrefix("/tree/primary:")).trimStart('/')
            return buildString {
                append(Environment.getExternalStorageDirectory().path)
                if (relative.isNotEmpty()) {
                    append("/")
                    append(relative)
                }
            }
        }

        return path
    }

    private fun Uri.toTreeUriIfNeeded(): Uri {
        if (pathSegments.contains("tree")) return this

        val authority = authority ?: return this
        val documentId = runCatching { DocumentsContract.getDocumentId(this) }.getOrNull() ?: return this
        return DocumentsContract.buildTreeDocumentUri(authority, documentId)
    }

    private fun persistUriPermissionIfPossible(uri: Uri) {
        val readWriteFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        val resolver = AndroidApp.contentResolver
        val persisted = runCatching {
            resolver.takePersistableUriPermission(uri, readWriteFlags)
        }.isSuccess

        if (!persisted) {
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (index >= 0 && !isNull(index)) getString(index) else null

    private fun Cursor.getLongOrDefault(index: Int, default: Long = 0L): Long =
        if (index >= 0 && !isNull(index)) getLong(index) else default

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
actual fun randomUUID(): String = java.util.UUID.randomUUID().toString()
actual fun getScreenFeature(): ScreenFeature = AndroidScreenFeature(currentActivity!!)
