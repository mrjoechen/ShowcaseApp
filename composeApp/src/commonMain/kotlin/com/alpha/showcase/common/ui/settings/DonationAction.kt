package com.alpha.showcase.common.ui.settings

import androidx.compose.runtime.*
import com.alpha.showcase.common.ui.confetti.ConfettiController
import com.alpha.showcase.common.ui.confetti.ConfettiType
import com.alpha.showcase.common.ui.confetti.LocalConfettiTrigger
import showcaseapp.composeapp.generated.resources.donation_open_failed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DONATION_URL = "https://showcase.joechen.space/donate.html"

@Composable
internal fun donationAction(): () -> Unit {
    val openUri = donationUriOpener()
    val scope = rememberCoroutineScope()
    val localTrigger = LocalConfettiTrigger.current
    var pending by remember { mutableStateOf(false) }
    return {
        if (!pending) {
            pending = true
            ConfettiController.trigger(ConfettiType.Celebration)
            localTrigger(ConfettiType.Celebration)
            scope.launch {
                try {
                    delay(900)
                    openUri(DONATION_URL)
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    com.alpha.showcase.common.utils.ToastUtil.error(
                        org.jetbrains.compose.resources.getString(showcaseapp.composeapp.generated.resources.Res.string.donation_open_failed)
                    )
                } finally {
                    pending = false
                }
            }
        }
    }
}

@Composable
internal expect fun donationUriOpener(): (String) -> Unit
