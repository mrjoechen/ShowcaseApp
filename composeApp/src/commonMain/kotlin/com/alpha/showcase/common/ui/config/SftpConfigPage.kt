package com.alpha.showcase.common.ui.config

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.SFTP
import com.alpha.showcase.common.networkfile.storage.TYPE_SFTP
import com.alpha.showcase.common.networkfile.storage.getType
import com.alpha.showcase.common.networkfile.storage.remote.Sftp
import com.alpha.showcase.common.networkfile.util.RConfig
import showcaseapp.composeapp.generated.resources.Res
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.ui.view.HintText
import com.alpha.showcase.common.utils.checkHost
import com.alpha.showcase.common.utils.checkName
import com.alpha.showcase.common.utils.checkPath
import com.alpha.showcase.common.utils.checkPort
import com.alpha.showcase.common.utils.decodeName
import com.alpha.showcase.common.utils.encodeName
import com.alpha.showcase.common.ui.dialog.FilePathSelector
import com.alpha.showcase.common.ui.view.EXISTING_PASSWORD_PLACEHOLDER
import com.alpha.showcase.common.ui.view.PasswordInput
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import showcaseapp.composeapp.generated.resources.host
import showcaseapp.composeapp.generated.resources.my
import showcaseapp.composeapp.generated.resources.port
import showcaseapp.composeapp.generated.resources.save
import showcaseapp.composeapp.generated.resources.source
import showcaseapp.composeapp.generated.resources.source_name
import showcaseapp.composeapp.generated.resources.test_connection
import showcaseapp.composeapp.generated.resources.user

