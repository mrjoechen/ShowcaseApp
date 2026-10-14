package com.alpha.showcase.common.ui.dialog

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.DialogProperties
import com.alpha.showcase.common.ui.focusScaleEffect
import com.alpha.showcase.common.ui.tvScreenFocus
import usesRemoteNavigation

/**
 * A choice the user makes explicitly: only [onAgree] grants it, tapping outside does nothing, and
 * back declines. On TV, focus starts on Decline so agreeing takes a deliberate move.
 */
@Composable
internal fun ConsentDialog(
    icon: ImageVector,
    title: String,
    message: String,
    agreeLabel: String,
    declineLabel: String,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    enabled: Boolean = true,
) {
    val remote = usesRemoteNavigation()
    val agreeInteraction = remember { MutableInteractionSource() }
    val declineInteraction = remember { MutableInteractionSource() }
    fun Modifier.remoteFocusScale(source: MutableInteractionSource) =
        if (remote) focusScaleEffect(interactionSource = source) else this
    AlertDialog(
        onDismissRequest = { if (enabled) onDecline() },
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onAgree, enabled = enabled, interactionSource = agreeInteraction,
                modifier = Modifier.remoteFocusScale(agreeInteraction)) {
                Text(agreeLabel)
            }
        },
        dismissButton = {
            // Resolve input mode and focus inside the dialog's own Compose scene.
            Box(Modifier.tvScreenFocus()) {
                TextButton(onClick = onDecline, enabled = enabled, interactionSource = declineInteraction,
                    modifier = Modifier.remoteFocusScale(declineInteraction)) {
                    Text(declineLabel)
                }
            }
        },
    )
}
