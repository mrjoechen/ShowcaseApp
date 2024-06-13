import com.alpha.showcase.api.tmdb.TmdbApi
import com.alpha.showcase.api.tmdb.backdropUrl
import com.alpha.showcase.api.tmdb.posterUrl
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TmdbApiTest {
    @Test
    fun invalidApiKey() = runTest {
        val api = TmdbApi("")
        assertFailsWith<ClientRequestException> {
            api.getPopularMovies()
        }
    }

    @Test
    fun getUpcomingMovies() = runTest {
        val api = TmdbApi()
        val movies = api.getUpcomingMovies()
        movies.results.forEach {
            println(it.posterUrl)
        }
        assertTrue { movies.results.isNotEmpty() }
    }

    @Test
    fun getNowPlayingMovies() = runTest {
        val api = TmdbApi()
        val movies = api.getNowPlayingMovies()
        println(movies)
        movies.results.forEach {
            println(it.backdropUrl)
        }
        assertTrue { movies.results.isNotEmpty() }
    }

    @Test
    fun getMoviesImages() = runTest {
        val api = TmdbApi()
        val images = api.getMovieImages(1022789)
        println(images)
        assertTrue { images.posters.isNotEmpty() && images.backdrops.isNotEmpty() }
    }
}