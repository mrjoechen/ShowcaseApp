package com.alpha.showcase.common.ui.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.ui.DELETE_COLOR

@Composable
fun DeleteDialog(
    deleteName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(onDismissRequest = {
        onDismiss()
    },

        icon = {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete",
                tint = DELETE_COLOR
            )
        },

        title = {
            Text(text = "Delete", color = DELETE_COLOR)
        },

        text = {
            Text(
                buildAnnotatedString {
                    append("Are you sure you want to delete")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(" $deleteName ")
                    }
                    append(" ?")
                },
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },

        confirmButton = {
            TextButton(onClick = {
                onConfirm()
            }) {
                Text(
                    text = "confirm",
                    modifier = Modifier.padding(5.dp),
                    color = DELETE_COLOR
                )
            }
        }, dismissButton = {
            TextButton(onClick = {
                onCancel()
            }) {
                Text(
                    text = "cancel",
                    modifier = Modifier.padding(5.dp)
                )
            }
        }
    )
}