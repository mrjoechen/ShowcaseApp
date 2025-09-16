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
import com.alpha.showcase.common.X
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.GOOGLE_PHOTOS
import com.alpha.showcase.common.networkfile.storage.TYPE_GOOGLE_PHOTOS
import com.alpha.showcase.common.networkfile.storage.drive.GooglePhotos
import com.alpha.showcase.common.networkfile.storage.getType
import com.alpha.showcase.common.networkfile.storage.remote.OAuthRcloneApi
import com.alpha.showcase.common.theme.Dimen
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
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.error
import showcaseapp.composeapp.generated.resources.ic_google_photos
import showcaseapp.composeapp.generated.resources.link_to
import showcaseapp.composeapp.generated.resources.save
import showcaseapp.composeapp.generated.resources.source
import showcaseapp.composeapp.generated.resources.source_name
import showcaseapp.composeapp.generated.resources.test_connection

@Composable
fun GPhotosConfigPage(
    googlePhotos: GooglePhotos? = null,
    onTestClick: suspend (GooglePhotos) -> Result<Any>?,
    onSaveClick: suspend (GooglePhotos) -> Unit,
    onLinkClick: suspend (OAuthRcloneApi) -> OAuthRcloneApi?,
    onSelectPath: (suspend (GooglePhotos, String) -> Result<Any>?)? = null
) {

    var name by rememberSaveable(key = "name") {
        mutableStateOf(googlePhotos?.name?.decodeName() ?: "")
    }

    var path by rememberSaveable(key = "path") {
        mutableStateOf(googlePhotos?.path ?: "")
    }

    var nameValid by rememberSaveable(key = "nameValid") { mutableStateOf(true) }
    var pathValid by rememberSaveable(key = "pathValid") { mutableStateOf(true) }

    var resultGPhotos by remember {
        mutableStateOf(
            googlePhotos
        )
    }

    val scope = rememberCoroutineScope()
    val label =
        "${getType(TYPE_GOOGLE_PHOTOS).typeName} ${stringResource(Res.string.source)}}"
    val focusRequester = remember { FocusRequester() }

    val editMode = googlePhotos != null
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
                painter = painterResource(Res.drawable.ic_google_photos),
                contentDescription = GOOGLE_PHOTOS.typeName,
                tint = Color.Unspecified
            )
            Text(
                text = GOOGLE_PHOTOS.typeName,
                style = MaterialTheme.typography.bodySmall.merge(),
                modifier = Modifier.padding(Dimen.spaceM),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        SelectPathDropdown(resultGPhotos, filter = {
            it.startsWith("album") || it.startsWith("shared-album") || it.startsWith("feature")
        },
            initPathList = { _, resultPath ->
                onSelectPath?.invoke(
                    resultGPhotos as GooglePhotos,
                    resultPath
                ) as Result<List<NetworkFile>>
            }) { _, resultPath ->
            path = resultPath
            onSelectPath?.invoke(resultGPhotos!!, resultPath) as Result<List<NetworkFile>>
        }
        Spacer(modifier = Modifier.height(16.dp))

        fun checkAndFix(): Boolean {
            nameValid = checkName(name, true) {
                pathValid = checkPath(path, true)
            }
            return nameValid && pathValid
        }

        var checkingState by remember { mutableStateOf(false) }

        ElevatedButton(onClick = {
            scope.launch {
                if (checkAndFix()) {
                    val googlePhotos = GooglePhotos(
                        name = name.encodeName(),
                        cid = X.GCP_CLIENT_ID,
                        sid = X.GCP_SECRET
                    )
                    val oAuthRcloneApi = onLinkClick.invoke(googlePhotos)
                    (oAuthRcloneApi as GooglePhotos?)?.apply {
                        resultGPhotos = this
                        val result = onTestClick.invoke(this)
                        result?.onSuccess {
                            (it as List<NetworkFile>).apply {
                                Log.d("onSuccess $it")
                            }
                        }
                    }
                }
            }
        }, modifier = Modifier.padding(10.dp)) {
            Text(
                text = stringResource(Res.string.link_to) + " " + GOOGLE_PHOTOS.typeName,
                maxLines = 1
            )
        }


        Row {
            ElevatedButton(onClick = {
                if (checkAndFix() && !checkingState) {
                    scope.launch {
                        checkingState = true
                        resultGPhotos?.apply {
                            onTestClick.invoke(this)
                        } ?: kotlin.run {
                            Log.d("Result Google Photos is null")
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
                Text(text = stringResource(Res.string.test_connection))
            }

            ElevatedButton(onClick = {
                scope.launch {
                    if (checkAndFix()) {
                        resultGPhotos?.apply {
                            val gphoto = GooglePhotos(
                                name = name.encodeName(),
                                cid = X.GCP_CLIENT_ID,
                                sid = X.GCP_SECRET,
                                path = path,
                                token = token
                            )
                            onSaveClick.invoke(gphoto)
                        } ?: kotlin.run {
                            Log.d("Result Google Photos is null")
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


@Composable
fun PreviewGPhotoConfig() {
    GPhotosConfigPage(onTestClick = { null }, onSaveClick = {}, onLinkClick = {null})
}