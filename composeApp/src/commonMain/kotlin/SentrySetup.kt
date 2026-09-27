import com.alpha.showcase.common.SENTRY_DSN
import com.alpha.showcase.common.ui.settings.SettingPreferenceRepo
import com.alpha.showcase.common.utils.SentryTestResult
import com.alpha.showcase.common.utils.captureSentryTestEvent
import com.alpha.showcase.common.utils.SentryLifecycleController
import com.alpha.showcase.common.gitHash
import com.alpha.showcase.common.versionCode
import com.alpha.showcase.common.versionName
import io.sentry.kotlin.multiplatform.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun initializeSentry() {
    Sentry.init { options ->
        options.dsn = SENTRY_DSN
        options.debug = isDebug
        options.sendDefaultPii = false
        options.maxBreadcrumbs = 0
        options.attachScreenshot = false
        options.attachViewHierarchy = false
        options.enableCaptureFailedRequests = false
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

/** Tests the existing SDK session; deliberately does not initialize it or change consent. */
suspend fun testCaptureError(): SentryTestResult {
    try {
        return captureSentryTestEvent(
            hasConsent = SettingPreferenceRepo().getPreference().hasAnonymousUsageConsent,
            isSupported = !isWeb(), // KMP 0.24.0 ships no-op JS and Wasm implementations.
            isSdkEnabled = { Sentry.isEnabled() },
            capture = { error ->
                Sentry.captureException(error) { scope ->
                    scope.setTag("showcase.test", "true")
                    scope.setTag("showcase.test.kind", "handled_exception")
                    scope.setTag("showcase.platform", getPlatform().platform.platformName)
                    scope.setTag("showcase.version", "$versionName.$versionCode.$gitHash")
                    scope.setTag("showcase.build", if (isDebug) "debug" else "release")
                }.toString()
            },
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        return SentryTestResult.Failed
    }
}
