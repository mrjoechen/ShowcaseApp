import com.alpha.showcase.api.unsplash.UnsplashApi
import com.alpha.showcase.api.unsplash.UnsplashOrientation
import com.alpha.showcase.api.unsplash.applyUnsplashApiToken
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UnsplashApiTest {
	@Test
	fun noArgConstructorRemainsSourceCompatible() {
		val factory: () -> UnsplashApi = ::UnsplashApi
		assertNotNull(factory)
	}

	@Test
	fun orientationQueryValues() {
		assertEquals(null, UnsplashOrientation.All.queryValue)
		assertEquals("landscape", UnsplashOrientation.Landscape.queryValue)
		assertEquals("portrait", UnsplashOrientation.Portrait.queryValue)
		assertEquals("squarish", UnsplashOrientation.Squarish.queryValue)
	}

	@Test
	fun configuredApiTokenUsesUnsplashAuthorizationScheme() {
		val headers = HeadersBuilder()

		headers.applyUnsplashApiToken("configured-token")

		assertEquals("Client-ID configured-token", headers[HttpHeaders.Authorization])
	}
}
