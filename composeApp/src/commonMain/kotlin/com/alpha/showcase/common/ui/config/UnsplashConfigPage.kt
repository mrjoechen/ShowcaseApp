package com.alpha.showcase.common.ui.config

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.alpha.showcase.api.unsplash.Topic
import com.alpha.showcase.api.unsplash.UnsplashOrientation
import com.alpha.showcase.common.networkfile.storage.remote.UNSPLASH
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.repo.ExternalImageApiKeyEdit
import com.alpha.showcase.common.repo.Types
import com.alpha.showcase.common.repo.UnSplashSourceType
import com.alpha.showcase.common.repo.UnsplashConfigDraft
import com.alpha.showcase.common.repo.createUnsplashApi
import com.alpha.showcase.common.repo.shouldRequestUnsplashApiKey
import com.alpha.showcase.common.repo.supportsOrientation
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.ui.view.LargeDropdownMenu
import com.alpha.showcase.common.ui.view.EXISTING_PASSWORD_PLACEHOLDER
import com.alpha.showcase.common.ui.view.PasswordInput
import com.alpha.showcase.common.utils.ToastUtil
import com.alpha.showcase.common.utils.decodeName
import com.alpha.showcase.common.utils.encodeName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.choose_type
import showcaseapp.composeapp.generated.resources.collection_id
import showcaseapp.composeapp.generated.resources.ic_unsplash
import showcaseapp.composeapp.generated.resources.loading
import showcaseapp.composeapp.generated.resources.name_is_invalid
import showcaseapp.composeapp.generated.resources.name_require_hint
import showcaseapp.composeapp.generated.resources.please_enter_your_api_key
import showcaseapp.composeapp.generated.resources.save
import showcaseapp.composeapp.generated.resources.test_connection
import showcaseapp.composeapp.generated.resources.topic_id_or_slug
import showcaseapp.composeapp.generated.resources.unsplash_orientation
import showcaseapp.composeapp.generated.resources.unsplash_orientation_all
import showcaseapp.composeapp.generated.resources.unsplash_orientation_landscape
import showcaseapp.composeapp.generated.resources.unsplash_orientation_portrait
import showcaseapp.composeapp.generated.resources.unsplash_orientation_squarish
import showcaseapp.composeapp.generated.resources.unsplash_api_key
import showcaseapp.composeapp.generated.resources.userName

