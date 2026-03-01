package com.alpha.showcase.common.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.ui.view.HintText
import com.alpha.showcase.common.utils.checkName
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.cancel
import showcaseapp.composeapp.generated.resources.confirm
import showcaseapp.composeapp.generated.resources.gallery_source_name
import showcaseapp.composeapp.generated.resources.source_name

@Composable
fun AddGallerySource(
    onCancelClick: () -> Unit,
    onConfirmClick: (String) -> Unit,
) {
    var name by rememberSaveable {
        mutableStateOf("")
    }
    var nameValid by rememberSaveable {
        mutableStateOf(true)
    }

    AlertDialog(
        dialogPaneDescription = stringResource(Res.string.gallery_source_name),
        onDismissRequest = onCancelClick
    ) {
        Surface(
            shape = androidx.compose.material3.MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    shape = RoundedCornerShape(Dimen.textFiledCorners),
                    label = {
                        Text(
                            text = stringResource(Res.string.source_name),
                            style = TextStyle(fontWeight = FontWeight.Bold)
                        )
                    },
                    value = name,
                    onValueChange = {
                        name = it
                        nameValid = it.checkName(it)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    placeholder = { HintText(text = stringResource(Res.string.gallery_source_name)) },
                    singleLine = true,
                    maxLines = 1,
                    isError = !nameValid
                )

                Spacer(modifier = Modifier.height(Dimen.spaceXL))

                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onCancelClick) {
                        Text(stringResource(Res.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            nameValid = name.checkName(name)
                            if (nameValid) {
                                onConfirmClick(name)
                            }
                        }
                    ) {
                        Text(stringResource(Res.string.confirm))
                    }
                }
            }
        }
    }
}
