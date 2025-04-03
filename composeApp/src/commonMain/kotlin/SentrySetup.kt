import com.alpha.showcase.common.DEBUG
import com.alpha.showcase.common.SENTRY_DSN
import io.sentry.kotlin.multiplatform.Sentry


fun initializeSentry() {
    Sentry.init { options ->
        options.dsn = SENTRY_DSN
        options.debug = DEBUG
    }
}

fun testCaptureError() {
    try {
        throw Exception("This is a test Crash.")
    } catch (e: Exception) {
        Sentry.captureException(e)
    }
}
