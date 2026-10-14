package com.alpha.showcase.common.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.runtime.*
import com.alpha.showcase.common.ui.dialog.ConsentDialog
import com.alpha.showcase.common.ui.vm.UiState
import com.alpha.showcase.common.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*

/**
 * Asks once per device whether to share usage and crash data. Until the user answers, the
 * preference has no current consent, so no optional analytics, feedback or crash data is sent.
 */
@Composable
internal fun AnonymousUsageConsentHost(enabled: Boolean = true) {
    val state by SettingsViewModel.generalPreferenceFlow.collectAsState()
    val preference = (state as? UiState.Content)?.data ?: return
    if (!enabled || !preference.needsAnonymousUsageDecision) return
    var answering by remember { mutableStateOf(false) }
    fun answer(allowed: Boolean) {
        if (answering) return
        answering = true
        // The write and any collector start-up belong to the view model, not to this dialog's scope.
        SettingsViewModel.viewModelScope.launch {
            try {
                SettingsViewModel.updatePreference(preference.decideAnonymousUsage(allowed), preference)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The question stays open; collection remains off until an answer is stored.
                Log.w("UsageConsent", "Failed to store the usage consent answer")
            } finally {
                answering = false
            }
        }
    }
    ConsentDialog(
        icon = Icons.Outlined.Analytics,
        title = stringResource(Res.string.usage_consent_title),
        message = stringResource(Res.string.usage_consent_message),
        agreeLabel = stringResource(Res.string.usage_consent_allow),
        declineLabel = stringResource(Res.string.usage_consent_deny),
        onAgree = { answer(true) },
        onDecline = { answer(false) },
        enabled = !answering,
    )
}
