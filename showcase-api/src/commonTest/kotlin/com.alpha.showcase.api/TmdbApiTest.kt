import com.alpha.showcase.api.tmdb.TmdbApi
import com.alpha.showcase.api.tmdb.applyTmdbApiToken
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class TmdbApiTest {
    @Test
    fun noArgConstructorRemainsSourceCompatible() {
        val factory: () -> TmdbApi = ::TmdbApi

        assertNotNull(factory)
    }

    @Test
    fun configuredApiTokenUsesBearerAuthorizationScheme() {
        val headers = HeadersBuilder()

        headers.applyTmdbApiToken("configured-token")

        assertEquals("Bearer configured-token", headers[HttpHeaders.Authorization])
    }

    @Test
    fun invalidApiKey() = runTest {
        val api = TmdbApi("")
        assertFailsWith<ClientRequestException> {
            api.getPopularMovies()
        }
    }
}
