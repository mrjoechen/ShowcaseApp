import com.alpha.showcase.api.unsplash.UnsplashApi
import com.alpha.showcase.api.unsplash.UnsplashOrientation
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UnsplashApiTest {
	@Test
	fun orientationQueryValues() {
		assertEquals(null, UnsplashOrientation.All.queryValue)
		assertEquals("landscape", UnsplashOrientation.Landscape.queryValue)
		assertEquals("portrait", UnsplashOrientation.Portrait.queryValue)
		assertEquals("squarish", UnsplashOrientation.Squarish.queryValue)
	}

	@Test
	fun invalidApiKey() = runTest {
		val api = UnsplashApi("")
		assertFailsWith<ClientRequestException> {
			api.getUserLikes("chenqiao")
		}
	}
	@Test
	fun feedPhotos() = runTest {
		val api = UnsplashApi()
		val photos = api.getFeedPhotos()
		println(photos)
		assertTrue { photos.isNotEmpty() }
	}

	@Test
	fun usersPhotos() = runTest {
		val api = UnsplashApi()
		val photos = api.getUserPhotos("chenqiao")
		println(photos)
		assertTrue { photos.isNotEmpty() }
	}

	@Test
	fun usersLike() = runTest {
		val api = UnsplashApi()
		val likes = api.getUserLikes("chenqiao")
		println(likes)
		assertTrue { likes.isNotEmpty() }
	}

	@Test
	fun usersCollections() = runTest {
		val api = UnsplashApi()
		val collections = api.getUserCollections("chenqiao")
		println(collections)
		assertTrue { collections.isNotEmpty() }
	}


	@Test
	fun collectionPhotos() = runTest {
		val api = UnsplashApi()
		val collections = api.getCollectionPhotos("UnRDP57gf9Y")
		println(collections)
		assertTrue { collections.isNotEmpty() }
	}


	@Test
	fun topicPhotos() = runTest {
		val api = UnsplashApi()
		val collections = api.getTopicPhotos("wallpapers")
		println(collections)
		assertTrue { collections.isNotEmpty() }
	}

	@Test
	fun randomPhoto() = runTest {
		val api = UnsplashApi()
		val collections = api.getRandomPhotos(collections = "8921087", "", "jfdelp", "", count = 1)
		println(collections)
		assertTrue { collections.size == 1 }
	}
}
