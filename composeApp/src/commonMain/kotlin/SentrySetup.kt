import com.alpha.showcase.common.SENTRY_DSN
import com.alpha.showcase.common.utils.SentryLifecycleController
import io.sentry.kotlin.multiplatform.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun initializeSentry() {
    Sentry.init { options ->
        options.dsn = SENTRY_DSN
        options.debug = isDebug
    }
}

private val sentryLifecycleController = SentryLifecycleController(
    initializeSdk = ::initializeSentry,
    closeSdk = Sentry::close,
    runOnRequiredThread = { action ->
        withContext(Dispatchers.Main.immediate) {
            action()
        }
    },
)

suspend fun setSentryEnabled(enabled: Boolean) =
    sentryLifecycleController.setEnabled(enabled)

fun testCaptureError() {
    try {
        throw Exception("This is a test Crash.")
    } catch (e: Exception) {
        Sentry.captureException(e)
    }
}
