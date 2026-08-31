import com.alpha.showcase.api.pexels.PexelsApi
import com.alpha.showcase.api.pexels.applyPexelsApiKey
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PexelsApiTest {
	@Test
	fun noArgConstructorRemainsSourceCompatible() {
		val factory: () -> PexelsApi = ::PexelsApi
		assertNotNull(factory)
	}

	@Test
	fun configuredApiKeyIsUsedAsAuthorizationHeader() {
		val headers = HeadersBuilder()

		headers.applyPexelsApiKey("configured-key")

		assertEquals("configured-key", headers[HttpHeaders.Authorization])
	}
}
