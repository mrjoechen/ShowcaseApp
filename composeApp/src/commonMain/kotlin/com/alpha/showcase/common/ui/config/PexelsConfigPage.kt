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
import com.alpha.showcase.api.pexels.PexelsCollection
import com.alpha.showcase.common.networkfile.storage.remote.PEXELS
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.repo.ExternalImageApiKeyEdit
import com.alpha.showcase.common.repo.PEXELS_API_KEY_KEY
import com.alpha.showcase.common.repo.PEXELS_COLLECTION_ID_KEY
import com.alpha.showcase.common.repo.PexelsConfigDraft
import com.alpha.showcase.common.repo.PexelsSourceType
import com.alpha.showcase.common.repo.PexelsTypes
import com.alpha.showcase.common.repo.createPexelsApi
import com.alpha.showcase.common.repo.shouldRequestPexelsApiKey
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
import showcaseapp.composeapp.generated.resources.choose_collection
import showcaseapp.composeapp.generated.resources.choose_type
import showcaseapp.composeapp.generated.resources.collection_id
import showcaseapp.composeapp.generated.resources.ic_pexels
import showcaseapp.composeapp.generated.resources.loading
import showcaseapp.composeapp.generated.resources.name_is_invalid
import showcaseapp.composeapp.generated.resources.name_require_hint
import showcaseapp.composeapp.generated.resources.please_choose_collection
import showcaseapp.composeapp.generated.resources.please_enter_your_api_key
import showcaseapp.composeapp.generated.resources.pexels_api_key
import showcaseapp.composeapp.generated.resources.save
import showcaseapp.composeapp.generated.resources.test_connection

@Composable
fun PexelsConfigPage(
    pexelsSource: PexelsSource? = null,
    onTestClick: suspend (PexelsSource) -> Result<Any>?,
    onSaveClick: suspend (PexelsSource) -> Unit
) {
    val editMode = pexelsSource != null
    val existingStoredApiKey = pexelsSource?.extra?.get(PEXELS_API_KEY_KEY)
    var checkingState by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var selectedTypeIndex by remember {
        mutableIntStateOf(
            PexelsTypes.indexOf(PexelsSourceType.fromStoredType(pexelsSource?.photoType))
                .coerceAtLeast(0)
        )
    }
    var name by rememberSaveable {
        mutableStateOf(pexelsSource?.name?.decodeName().orEmpty())
    }
    var collectionId by rememberSaveable {
        mutableStateOf(pexelsSource?.extra?.get(PEXELS_COLLECTION_ID_KEY).orEmpty())
    }
    var apiKey by remember(existingStoredApiKey) { mutableStateOf("") }
    var apiKeyChanged by rememberSaveable {
        mutableStateOf(existingStoredApiKey.isNullOrBlank())
    }
    var apiKeyVisible by rememberSaveable { mutableStateOf(false) }
    var collections by remember { mutableStateOf<List<PexelsCollection>>(emptyList()) }
    var collectionsLoading by remember { mutableStateOf(false) }

    val selectedType = PexelsTypes[selectedTypeIndex]
    val userApiKeyRequired = shouldRequestPexelsApiKey(selectedType)
    val apiKeyEdit = ExternalImageApiKeyEdit(
        input = apiKey,
        existingStoredValue = existingStoredApiKey,
        changed = apiKeyChanged,
    )
    val nameInvalidText = stringResource(Res.string.name_is_invalid)
    val apiKeyRequiredText = stringResource(Res.string.please_enter_your_api_key)
    val collectionRequiredText = stringResource(Res.string.please_choose_collection)

    LaunchedEffect(selectedType, apiKey, apiKeyChanged) {
        collections = emptyList()
        val shouldLoad = selectedType != PexelsSourceType.FeedPhotos &&
            (!userApiKeyRequired || !apiKeyEdit.isMissing)
        if (!shouldLoad) {
            collectionsLoading = false
            return@LaunchedEffect
        }

        collectionsLoading = true
        try {
            if (userApiKeyRequired && !apiKeyEdit.isLocked) {
                delay(PERSONAL_COLLECTION_KEY_DEBOUNCE_MILLIS)
            }
            loadRemoteOptions {
                val api = if (userApiKeyRequired) {
                    val resolvedApiKey = apiKeyEdit.valueForRequest { RConfig.decryptAsync(it) }
                    createPexelsApi(resolvedApiKey)
                } else {
                    createPexelsApi()
                }
                loadAllRemoteOptions { page ->
                    val result = when (selectedType) {
                        PexelsSourceType.Collections -> api.featuredCollections(
                            page = page,
                            perPage = MAX_COLLECTION_OPTIONS,
                        )

                        PexelsSourceType.MyCollection -> api.myCollections(
                            page = page,
                            perPage = MAX_COLLECTION_OPTIONS,
                        )

                        PexelsSourceType.FeedPhotos -> error("Feed photos do not have collections")
                    }
                    RemoteOptionsPage(
                        items = result.collections,
                        hasMore = !result.nextPage.isNullOrBlank(),
                    )
                }
            }
                .onSuccess { collections = it }
                .onFailure { ToastUtil.error(it.message ?: "Failed to load Pexels collections") }
        } finally {
            collectionsLoading = false
        }
    }

    fun buildSource(): PexelsSource {
        return PexelsConfigDraft(
            name = name.encodeName(),
            photoType = selectedType,
            collectionId = collectionId,
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

            selectedType != PexelsSourceType.FeedPhotos && collectionId.isBlank() -> {
                ToastUtil.error(collectionRequiredText)
                false
            }

            else -> true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Icon(
            modifier = Modifier.size(96.dp),
            painter = painterResource(Res.drawable.ic_pexels),
            contentDescription = PEXELS.typeName
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

        LargeDropdownMenu(
            label = stringResource(Res.string.choose_type),
            items = PexelsTypes.map { stringResource(it.titleRes) },
            selectedIndex = selectedTypeIndex,
            onItemSelected = { index, _ -> selectedTypeIndex = index }
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (userApiKeyRequired) {
            PasswordInput(
                password = if (apiKeyEdit.isLocked) EXISTING_PASSWORD_PLACEHOLDER else apiKey,
                passwordVisible = apiKeyVisible,
                editMode = editMode,
                readOnly = apiKeyEdit.isLocked,
                label = stringResource(Res.string.pexels_api_key),
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
            ApiTokenHelp(ApiTokenProvider.Pexels)
        }

        if (selectedType != PexelsSourceType.FeedPhotos) {
            when {
                collectionsLoading -> {
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

                collections.isNotEmpty() -> {
                    LargeDropdownMenu(
                        label = stringResource(Res.string.choose_collection),
                        items = collections,
                        selectedIndex = collections.indexOfFirst { it.id == collectionId },
                        onItemSelected = { _, item -> collectionId = item.id },
                        selectedItemToString = { it.title }
                    )
                }

                else -> {
                    OutlinedTextField(
                        shape = RoundedCornerShape(Dimen.textFiledCorners),
                        value = collectionId,
                        onValueChange = { collectionId = it.trim() },
                        label = { Text(stringResource(Res.string.collection_id)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

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

    if (pexelsSource == null) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

private const val MAX_COLLECTION_OPTIONS = 80
private const val PERSONAL_COLLECTION_KEY_DEBOUNCE_MILLIS = 600L
