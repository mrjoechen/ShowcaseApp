import com.alpha.showcase.api.pexels.PexelsApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PexelsBuiltInApiTest {
    @Test
    fun curatedPhotos() = runTest {
        val api = PexelsApi()
        val pagination = api.curatedPhotos()
        println(pagination)
        assertTrue { pagination.photos.isNotEmpty() }
    }

    @Test
    fun curatedPhotosWithPerPage() = runTest {
        val api = PexelsApi()
        val pagination = api.curatedPhotos(perPage = 32)
        println(pagination)
        assertTrue { pagination.photos.size == 32 }
    }
}
