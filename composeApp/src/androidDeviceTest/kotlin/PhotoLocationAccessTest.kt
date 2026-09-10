import android.Manifest
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import android.net.Uri
import android.provider.MediaStore
import com.alpha.showcase.common.isPhotoContentUri
import com.alpha.showcase.common.originalPhotoUri
import io.github.vinceglb.filekit.dialogs.FileKitType

class PhotoLocationAccessTest {
    @Test fun deniedLocationAccessLeavesRegularPhotoLoadingAvailable() {
        val denied = object : android.content.ContextWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ) {
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                if (permission == Manifest.permission.ACCESS_MEDIA_LOCATION) PackageManager.PERMISSION_DENIED
                else super.checkPermission(permission, pid, uid)
        }
        assertNull(originalPhotoUri(denied, Uri.parse("content://media/external/images/media/123")))
    }
    @Test fun galleryUsesDocumentPickerToPreserveOriginalAccess() {
        kotlin.test.assertIs<FileKitType.File>(AndroidPlatform.galleryPickerType())
    }

    @Test fun pickerGrantsAreNotRewrittenToMediaStoreItems() {
        assertFalse(isPhotoContentUri(Uri.parse("content://media/picker/0/com.android.providers.media.photopicker/media/123")))
    }

    @Test fun authorizedMediaRequestRequiresOriginal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.ACCESS_MEDIA_LOCATION)
        try {
            val uri = assertNotNull(originalPhotoUri(instrumentation.targetContext,
                Uri.parse("content://media/external/images/media/123")))
            assertEquals(true, MediaStore.getRequireOriginal(uri))
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
    @Test fun applicationDeclaresPhotoLocationAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val permissions = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_PERMISSIONS,
        ).requestedPermissions.orEmpty().toList()
        assertContains(permissions, Manifest.permission.ACCESS_MEDIA_LOCATION)
    }
}
