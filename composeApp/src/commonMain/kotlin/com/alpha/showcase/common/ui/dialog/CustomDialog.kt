package com.alpha.showcase.common.ui.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.cancel
import showcaseapp.composeapp.generated.resources.confirm
import showcaseapp.composeapp.generated.resources.feedback
import showcaseapp.composeapp.generated.resources.thanks_for_your_feed_back
import showcaseapp.composeapp.generated.resources.email


@Preview
@Composable
fun FeedbackDialog(onFeedback: (String, String) -> Unit = { _: String, _: String -> }, onDismiss: () -> Unit = {}) {

    var feedback by remember {
        mutableStateOf("")
    }

    var email by remember {
        mutableStateOf("")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.feedback)) },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(200.dp, 300.dp)
                    .height(180.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Surface(shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
                    TextField(
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        value = feedback,
                        placeholder = { Text(text = stringResource(Res.string.thanks_for_your_feed_back) + " ...") },
                        onValueChange = {
                            feedback = it
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        value = email,
                        maxLines = 1,
                        placeholder = { Text(text = stringResource(Res.string.email)) },
                        onValueChange = {
                            email = it
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (feedback.isNotBlank()) {
                        onFeedback(feedback, email)
                        onDismiss()
                    }
                }
            ) {
                Text(text = stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                }
            ) {
                Text(text = stringResource(Res.string.cancel))
            }
        }
    )
}