@Composable
fun UnsplashConfigPage(
    unsplashSource: UnSplashSource? = null,
    onTestClick: suspend (UnSplashSource) -> Result<Any>?,
    onSaveClick: suspend (UnSplashSource) -> Unit
) {
    val editMode = unsplashSource != null
    val existingStoredApiKey = unsplashSource?.apiKey
    val userApiKeyRequired = shouldRequestUnsplashApiKey()
    var checkingState by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var selectedTypeIndex by remember {
        mutableIntStateOf(
            Types.indexOfFirst { it.type == unsplashSource?.photoType }.coerceAtLeast(0)
        )
    }
    var name by rememberSaveable {
        mutableStateOf(unsplashSource?.name?.decodeName().orEmpty())
    }
    var userName by rememberSaveable {
        mutableStateOf(unsplashSource?.user.orEmpty())
    }
    var collectionId by rememberSaveable {
        mutableStateOf(unsplashSource?.collectionId.orEmpty())
    }
    var topicId by rememberSaveable {
        mutableStateOf(unsplashSource?.topic.orEmpty())
    }
    var apiKey by remember(existingStoredApiKey) { mutableStateOf("") }
    var apiKeyChanged by rememberSaveable {
        mutableStateOf(existingStoredApiKey.isNullOrBlank())
    }
    var apiKeyVisible by rememberSaveable { mutableStateOf(false) }

    val orientations = UnsplashOrientation.entries
    var selectedOrientationIndex by rememberSaveable {
        mutableIntStateOf(
            orientations.indexOf(
                UnsplashOrientation.fromStoredValue(unsplashSource?.orientation)
            ).coerceAtLeast(0)
        )
    }
    var topics by remember { mutableStateOf<List<Topic>>(emptyList()) }
    var topicsLoading by remember { mutableStateOf(false) }

    val selectedType = Types[selectedTypeIndex]
    val apiKeyEdit = ExternalImageApiKeyEdit(
        input = apiKey,
        existingStoredValue = existingStoredApiKey,
        changed = apiKeyChanged,
    )
    val orientationLabels = mapOf(
        UnsplashOrientation.All to stringResource(Res.string.unsplash_orientation_all),
        UnsplashOrientation.Landscape to stringResource(Res.string.unsplash_orientation_landscape),
        UnsplashOrientation.Portrait to stringResource(Res.string.unsplash_orientation_portrait),
        UnsplashOrientation.Squarish to stringResource(Res.string.unsplash_orientation_squarish)
    )

    val nameInvalidText = stringResource(Res.string.name_is_invalid)
    val apiKeyRequiredText = stringResource(Res.string.please_enter_your_api_key)

    LaunchedEffect(selectedType, apiKey, apiKeyChanged) {
        if (selectedType == UnSplashSourceType.TopicsPhotos) {
            if (userApiKeyRequired && apiKeyEdit.isMissing) {
                topics = emptyList()
                topicsLoading = false
                return@LaunchedEffect
            }
            topicsLoading = true
            try {
                if (userApiKeyRequired && !apiKeyEdit.isLocked) {
                    delay(API_KEY_DEBOUNCE_MILLIS)
                }
                loadRemoteOptions {
                    val resolvedApiKey = if (userApiKeyRequired) {
                        apiKeyEdit.valueForRequest { RConfig.decryptAsync(it) }
                    } else {
                        null
                    }
                    val api = createUnsplashApi(resolvedApiKey)
                    loadAllRemoteOptions { page ->
                        val pageItems = api.getTopics(
                            page = page,
                            perPage = MAX_TOPIC_OPTIONS,
                        )
                        RemoteOptionsPage(
                            items = pageItems,
                            hasMore = pageItems.size == MAX_TOPIC_OPTIONS,
                        )
                    }
                }.onSuccess {
                    topics = it
                }.onFailure {
                    topics = emptyList()
                    ToastUtil.error(it.message ?: "Failed to load Unsplash topics")
                }
            } finally {
                topicsLoading = false
            }
        } else {
            topicsLoading = false
        }
    }

    fun buildSource(): UnSplashSource {
        return UnsplashConfigDraft(
            name = name.encodeName(),
            photoType = selectedType,
            user = userName,
            collectionId = collectionId,
            topic = topicId,
            orientation = orientations[selectedOrientationIndex].storedValue,
            apiKeyEdit = apiKeyEdit,
            storeApiKey = userApiKeyRequired,
        ).toSource { it }
    }

    fun isValid(): Boolean {
        return when {
            name.isBlank() -> {
                ToastUtil.error(nameInvalidText)
                false
            }

            userApiKeyRequired && apiKeyEdit.isMissing -> {
                ToastUtil.error(apiKeyRequiredText)
                false
            }

            else -> true
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
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
            onValueChange = { name = it },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            singleLine = true,
            label = { Text(stringResource(Res.string.name_require_hint)) },
            modifier = Modifier.focusRequester(focusRequester)
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (userApiKeyRequired) {
            PasswordInput(
                password = if (apiKeyEdit.isLocked) EXISTING_PASSWORD_PLACEHOLDER else apiKey,
                passwordVisible = apiKeyVisible,
                editMode = editMode,
                readOnly = apiKeyEdit.isLocked,
                label = stringResource(Res.string.unsplash_api_key),
                onPasswordChange = { value ->
                    if (apiKeyEdit.isLocked) {
                        apiKeyChanged = true
                        apiKey = ""
                        apiKeyVisible = false
                    } else {
                        apiKeyChanged = true
                        apiKey = value.trim()
                    }
                },
                onPasswordVisibleChanged = { apiKeyVisible = it },
            )
            ApiTokenHelp(ApiTokenProvider.Unsplash)
        }

        LargeDropdownMenu(
            label = stringResource(Res.string.choose_type),
            items = Types.map { stringResource(it.titleRes) },
            selectedIndex = selectedTypeIndex,
            onItemSelected = { index, _ -> selectedTypeIndex = index }
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (selectedType.supportsOrientation()) {
            LargeDropdownMenu(
                label = stringResource(Res.string.unsplash_orientation),
                items = orientations,
                selectedIndex = selectedOrientationIndex,
                onItemSelected = { index, _ -> selectedOrientationIndex = index },
                selectedItemToString = { orientationLabels.getValue(it) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        when (selectedType) {
            UnSplashSourceType.UsersPhotos,
            UnSplashSourceType.UsersLiked -> {
                OutlinedTextField(
                    shape = RoundedCornerShape(Dimen.textFiledCorners),
                    value = userName,
                    onValueChange = { userName = it.trim() },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.userName)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )
                )
            }

            UnSplashSourceType.Collections -> {
                OutlinedTextField(
                    shape = RoundedCornerShape(Dimen.textFiledCorners),
                    value = collectionId,
                    onValueChange = { collectionId = it.trim() },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.collection_id)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )
                )
            }

            UnSplashSourceType.TopicsPhotos -> {
                when {
                    topicsLoading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Dimen.spaceL),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = stringResource(Res.string.loading),
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }

                    topics.isNotEmpty() -> {
                        LargeDropdownMenu(
                            label = stringResource(Res.string.topic_id_or_slug),
                            items = topics,
                            selectedIndex = topics.indexOfFirst {
                                it.id == topicId || it.slug == topicId
                            },
                            onItemSelected = { _, item -> topicId = item.slug },
                            selectedItemToString = { it.title }
                        )
                    }

                    else -> {
                        OutlinedTextField(
                            shape = RoundedCornerShape(Dimen.textFiledCorners),
                            value = topicId,
                            onValueChange = { topicId = it.trim() },
                            singleLine = true,
                            label = { Text(stringResource(Res.string.topic_id_or_slug)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            )
                        )
                    }
                }
            }

            else -> Unit
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row {
            ElevatedButton(
                onClick = {
                    if (!checkingState && isValid()) {
                        scope.launch {
                            checkingState = true
                            try {
                                onTestClick(buildSource())
                            } finally {
                                checkingState = false
                            }
                        }
                    }
                },
                modifier = Modifier.padding(10.dp)
            ) {
                if (checkingState) {
                    Box {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(5.dp).size(Dimen.spaceL),
                            strokeWidth = 2.dp
                        )
                    }
                }
                Text(text = stringResource(Res.string.test_connection))
            }

            ElevatedButton(
                onClick = {
                    if (isValid()) {
                        scope.launch { onSaveClick(buildSource()) }
                    }
                },
                modifier = Modifier.padding(10.dp)
            ) {
                Text(text = stringResource(Res.string.save), maxLines = 1)
            }
        }
    }

    if (unsplashSource == null) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

private const val MAX_TOPIC_OPTIONS = 30
private const val API_KEY_DEBOUNCE_MILLIS = 600L
