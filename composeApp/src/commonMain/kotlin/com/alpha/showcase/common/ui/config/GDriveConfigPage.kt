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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alpha.X
import com.alpha.networkfile.model.NetworkFile
import com.alpha.networkfile.storage.GOOGLE_DRIVE
import com.alpha.networkfile.storage.TYPE_GOOGLE_DRIVE
import com.alpha.networkfile.storage.drive.GoogleDrive
import com.alpha.networkfile.storage.getType
import com.alpha.networkfile.storage.remote.OAuthRcloneApi
import com.alpha.showcase.common.ui.theme.Dimen
import com.alpha.showcase.common.ui.view.HintText
import com.alpha.showcase.common.utils.Log
import com.alpha.showcase.common.utils.ToastUtil
import com.alpha.showcase.common.utils.checkName
import com.alpha.showcase.common.utils.checkPath
import com.alpha.showcase.common.utils.decodeName
import com.alpha.showcase.common.utils.encodeName
import com.alpha.showcase.common.ui.view.SelectPathDropdown
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.error
import showcaseapp.composeapp.generated.resources.ic_google_drive
import showcaseapp.composeapp.generated.resources.link_to
import showcaseapp.composeapp.generated.resources.save
import showcaseapp.composeapp.generated.resources.source
import showcaseapp.composeapp.generated.resources.source_name
import showcaseapp.composeapp.generated.resources.test_connection

@Composable
fun GDriveConfigPage(
    googleDrive: GoogleDrive? = null,
    onTestClick: suspend (GoogleDrive) -> Result<Any>?,
    onSaveClick: suspend (GoogleDrive) -> Unit,
    onLinkClick: (suspend (OAuthRcloneApi) -> OAuthRcloneApi?)? = null,
    onSelectPath: (suspend (GoogleDrive, String) -> Result<Any>?)? = null
) {

    var name by rememberSaveable(key = "name") {
        mutableStateOf(googleDrive?.name?.decodeName() ?: "")
    }

    var path by rememberSaveable(key = "path") {
        mutableStateOf(googleDrive?.path ?: "")
    }

    var nameValid by rememberSaveable(key = "nameValid") { mutableStateOf(true) }
    var pathValid by rememberSaveable(key = "pathValid") { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val label =
        "${getType(TYPE_GOOGLE_DRIVE).typeName} ${stringResource(Res.string.source)}"
    val focusRequester = remember { FocusRequester() }

    var resultGDrive by remember {
        mutableStateOf(
            googleDrive
        )
    }

    val editMode = googleDrive != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {


        Column(
            modifier = Modifier
                .padding(Dimen.spaceM),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Icon(
                painter = painterResource(Res.drawable.ic_google_drive),
                contentDescription = GOOGLE_DRIVE.typeName,
                tint = Color.Unspecified
            )
            Text(
                text = GOOGLE_DRIVE.typeName,
                style = MaterialTheme.typography.bodySmall.merge(),
                modifier = Modifier.padding(Dimen.spaceM),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            modifier = Modifier.focusRequester(focusRequester),
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

        SelectPathDropdown(resultGDrive,
            initPathList = { _, resultPath ->
                onSelectPath?.invoke(
                    resultGDrive as GoogleDrive,
                    resultPath
                ) as Result<List<NetworkFile>>
            }) { _, resultPath ->
            path = resultPath
            onSelectPath?.invoke(resultGDrive!!, resultPath) as Result<List<NetworkFile>>
        }

//        if (BuildConfig.DEBUG) {
//            Spacer(modifier = Modifier.height(16.dp))
//
//            OutlinedTextField(
//                label = {
//                    Text(
//                        text = stringResource(R.string.path),
//                        style = TextStyle(fontWeight = FontWeight.Bold)
//                    )
//                },
//                value = path,
//                onValueChange = {
//                    path = it
//                    pathValid = context.checkPath(it)
//                },
//                keyboardOptions = KeyboardOptions(
//                    keyboardType = KeyboardType.Text,
//                    imeAction = ImeAction.Done
//                ),
//                placeholder = { HintText(text = "/") },
//                singleLine = true,
//                isError = !pathValid
//            )
//        }
        Spacer(modifier = Modifier.height(16.dp))

        fun checkAndFix(): Boolean {
            nameValid = checkName(name, true) {
                pathValid = checkPath(path, true)
            }
            return nameValid && pathValid
        }

        var checkingState by remember { mutableStateOf(false) }

        var linkingState by remember { mutableStateOf(false) }


        ElevatedButton(onClick = {
            scope.launch {
                if (checkAndFix()) {
                    val drive = GoogleDrive(
                        name = name.encodeName(),
                        path = path,
                        cid = X.GCP_CLIENT_ID,
                        sid = X.GCP_SECRET
                    )
                    linkingState = true
                    val oAuthRcloneApi = onLinkClick?.invoke(drive)
                    (oAuthRcloneApi as GoogleDrive?)?.apply {
                        resultGDrive = this
                        val result = onTestClick.invoke(this)
                        result?.onSuccess {
                            if (it is List<*>) {
                                (it as List<NetworkFile>).apply {
                                    Log.d("OAuth GDrive onSuccess $it")
                                }
                            }

                        }
                    }
                    linkingState = false
                }
            }
        }, modifier = Modifier.padding(10.dp)) {

            if (linkingState) {
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
            Text(
                text = stringResource(Res.string.link_to) + " " + GOOGLE_DRIVE.typeName,
                maxLines = 1
            )
        }


        Row {
            ElevatedButton(onClick = {
                if (checkAndFix() && !checkingState) {
                    scope.launch {
                        checkingState = true
                        resultGDrive?.apply {
                            onTestClick.invoke(this)
                        }?: kotlin.run {
                            Log.d("Result GDrive is null")
                            ToastUtil.error(Res.string.error)
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
                Text(text =  stringResource(Res.string.test_connection))
            }

            ElevatedButton(onClick = {
                scope.launch {
                    if (checkAndFix()) {
                        resultGDrive?.apply {
                            val drive = GoogleDrive(
                                name = name.encodeName(),
                                path = path,
                                cid = X.GCP_CLIENT_ID,
                                sid = X.GCP_SECRET,
                                token = token,
                                folderId = folderId)
                            onSaveClick.invoke(drive)
                        }?: kotlin.run {
                            Log.d("Result GDrive is null")
                            ToastUtil.error(Res.string.error)
                        }
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
fun PreviewGDriveConfig() {
    GDriveConfigPage(onTestClick = { null }, onSaveClick = {})
}