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
import com.alpha.showcase.api.mtphoto.MTPhotoAlbum
import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO_AUTH_TYPE_API_KEY
import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO_AUTH_TYPE_PASSWORD
import com.alpha.showcase.common.networkfile.storage.remote.MTPhotoSource
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.repo.MTPhotoSourceRepo
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.ui.view.EXISTING_PASSWORD_PLACEHOLDER
import com.alpha.showcase.common.ui.view.HintText
import com.alpha.showcase.common.ui.view.LargeDropdownMenu
import com.alpha.showcase.common.ui.view.PasswordInput
import com.alpha.showcase.common.utils.ToastUtil
import com.alpha.showcase.common.utils.checkName
import com.alpha.showcase.common.utils.checkUrl
import com.alpha.showcase.common.utils.decodeName
import com.alpha.showcase.common.utils.encodeName
import com.alpha.showcase.common.utils.runConnectionProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.Url
import showcaseapp.composeapp.generated.resources.album
import showcaseapp.composeapp.generated.resources.auth_type
import showcaseapp.composeapp.generated.resources.auth_type_apikey
import showcaseapp.composeapp.generated.resources.auth_type_user_and_pass
import showcaseapp.composeapp.generated.resources.mtphoto_album_required
import showcaseapp.composeapp.generated.resources.mtphoto_albums_loaded
import showcaseapp.composeapp.generated.resources.mtphoto_api_key_required
import showcaseapp.composeapp.generated.resources.mtphoto_browser_access_error
import showcaseapp.composeapp.generated.resources.mtphoto_password_required
import showcaseapp.composeapp.generated.resources.mtphoto_web_mixed_content_error
import showcaseapp.composeapp.generated.resources.mtphoto_username_required
import showcaseapp.composeapp.generated.resources.save
import showcaseapp.composeapp.generated.resources.source_name
import showcaseapp.composeapp.generated.resources.test_connection
import showcaseapp.composeapp.generated.resources.user
import showcaseapp.composeapp.generated.resources.your_api_key

internal enum class MTPhotoConfigError {
    ApiKeyRequired,
    UsernameRequired,
    PasswordRequired,
    AlbumRequired,
    UnsupportedAuthType,
}

internal fun validateMTPhotoConfig(
    authType: String,
    apiKey: String,
    username: String,
    password: String,
    albumId: Int?,
    requireAlbum: Boolean = true,
): MTPhotoConfigError? = when {
    authType == MTPHOTO_AUTH_TYPE_API_KEY && apiKey.isBlank() -> MTPhotoConfigError.ApiKeyRequired
    authType == MTPHOTO_AUTH_TYPE_PASSWORD && username.isBlank() -> MTPhotoConfigError.UsernameRequired
    authType == MTPHOTO_AUTH_TYPE_PASSWORD && password.isBlank() -> MTPhotoConfigError.PasswordRequired
    authType !in setOf(MTPHOTO_AUTH_TYPE_API_KEY, MTPHOTO_AUTH_TYPE_PASSWORD) ->
        MTPhotoConfigError.UnsupportedAuthType
    requireAlbum && albumId == null -> MTPhotoConfigError.AlbumRequired
    else -> null
}

internal fun isCurrentMTPhotoAlbumResponse(
    requested: MTPhotoSource,
    current: MTPhotoSource,
    requestId: Long = 0,
    latestRequestId: Long = requestId,
): Boolean = requestId == latestRequestId &&
    requested.url == current.url &&
    requested.authType == current.authType &&
    requested.apiKey == current.apiKey &&
    requested.user == current.user &&
    requested.pass == current.pass

internal const val MTPHOTO_ALBUM_LOAD_TIMEOUT_MILLIS = 10_000L

internal suspend fun <T> loadMTPhotoAlbumsWithTimeout(
    timeoutMillis: Long = MTPHOTO_ALBUM_LOAD_TIMEOUT_MILLIS,
    loader: suspend () -> Result<T>,
): Result<T> = runConnectionProbe(timeoutMillis, loader)