@Composable
fun SftpConfigPage(
    sftp: Sftp? = null,
    onTestClick: suspend (Sftp) -> Result<Any>?,
    onSaveClick: suspend (Sftp) -> Unit,
    onSelectPath: (suspend (Sftp, String) -> Result<Any>?)? = null
) {
    val editMode = sftp != null
    val existingEncryptedPassword = sftp?.passwd
    val hasExistingPassword = !existingEncryptedPassword.isNullOrBlank()


    var name by rememberSaveable(key = "name") {
        mutableStateOf(sftp?.name?.decodeName() ?: "")
    }
    var host by rememberSaveable(key = "host") {
        mutableStateOf(sftp?.host ?: "")
    }
    var port by rememberSaveable(key = "port") {
        mutableStateOf(sftp?.port?.toString() ?: "")
    }
    var username by rememberSaveable(key = "username") {
        mutableStateOf(sftp?.user ?: "")
    }
    var password by rememberSaveable(key = "sftp_password") {
        mutableStateOf("")
    }
    var passwordLocked by rememberSaveable(key = "sftp_password_locked") {
        mutableStateOf(editMode && hasExistingPassword)
    }
    var passwordChanged by rememberSaveable(key = "sftp_password_changed") { mutableStateOf(false) }
    var path by rememberSaveable(key = "path") {
        mutableStateOf(sftp?.path ?: "")
    }

    var passwordVisible by rememberSaveable(key = "sftp_password_visible") { mutableStateOf(false) }
    var nameValid by rememberSaveable(key = "nameValid") { mutableStateOf(true) }
    var hostValid by rememberSaveable(key = "hostValid") { mutableStateOf(true) }
    var portValid by rememberSaveable(key = "portValid") { mutableStateOf(true) }
    var pathValid by rememberSaveable(key = "pathValid") { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val label =
        "${stringResource(Res.string.my)} ${getType(TYPE_SFTP).typeName} ${stringResource(Res.string.source)}"
    val focusRequester = remember { FocusRequester() }
    var resultSftp by remember {
        mutableStateOf(
            sftp
        )
    }
    var openPathDialogSignal by rememberSaveable(key = "sftp_open_path_dialog_signal") {
        mutableStateOf(0)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        OutlinedTextField(
            modifier = Modifier.focusRequester(focusRequester),
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
                nameValid = checkName(it)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            placeholder = { HintText(text = label) },
            singleLine = true,
            isError = !nameValid
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            label = {
                Text(
                    text = stringResource(Res.string.host),
                    style = TextStyle(fontWeight = FontWeight.Bold)
                )
            },
            value = host,
            onValueChange = {
                host = it
                hostValid = checkHost(it)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            placeholder = { HintText("192.168.1.1") },
            singleLine = true,
            isError = !hostValid
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            label = {
                Text(
                    text = stringResource(Res.string.port),
                    style = TextStyle(fontWeight = FontWeight.Bold)
                )
            },
            value = port,
            onValueChange = {
                port = it
                portValid = checkPort(it)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            placeholder = { HintText(text = SFTP.defaultPort.toString()) },
            singleLine = true,
            isError = !portValid
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            label = {
                Text(
                    text = stringResource(Res.string.user),
                    style = TextStyle(fontWeight = FontWeight.Bold)
                )
            },
            value = username,
            onValueChange = { username = it },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            placeholder = { Text(text = "") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        PasswordInput(
            password = if (passwordLocked) EXISTING_PASSWORD_PLACEHOLDER else password,
            passwordVisible = passwordVisible,
            editMode = editMode,
            readOnly = passwordLocked,
            onPasswordChange = {
                if (passwordLocked) {
                    passwordLocked = false
                    passwordChanged = true
                    password = ""
                    passwordVisible = false
                } else if (it != password) {
                    passwordChanged = true
                    password = it
                }
            },
            onPasswordVisibleChanged = {
                passwordVisible = it
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        FilePathSelector(
            fileApi = resultSftp,
            path = path,
            onPathChange = { newPath ->
                path = newPath
                pathValid = checkPath(newPath)
            },
            openDialogSignal = openPathDialogSignal
        )
        Spacer(modifier = Modifier.height(16.dp))

        fun checkAndFix(): Boolean {
            val realPort = port.ifBlank { SFTP.defaultPort.toString() }
            nameValid = checkName(name, true) {
                hostValid = checkHost(host, true) {
                    portValid = checkPort(realPort, true) {
                        pathValid = checkPath(path, true)
                    }
                }
            }
            return nameValid && hostValid && portValid && pathValid
        }

        var checkingState by remember { mutableStateOf(false) }

        Row {
            ElevatedButton(onClick = {
                if (checkAndFix() && !checkingState) {
                    scope.launch {
                        checkingState = true
                        val sftp = Sftp(
                            host = host,
                            port = port.ifBlank { SFTP.defaultPort.toString() }.toInt(),
                            user = username,
                            passwd = if (editMode && !passwordChanged) {
                                existingEncryptedPassword ?: RConfig.encrypt(password)
                            } else {
                                RConfig.encrypt(password)
                            },
                            name = name.encodeName(),
                            path = path
                        )
                        val result = onTestClick.invoke(sftp)
                        result?.onSuccess {
                            resultSftp = sftp
                            openPathDialogSignal++
                        }
                        checkingState = false
                    }
                }
            }, modifier = Modifier.padding(10.dp)) {
                if (checkingState) {
                    Box {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(5.dp)
                                .align(Alignment.Center)
                                .size(Dimen.spaceL),
                            strokeWidth = 2.dp
                        )
                    }
                }
                Text(text = stringResource(Res.string.test_connection))
            }

            ElevatedButton(onClick = {
                scope.launch {
                    if (checkAndFix()) {
                        val sftp = Sftp(
                            host = host,
                            port = port.ifBlank { SFTP.defaultPort.toString() }.toInt(),
                            user = username,
                            passwd = if (editMode && !passwordChanged) {
                                existingEncryptedPassword ?: RConfig.encrypt(password)
                            } else {
                                RConfig.encrypt(password)
                            },
                            name = name.encodeName(),
                            path = path
                        )
                        onSaveClick.invoke(sftp)
                    }
                }
            }, modifier = Modifier.padding(10.dp)) {
                Text(text = stringResource(Res.string.save), maxLines = 1)
            }
        }
    }
    if (!editMode) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

@Preview
@Composable
fun PreviewSFtpConfig() {
    SftpConfigPage(onTestClick = { null }, onSaveClick = {})
}
