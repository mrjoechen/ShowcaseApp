import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem

actual suspend fun clearPlatformImageCache() {
    withContext(Dispatchers.Default) {
        runCatching {
            FileSystem.SYSTEM.deleteRecursively(imageCache)
        }.onFailure {
            it.printStackTrace()
        }
    }
}
