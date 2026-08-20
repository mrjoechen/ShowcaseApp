package com.alpha.showcase.common.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import com.alpha.showcase.common.networkfile.storage.remote.S3_DEFAULT_REGION
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.ui.view.EXISTING_PASSWORD_PLACEHOLDER
import com.alpha.showcase.common.ui.view.PasswordInput
import com.alpha.showcase.common.utils.checkName
import com.alpha.showcase.common.utils.decodeName
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.save
import showcaseapp.composeapp.generated.resources.s3_access_key
import showcaseapp.composeapp.generated.resources.s3_access_key_hint
import showcaseapp.composeapp.generated.resources.s3_bucket
import showcaseapp.composeapp.generated.resources.s3_bucket_hint
import showcaseapp.composeapp.generated.resources.s3_endpoint
import showcaseapp.composeapp.generated.resources.s3_endpoint_hint
import showcaseapp.composeapp.generated.resources.s3_prefix
import showcaseapp.composeapp.generated.resources.s3_prefix_hint
import showcaseapp.composeapp.generated.resources.s3_region
import showcaseapp.composeapp.generated.resources.s3_secret_key
import showcaseapp.composeapp.generated.resources.s3_use_ssl
import showcaseapp.composeapp.generated.resources.source_name
import showcaseapp.composeapp.generated.resources.test_connection

@Composable
fun S3ConfigPage(
    s3Source: S3Source? = null,
    onTestClick: suspend (S3Source) -> Result<Any>?,
    onSaveClick: suspend (S3Source) -> Unit,
) {
    val editMode = s3Source != null
    val existingEncryptedSecretKey = s3Source?.secretKey
    var name by rememberSaveable { mutableStateOf(s3Source?.name?.decodeName().orEmpty()) }
    var endpoint by rememberSaveable { mutableStateOf(s3Source?.endpoint.orEmpty()) }
    var accessKey by rememberSaveable { mutableStateOf(s3Source?.accessKey.orEmpty()) }
    var secretKey by rememberSaveable { mutableStateOf("") }
    var secretKeyLocked by rememberSaveable {
        mutableStateOf(editMode && !existingEncryptedSecretKey.isNullOrBlank())
    }
    var secretKeyChanged by rememberSaveable { mutableStateOf(!secretKeyLocked) }
    var secretKeyVisible by rememberSaveable { mutableStateOf(false) }
    var bucket by rememberSaveable { mutableStateOf(s3Source?.bucket.orEmpty()) }
    var region by rememberSaveable { mutableStateOf(s3Source?.region ?: S3_DEFAULT_REGION) }
    var prefix by rememberSaveable { mutableStateOf(s3Source?.prefix.orEmpty()) }
    var useSSL by rememberSaveable { mutableStateOf(s3Source?.useSSL ?: true) }
    var nameValid by rememberSaveable { mutableStateOf(true) }
    var endpointValid by rememberSaveable { mutableStateOf(true) }
    var accessKeyValid by rememberSaveable { mutableStateOf(true) }
    var secretKeyValid by rememberSaveable { mutableStateOf(true) }
    var bucketValid by rememberSaveable { mutableStateOf(true) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    fun buildSource(): S3Source? {
        nameValid = checkName(name)
        endpointValid = isValidS3Endpoint(endpoint)
        accessKeyValid = accessKey.isNotBlank()
        secretKeyValid = if (secretKeyChanged) secretKey.isNotBlank() else !existingEncryptedSecretKey.isNullOrBlank()
        bucketValid = bucket.isNotBlank()
        if (!nameValid || !endpointValid || !accessKeyValid || !secretKeyValid || !bucketValid) return null
        return S3ConfigDraft(
            name = name,
            endpoint = endpoint,
            accessKey = accessKey,
            secretKey = secretKey,
            existingEncryptedSecretKey = existingEncryptedSecretKey,
            secretKeyChanged = secretKeyChanged,
            bucket = bucket,
            region = region,
            prefix = prefix,
            useSSL = useSSL,
        ).toSource(RConfig.encrypt)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            value = name,
            onValueChange = { name = it; nameValid = checkName(it) },
            label = { Text(stringResource(Res.string.source_name)) },
            isError = !nameValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            value = endpoint,
            onValueChange = { endpoint = it.trim(); endpointValid = isValidS3Endpoint(it) },
            label = { Text(stringResource(Res.string.s3_endpoint)) },
            placeholder = { Text(stringResource(Res.string.s3_endpoint_hint)) },
            isError = !endpointValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            value = accessKey,
            onValueChange = { accessKey = it.trim(); accessKeyValid = it.isNotBlank() },
            label = { Text(stringResource(Res.string.s3_access_key)) },
            placeholder = { Text(stringResource(Res.string.s3_access_key_hint)) },
            isError = !accessKeyValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(16.dp))
        PasswordInput(
            modifier = Modifier.fillMaxWidth(),
            password = if (secretKeyLocked) EXISTING_PASSWORD_PLACEHOLDER else secretKey,
            passwordVisible = secretKeyVisible,
            editMode = editMode,
            readOnly = secretKeyLocked,
            label = stringResource(Res.string.s3_secret_key),
            isError = !secretKeyValid,
            onPasswordChange = { value ->
                if (secretKeyLocked) {
                    secretKeyLocked = false
                    secretKeyChanged = true
                    secretKey = ""
                    secretKeyVisible = false
                } else {
                    secretKeyChanged = true
                    secretKey = value
                    secretKeyValid = value.isNotBlank()
                }
            },
            onPasswordVisibleChanged = { secretKeyVisible = it },
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            value = bucket,
            onValueChange = { bucket = it.trim(); bucketValid = it.isNotBlank() },
            label = { Text(stringResource(Res.string.s3_bucket)) },
            placeholder = { Text(stringResource(Res.string.s3_bucket_hint)) },
            isError = !bucketValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            value = region,
            onValueChange = { region = it.trim() },
            label = { Text(stringResource(Res.string.s3_region)) },
            placeholder = { Text(S3_DEFAULT_REGION) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            value = prefix,
            onValueChange = { prefix = it.trim() },
            label = { Text(stringResource(Res.string.s3_prefix)) },
            placeholder = { Text(stringResource(Res.string.s3_prefix_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.s3_use_ssl), modifier = Modifier.padding(horizontal = 16.dp))
            Switch(
                checked = useSSL,
                onCheckedChange = { useSSL = it },
                thumbContent = {
                    if (useSSL) {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize))
                    }
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ElevatedButton(
                onClick = {
                    if (!checking) buildSource()?.let { source ->
                        scope.launch {
                            checking = true
                            try {
                                onTestClick(source)
                            } finally {
                                checking = false
                            }
                        }
                    }
                },
            ) {
                if (checking) {
                    Box { CircularProgressIndicator(Modifier.padding(5.dp).size(Dimen.spaceL), strokeWidth = 2.dp) }
                }
                Text(stringResource(Res.string.test_connection))
            }
            ElevatedButton(onClick = { buildSource()?.let { source -> scope.launch { onSaveClick(source) } } }) {
                Text(stringResource(Res.string.save), maxLines = 1)
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (!editMode) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}
