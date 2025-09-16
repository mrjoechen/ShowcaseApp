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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.alpha.showcase.common.networkfile.storage.remote.UNSPLASH
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.repo.Types
import com.alpha.showcase.common.repo.UnSplashSourceType
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.utils.ToastUtil
import com.alpha.showcase.common.utils.decodeName
import com.alpha.showcase.common.utils.encodeName
import com.alpha.showcase.common.ui.view.LargeDropdownMenu
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.choose_type
import showcaseapp.composeapp.generated.resources.collection_id
import showcaseapp.composeapp.generated.resources.ic_unsplash
import showcaseapp.composeapp.generated.resources.name_is_invalid
import showcaseapp.composeapp.generated.resources.name_require_hint
import showcaseapp.composeapp.generated.resources.save
import showcaseapp.composeapp.generated.resources.test_connection
import showcaseapp.composeapp.generated.resources.topic_id_or_slug
import showcaseapp.composeapp.generated.resources.userName

@Composable
fun UnsplashConfigPage(
    unsplashSource: UnSplashSource? = null,
    onTestClick: suspend (UnSplashSource) -> Result<Any>?,
    onSaveClick: suspend (UnSplashSource) -> Unit
) {

    var checkingState by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var selectedTypeIndex by remember {
        mutableIntStateOf(unsplashSource?.photoType?.let {
            Types.indexOfFirst { type -> type.type == it }
        } ?: 0)
    }

    var name by rememberSaveable(key = "name") {
        mutableStateOf(unsplashSource?.name?.decodeName() ?: "")
    }


    var userName by rememberSaveable(key = "userName") {
        mutableStateOf(unsplashSource?.user ?: "")
    }

    val userNameValid by rememberSaveable(key = "userNameValid") {
        mutableStateOf(true)
    }

    var collectionId by rememberSaveable(key = "collectionId") {
        mutableStateOf(unsplashSource?.collectionId ?: "")
    }

    var topicId by rememberSaveable(key = "topicId") {
        mutableStateOf(unsplashSource?.topic ?: "")
    }

    val editMode = unsplashSource != null
    val focusRequester = remember { FocusRequester() }

    Column(
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(16.dp)
    ) {

        Icon(
            modifier = Modifier.size(96.dp),
            painter = painterResource(Res.drawable.ic_unsplash),
            contentDescription = UNSPLASH.typeName
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            value = name,
            onValueChange = {
                name = it
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            singleLine = true,
            label = { Text(stringResource(Res.string.name_require_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
        Spacer(modifier = Modifier.height(16.dp))

        LargeDropdownMenu(
            label = stringResource(Res.string.choose_type),
            items = Types.map { stringResource(it.titleRes) },
            selectedIndex = selectedTypeIndex,
            onItemSelected = { index, _ -> selectedTypeIndex = index }
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (Types[selectedTypeIndex]) {
            UnSplashSourceType.UsersPhotos,
            UnSplashSourceType.UsersLiked -> {
                OutlinedTextField(
                    shape = RoundedCornerShape(Dimen.textFiledCorners),
                    value = userName,
                    onValueChange = {
                        userName = it.trim()
                    },
                    isError = !userNameValid,
                    singleLine = true,
                    label = { Text(stringResource(Res.string.userName)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }

//                UnSplashSourceType.UsersCollection -> {
//                    OutlinedTextField(
//                        value = userName,
//                        onValueChange = {
//                            userName = it.trim()
//                        },
//                        isError = !userNameValid,
//                        label = { Text(stringResource(Res.string.userName)) },
//                        keyboardOptions = KeyboardOptions(
//                            keyboardType = KeyboardType.Text,
//                            imeAction = ImeAction.Next
//                        )
//                    )
//                }

            UnSplashSourceType.Collections -> {
                OutlinedTextField(
                    shape = RoundedCornerShape(Dimen.textFiledCorners),
                    value = collectionId,
                    onValueChange = {
                        collectionId = it.trim()
                    },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.collection_id)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            UnSplashSourceType.TopicsPhotos -> {
                OutlinedTextField(
                    shape = RoundedCornerShape(Dimen.textFiledCorners),
                    value = topicId,
                    onValueChange = {
                        topicId = it.trim()
                    },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.topic_id_or_slug)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }

            else -> {


            }
        }

        Spacer(modifier = Modifier.height(16.dp))



        Row {
            ElevatedButton(onClick = {
                if (!checkingState) {
                    scope.launch {

                        if (name.isEmpty()) {
                            ToastUtil.error(
                                Res.string.name_is_invalid
                            )
                        } else {
                            checkingState = true
                            onTestClick.invoke(
                                UnSplashSource(
                                    name.encodeName(),
                                    Types[selectedTypeIndex].type,
                                    userName,
                                    collectionId,
                                    topicId
                                )
                            )
                            checkingState = false
                        }
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
                if (name.isEmpty()) {
                    ToastUtil.error(Res.string.name_is_invalid)
                } else {
                    scope.launch {
                        onSaveClick.invoke(
                            UnSplashSource(
                                name.encodeName(),
                                Types[selectedTypeIndex].type,
                                userName,
                                collectionId,
                                topicId
                            )
                        )
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
