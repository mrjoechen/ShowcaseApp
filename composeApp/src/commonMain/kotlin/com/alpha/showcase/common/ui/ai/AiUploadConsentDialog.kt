package com.alpha.showcase.common.ui.ai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.runtime.*
import com.alpha.showcase.common.ai.AiProfile
import com.alpha.showcase.common.ai.AiUploadConsent
import com.alpha.showcase.common.ai.consentHost
import com.alpha.showcase.common.ui.dialog.ConsentDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*

@Composable
internal fun AiUploadConsentDialog(host: String, onAgree: () -> Unit, onDecline: () -> Unit) {
    ConsentDialog(
        icon = Icons.Outlined.PrivacyTip,
        title = stringResource(Res.string.ai_upload_consent_title),
        message = stringResource(Res.string.ai_upload_consent_message, host),
        agreeLabel = stringResource(Res.string.ai_upload_consent_agree),
        declineLabel = stringResource(Res.string.ai_upload_consent_decline),
        onAgree = onAgree,
        onDecline = onDecline,
    )
}

/** Asks for upload consent on behalf of an action that would make a service receive photos. */
@Stable
internal class AiUploadConsentPrompt(private val consent: AiUploadConsent, private val scope: CoroutineScope) {
    private class Pending(val profile: AiProfile, val onFailure: () -> Unit, val onGranted: () -> Unit)

    private var pending by mutableStateOf<Pending?>(null)

    /** Runs [onGranted] now if uploads to [profile] are allowed, otherwise once the user agrees. */
    suspend fun ensure(profile: AiProfile, onFailure: () -> Unit = {}, onGranted: () -> Unit) {
        if (consent.isGranted(profile)) onGranted() else ask(profile, onFailure, onGranted)
    }

    fun ask(profile: AiProfile, onFailure: () -> Unit = {}, onGranted: () -> Unit) {
        pending = Pending(profile, onFailure, onGranted)
    }

    @Composable
    fun Dialog() {
        val request = pending ?: return
        AiUploadConsentDialog(request.profile.consentHost(),
            onAgree = {
                pending = null
                scope.launch {
                    try {
                        consent.grant(request.profile)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        request.onFailure()
                        return@launch
                    }
                    request.onGranted()
                }
            },
            onDecline = { pending = null })
    }
}

@Composable
internal fun rememberAiUploadConsentPrompt(consent: AiUploadConsent): AiUploadConsentPrompt {
    val scope = rememberCoroutineScope()
    return remember(consent, scope) { AiUploadConsentPrompt(consent, scope) }
}
