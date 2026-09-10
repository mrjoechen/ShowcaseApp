import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vinceglb.filekit.path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DirectoryPickerInitialLocationTest {
    @Test
    fun localFolderPickerStartsAtStorageRootInsteadOfRestoringLastAccessedStack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = assertNotNull(
            AndroidPlatform.directoryPickerInitialDirectory(),
            "Android must supply an initial location to bypass DocumentsUI's stale last-accessed stack",
        )
        val intent = ActivityResultContracts.OpenDocumentTree()
            .createIntent(context, Uri.parse(directory.path))

        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.action)
        @Suppress("DEPRECATION")
        val initialUri = assertNotNull(intent.getParcelableExtra<Uri>(DocumentsContract.EXTRA_INITIAL_URI))
        assertEquals("content", initialUri.scheme)
        assertEquals("com.android.externalstorage.documents", initialUri.authority)
        assertEquals("primary", DocumentsContract.getRootId(initialUri))
    }
}