@Composable
fun MTPhotoConfigPage(
    mtPhotoSource: MTPhotoSource? = null,
    onTestClick: suspend (MTPhotoSource) -> Result<Any>?,
    onSaveClick: suspend (MTPhotoSource) -> Unit,
) {
    val editMode = mtPhotoSource != null
    val repo = remember { MTPhotoSourceRepo() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var name by rememberSaveable { mutableStateOf(mtPhotoSource?.name?.decodeName().orEmpty()) }
    var url by rememberSaveable { mutableStateOf(mtPhotoSource?.url.orEmpty()) }
    var authType by rememberSaveable {
        mutableStateOf(mtPhotoSource?.authType ?: MTPHOTO_AUTH_TYPE_API_KEY)
    }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf(mtPhotoSource?.user.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var apiKeyVisible by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var apiKeyLocked by rememberSaveable {
        mutableStateOf(editMode && !mtPhotoSource?.apiKey.isNullOrBlank())
    }
    var passwordLocked by rememberSaveable {
        mutableStateOf(editMode && !mtPhotoSource?.pass.isNullOrBlank())
    }
    var existingApiKeyPlain by remember(mtPhotoSource?.apiKey) { mutableStateOf("") }
    var existingPasswordPlain by remember(mtPhotoSource?.pass) { mutableStateOf("") }
    var secretsLoaded by remember(mtPhotoSource) { mutableStateOf(mtPhotoSource == null) }

    var selectedAlbumId by rememberSaveable { mutableStateOf(mtPhotoSource?.albumId) }
    var selectedAlbumName by rememberSaveable { mutableStateOf(mtPhotoSource?.albumName.orEmpty()) }
    var albums by remember { mutableStateOf<List<MTPhotoAlbum>>(emptyList()) }
    var checking by remember { mutableStateOf(false) }
    var latestAlbumRequestId by remember { mutableStateOf(0L) }
    var nameValid by rememberSaveable { mutableStateOf(true) }
    var urlValid by rememberSaveable { mutableStateOf(true) }

    val apiKeyRequiredMessage = stringResource(Res.string.mtphoto_api_key_required)
    val usernameRequiredMessage = stringResource(Res.string.mtphoto_username_required)
    val passwordRequiredMessage = stringResource(Res.string.mtphoto_password_required)
    val albumRequiredMessage = stringResource(Res.string.mtphoto_album_required)
    val albumsLoadedMessage = stringResource(Res.string.mtphoto_albums_loaded)
    val browserAccessErrorMessage = stringResource(Res.string.mtphoto_browser_access_error)
    val webMixedContentErrorMessage = stringResource(Res.string.mtphoto_web_mixed_content_error)

    fun effectiveApiKey(): String = if (apiKeyLocked) existingApiKeyPlain else apiKey
    fun effectivePassword(): String = if (passwordLocked) existingPasswordPlain else password

    fun clearLoadedAlbums() {
        albums = emptyList()
        selectedAlbumId = null
        selectedAlbumName = ""
    }

    fun buildSource(): MTPhotoSource = MTPhotoSource(
        name = name.encodeName(),
        url = url.trim().trimEnd('/'),
        authType = authType,
        apiKey = effectiveApiKey().takeIf { authType == MTPHOTO_AUTH_TYPE_API_KEY },
        user = username.takeIf { authType == MTPHOTO_AUTH_TYPE_PASSWORD },
        pass = effectivePassword().takeIf { authType == MTPHOTO_AUTH_TYPE_PASSWORD },
        albumId = selectedAlbumId,
        albumName = selectedAlbumName.takeIf { selectedAlbumId != null },
    )

    fun connectionFailureMessage(error: Throwable): String {
        val problem = (error as? BrowserConnectionException)?.problem
            ?: browserConnectionProblem(
                baseUrl = buildSource().url,
                error = error,
            )
        return when (problem) {
            BrowserConnectionProblem.MixedContent -> webMixedContentErrorMessage
            BrowserConnectionProblem.BrowserAccess -> browserAccessErrorMessage
            null -> error.message ?: "MTPhoto connection failed"
        }
    }

    fun checkBaseFields(): Boolean {
        nameValid = checkName(name, showToast = true) {
            urlValid = checkUrl(url, showToast = true)
        }
        return nameValid && urlValid
    }

    fun showConfigError(error: MTPhotoConfigError) {
        val message = when (error) {
            MTPhotoConfigError.ApiKeyRequired -> apiKeyRequiredMessage
            MTPhotoConfigError.UsernameRequired -> usernameRequiredMessage
            MTPhotoConfigError.PasswordRequired -> passwordRequiredMessage
            MTPhotoConfigError.AlbumRequired -> albumRequiredMessage
            MTPhotoConfigError.UnsupportedAuthType -> "Unsupported MTPhoto authentication type"
        }
        ToastUtil.error(message)
    }

    fun validate(requireAlbum: Boolean): Boolean {
        if (!checkBaseFields()) return false
        val error = validateMTPhotoConfig(
            authType = authType,
            apiKey = effectiveApiKey(),
            username = username,
            password = effectivePassword(),
            albumId = selectedAlbumId,
            requireAlbum = requireAlbum,
        )
        error?.let(::showConfigError)
        return error == null
    }

    fun syncSelectedAlbum(selectSingleAlbum: Boolean) {
        val selected = albums.firstOrNull { it.id == selectedAlbumId }
            ?: albums.singleOrNull().takeIf { selectSingleAlbum }
        selectedAlbumId = selected?.id
        selectedAlbumName = selected?.name.orEmpty()
    }

    suspend fun refreshAlbums(selectSingleAlbum: Boolean): Result<List<MTPhotoAlbum>?> {
        val requestedSource = buildSource()
        browserConnectionProblem(baseUrl = requestedSource.url)?.let { problem ->
            return Result.failure(BrowserConnectionException(problem))
        }
        latestAlbumRequestId += 1
        val requestId = latestAlbumRequestId
        val result = loadMTPhotoAlbumsWithTimeout {
            repo.getAlbums(requestedSource)
        }
        if (
            !isCurrentMTPhotoAlbumResponse(
                requested = requestedSource,
                current = buildSource(),
                requestId = requestId,
                latestRequestId = latestAlbumRequestId,
            )
        ) {
            return Result.success(null)
        }
        return result.map { loaded ->
            albums = loaded
            syncSelectedAlbum(selectSingleAlbum)
            loaded
        }
    }

    LaunchedEffect(mtPhotoSource?.apiKey, mtPhotoSource?.pass) {
        try {
            existingApiKeyPlain = mtPhotoSource?.apiKey?.let { RConfig.decryptAsync(it) }.orEmpty()
            existingPasswordPlain = mtPhotoSource?.pass?.let { RConfig.decryptAsync(it) }.orEmpty()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            error.printStackTrace()
            ToastUtil.error(error.message ?: "Failed to read MTPhoto credentials")
        } finally {
            secretsLoaded = true
        }
    }

    LaunchedEffect(mtPhotoSource, secretsLoaded) {
        if (mtPhotoSource == null || !secretsLoaded) return@LaunchedEffect
        refreshAlbums(selectSingleAlbum = false).onFailure { error ->
            error.printStackTrace()
            ToastUtil.error(connectionFailureMessage(error))
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
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            modifier = Modifier.focusRequester(focusRequester),
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            label = {
                Text(stringResource(Res.string.source_name), style = TextStyle(fontWeight = FontWeight.Bold))
            },
            value = name,
            enabled = !checking,
            onValueChange = {
                name = it
                nameValid = checkName(it)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            placeholder = { HintText("MTPhoto") },
            singleLine = true,
            isError = !nameValid,
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            label = { Text(stringResource(Res.string.Url), style = TextStyle(fontWeight = FontWeight.Bold)) },
            value = url,
            enabled = !checking,
            onValueChange = {
                url = it
                urlValid = checkUrl(it)
                clearLoadedAlbums()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            placeholder = { Text("http://example.com:8063") },
            singleLine = true,
            isError = !urlValid,
        )

        Spacer(Modifier.height(16.dp))

        val authTypes = listOf(
            MTPHOTO_AUTH_TYPE_API_KEY to stringResource(Res.string.auth_type_apikey),
            MTPHOTO_AUTH_TYPE_PASSWORD to stringResource(Res.string.auth_type_user_and_pass),
        )
        LargeDropdownMenu(
            enabled = !checking,
            label = stringResource(Res.string.auth_type),
            items = authTypes,
            selectedIndex = authTypes.indexOfFirst { it.first == authType },
            selectedItemToString = { it.second },
            onItemSelected = { _, item ->
                if (item.first != authType) {
                    authType = item.first
                    clearLoadedAlbums()
                }
            },
        )

        Spacer(Modifier.height(16.dp))

        if (authType == MTPHOTO_AUTH_TYPE_API_KEY) {
            PasswordInput(
                password = if (apiKeyLocked) EXISTING_PASSWORD_PLACEHOLDER else apiKey,
                passwordVisible = apiKeyVisible,
                enabled = !checking,
                editMode = editMode,
                readOnly = apiKeyLocked,
                label = stringResource(Res.string.your_api_key),
                onPasswordChange = {
                    if (apiKeyLocked) {
                        apiKeyLocked = false
                        apiKey = ""
                        apiKeyVisible = false
                    } else {
                        apiKey = it
                    }
                    clearLoadedAlbums()
                },
                onPasswordVisibleChanged = { apiKeyVisible = it },
            )
        } else {
            OutlinedTextField(
                shape = RoundedCornerShape(Dimen.textFiledCorners),
                label = { Text(stringResource(Res.string.user), style = TextStyle(fontWeight = FontWeight.Bold)) },
                value = username,
                enabled = !checking,
                onValueChange = {
                    username = it
                    clearLoadedAlbums()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                singleLine = true,
            )

            Spacer(Modifier.height(16.dp))

            PasswordInput(
                password = if (passwordLocked) EXISTING_PASSWORD_PLACEHOLDER else password,
                passwordVisible = passwordVisible,
                enabled = !checking,
                editMode = editMode,
                readOnly = passwordLocked,
                onPasswordChange = {
                    if (passwordLocked) {
                        passwordLocked = false
                        password = ""
                        passwordVisible = false
                    } else {
                        password = it
                    }
                    clearLoadedAlbums()
                },
                onPasswordVisibleChanged = { passwordVisible = it },
            )
        }

        Spacer(Modifier.height(16.dp))

        if (albums.isNotEmpty()) {
            LargeDropdownMenu(
                enabled = !checking,
                label = stringResource(Res.string.album),
                items = albums,
                selectedIndex = albums.indexOfFirst { it.id == selectedAlbumId },
                selectedItemToString = { "${it.name} (${it.count})" },
                onItemSelected = { _, selected ->
                    selectedAlbumId = selected.id
                    selectedAlbumName = selected.name
                },
            )
        } else {
            OutlinedTextField(
                shape = RoundedCornerShape(Dimen.textFiledCorners),
                label = { Text(stringResource(Res.string.album), style = TextStyle(fontWeight = FontWeight.Bold)) },
                value = selectedAlbumName,
                enabled = !checking,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
            )
        }

        Spacer(Modifier.height(8.dp))

        Row {
            ElevatedButton(
                onClick = {
                    if (validate(requireAlbum = false) && !checking) {
                        scope.launch {
                            checking = true
                            try {
                                refreshAlbums(selectSingleAlbum = true)
                                    .onSuccess { loaded ->
                                        if (loaded == null) return@onSuccess
                                        if (selectedAlbumId == null) {
                                            ToastUtil.success(albumsLoadedMessage)
                                        } else {
                                            onTestClick(buildSource())
                                        }
                                    }
                                    .onFailure { error ->
                                        error.printStackTrace()
                                        ToastUtil.error(connectionFailureMessage(error))
                                    }
                            } finally {
                                checking = false
                            }
                        }
                    }
                },
                modifier = Modifier.padding(10.dp),
                enabled = secretsLoaded && !checking,
            ) {
                if (checking) {
                    Box {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(5.dp).align(Alignment.Center).size(Dimen.spaceL),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                Text(stringResource(Res.string.test_connection))
            }

            ElevatedButton(
                onClick = {
                    if (validate(requireAlbum = true)) {
                        scope.launch { onSaveClick(buildSource()) }
                    }
                },
                modifier = Modifier.padding(10.dp),
                enabled = secretsLoaded && !checking,
            ) {
                Text(stringResource(Res.string.save), maxLines = 1)
            }
        }
    }

    if (!editMode) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}
