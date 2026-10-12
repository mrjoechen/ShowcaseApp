package com.alpha.showcase.common.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.alpha.showcase.common.ui.focusScaleEffect
import com.alpha.showcase.common.ui.tvScreenFocus
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.confirm
import showcaseapp.composeapp.generated.resources.donate
import showcaseapp.composeapp.generated.resources.donation_qr_description

@Composable
internal actual fun donationUriOpener(): (String) -> Unit {
    var donation by remember { mutableStateOf<ImageBitmap?>(null) }
    donation?.let { image ->
        DonationQrDialog(image, onDismiss = { donation = null })
    }
    return remember {
        { url ->
            // Generate in the click action so the shared error handler can report failures.
            donation = donationQrCode(url)
        }
    }
}

@Composable
private fun DonationQrDialog(image: ImageBitmap, onDismiss: () -> Unit) {
    val confirmInteraction = remember { MutableInteractionSource() }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
        title = {
            Text(
                text = stringResource(Res.string.donate),
                modifier = Modifier.semantics { heading() },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                DonationQrImage(image)
            }
        },
        confirmButton = {
            // Resolve input mode and focus inside the dialog's own Compose scene.
            Box(Modifier.tvScreenFocus()) {
                TextButton(
                    onClick = onDismiss,
                    interactionSource = confirmInteraction,
                    modifier = Modifier
                        .widthIn(min = 64.dp)
                        .heightIn(min = 48.dp)
                        .focusScaleEffect(interactionSource = confirmInteraction),
                ) {
                    Text(stringResource(Res.string.confirm))
                }
            }
        },
    )
}

@Composable
private fun DonationQrImage(image: ImageBitmap) {
    val description = stringResource(Res.string.donation_qr_description)
    Canvas(
        modifier = Modifier.size(176.dp).semantics {
            contentDescription = description
            role = Role.Image
        },
    ) {
        // Core Image supplies one white module; add three without whitening the centering slack.
        val quietZone = 3
        val pixelsPerModule = (size.minDimension / (image.width + quietZone * 2)).toInt().coerceAtLeast(1)
        val imageSize = IntSize(image.width * pixelsPerModule, image.height * pixelsPerModule)
        val imageOffset = IntOffset((size.width.toInt() - imageSize.width) / 2, (size.height.toInt() - imageSize.height) / 2)
        val padding = quietZone * pixelsPerModule.toFloat()
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(imageOffset.x - padding, imageOffset.y - padding),
            size = Size(imageSize.width + padding * 2, imageSize.height + padding * 2),
            cornerRadius = CornerRadius(6.dp.toPx()),
        )
        drawImage(
            image = image,
            dstOffset = imageOffset,
            dstSize = imageSize,
            filterQuality = FilterQuality.None,
        )
    }
}
