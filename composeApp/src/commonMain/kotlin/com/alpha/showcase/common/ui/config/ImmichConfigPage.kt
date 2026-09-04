package com.alpha.showcase.common.ui.config

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
import com.alpha.showcase.api.immich.Album
import com.alpha.showcase.api.immich.ImmichApi
import com.alpha.showcase.api.immich.LoginRequest
import com.alpha.showcase.common.networkfile.storage.remote.IMMICH_AUTH_TYPE_API_KEY
import com.alpha.showcase.common.networkfile.storage.remote.IMMICH_AUTH_TYPE_BEARER
import com.alpha.showcase.common.networkfile.storage.remote.ImmichSource
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.ui.view.EXISTING_PASSWORD_PLACEHOLDER
import com.alpha.showcase.common.ui.view.HintText
import com.alpha.showcase.common.ui.view.LargeDropdownMenu
import com.alpha.showcase.common.ui.view.PasswordInput
import com.alpha.showcase.common.utils.ToastUtil
import com.alpha.showcase.common.utils.checkName
import com.alpha.showcase.common.utils.checkPort
import com.alpha.showcase.common.utils.checkUrl
import com.alpha.showcase.common.utils.decodeName
import com.alpha.showcase.common.utils.encodeName
import com.alpha.showcase.common.utils.runConnectionProbe
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.Url
import showcaseapp.composeapp.generated.resources.album
import showcaseapp.composeapp.generated.resources.auth_type
import showcaseapp.composeapp.generated.resources.auth_type_apikey
import showcaseapp.composeapp.generated.resources.auth_type_user_and_pass
import showcaseapp.composeapp.generated.resources.port
import showcaseapp.composeapp.generated.resources.repo_url_require_hint
import showcaseapp.composeapp.generated.resources.save
import showcaseapp.composeapp.generated.resources.source_name
import showcaseapp.composeapp.generated.resources.test_connection
import showcaseapp.composeapp.generated.resources.user
import showcaseapp.composeapp.generated.resources.your_api_key
import showcaseapp.composeapp.generated.resources.connection_successful
import showcaseapp.composeapp.generated.resources.web_source_browser_access_error
import showcaseapp.composeapp.generated.resources.web_source_mixed_content_error


@Composable
fun ImmichConfigPage(
    immichSource: ImmichSource? = null,
    onTestClick: suspend (ImmichSource) -> Result<Any>?,
    onSaveClick: suspend (ImmichSource) -> Unit
) {
    val editMode = immichSource != null

    var name by rememberSaveable(key = "name") {
        mutableStateOf(immichSource?.name?.decodeName() ?: "")
    }
    var url by rememberSaveable(key = "url") {
        mutableStateOf(immichSource?.url ?: "")
    }
    var port by rememberSaveable(key = "port") {
        mutableStateOf(immichSource?.port?.toString() ?: "")
    }
    var authType by rememberSaveable(key = "authType") {
        mutableStateOf(immichSource?.authType ?: IMMICH_AUTH_TYPE_API_KEY)
    }
    var useremail by rememberSaveable(key = "useremail") {
        mutableStateOf(immichSource?.user ?: "")
    }
    val existingEncryptedPassword = immichSource?.pass
    val hasExistingPassword = !existingEncryptedPassword.isNullOrBlank()
    var existingPlainPassword by remember(immichSource?.pass) { mutableStateOf("") }

    var password by rememberSaveable(key = "immich_password") {
        mutableStateOf("")
    }
    var passwordLocked by rememberSaveable(key = "immich_password_locked") {
        mutableStateOf(editMode && hasExistingPassword)
    }
    var passwordChanged by rememberSaveable(key = "immich_password_changed") { mutableStateOf(false) }
    var apiKey by rememberSaveable(key = "apiKey") {
        mutableStateOf("")
    }
    var apiKeyChanged by rememberSaveable(key = "apiKeyChanged") {
        mutableStateOf(false)
    }
    var secretsLoaded by remember(immichSource) { mutableStateOf(immichSource == null) }
    var album by rememberSaveable(key = "album") {
        mutableStateOf(immichSource?.album ?: "")
    }

    var tempImmich by remember {
        mutableStateOf(immichSource)
    }
    val focusRequester = remember { FocusRequester() }

    var showApikeyDialog by rememberSaveable(key = "showApikeyDialog") {
        mutableStateOf(
            false
        )
    }

    var passwordVisible by rememberSaveable(key = "immich_password_visible") { mutableStateOf(false) }
    var nameValid by rememberSaveable(key = "nameValid") { mutableStateOf(true) }
    var urlValid by rememberSaveable(key = "urlValid") { mutableStateOf(true) }
    var portValid by rememberSaveable(key = "portValid") { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val browserMixedContentMessage = stringResource(Res.string.web_source_mixed_content_error)
    val browserAccessMessage = stringResource(Res.string.web_source_browser_access_error)

    fun connectionFailureMessage(error: Throwable): String {
        val problem = (error as? BrowserConnectionException)?.problem
            ?: browserConnectionProblem(
                baseUrl = url,
                error = error,
            )
        return when (problem) {
            BrowserConnectionProblem.MixedContent -> browserMixedContentMessage
            BrowserConnectionProblem.BrowserAccess -> browserAccessMessage
            null -> error.message ?: "Immich connection failed"
        }
    }

    suspend fun loadAlbums(
        requestedUrl: String,
        requestedAuthType: String,
        requestedApiKey: String,
        requestedUser: String,
        requestedPassword: String,
    ): Result<List<Album>> {
        browserConnectionProblem(baseUrl = requestedUrl)?.let { problem ->
            return Result.failure(BrowserConnectionException(problem))
        }
        return runConnectionProbe {
            try {
                val service = ImmichApi()
                val loaded = when (requestedAuthType) {
                    IMMICH_AUTH_TYPE_API_KEY ->
                        service.getAlbumsWithApikey(requestedUrl, requestedApiKey)
                    IMMICH_AUTH_TYPE_BEARER -> {
                        val login = service.login(
                            requestedUrl,
                            LoginRequest(requestedUser, requestedPassword),
                        )
                        val accessToken = login?.accessToken
                            ?: return@runConnectionProbe Result.failure(
                                Exception(login?.message ?: "Immich authentication failed")
                            )
                        service.getAlbumsWithAccessToken(requestedUrl, "Bearer $accessToken")
                    }
                    else -> return@runConnectionProbe Result.failure(
                        Exception("Unsupported Immich authentication type")
                    )
                }
                loaded?.let { Result.success(it) }
                    ?: Result.failure(Exception("Immich returned no album response"))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }
    }

    LaunchedEffect(immichSource?.pass, immichSource?.apiKey) {
        existingPlainPassword = immichSource?.pass?.let { RConfig.decryptAsync(it) }.orEmpty()
        val decryptedApiKey = immichSource?.apiKey?.let { RConfig.decryptAsync(it) }.orEmpty()
        if (!apiKeyChanged) {
            apiKey = decryptedApiKey
        }
        secretsLoaded = true
    }


    fun checkAndFix(): Boolean {
        val realPort = port.ifBlank { "2283" }
        nameValid = checkName(name, true) {
            urlValid = checkUrl(url, true) {
                portValid = checkPort(realPort, true)
            }
        }
        return nameValid && urlValid && portValid
    }

    fun effectivePasswordPlain(): String {
        return if (editMode && !passwordChanged) {
            existingPlainPassword
        } else {
            password
        }
    }

    fun effectiveStoredPassword(): String {
        return if (editMode && !passwordChanged) {
            existingEncryptedPassword ?: password
        } else {
            password
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            modifier = Modifier.focusRequester(focusRequester),
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            label = {Text(text = stringResource(Res.string.source_name), style = TextStyle(fontWeight = FontWeight.Bold))},
            value = name,
            onValueChange = {
                name = it
                nameValid = checkName(it)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            placeholder = { HintText(text = "immich") },
            singleLine = true,
            isError = ! nameValid
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            label = {Text(text = stringResource(Res.string.Url), style = TextStyle(fontWeight = FontWeight.Bold))},
            value = url,
            onValueChange = {
                url = it
                urlValid = checkUrl(it)

                try {
                    val uri = Url(it)
                    val portMatches = uri.port == -1 || uri.port in 1 .. 65535
                    if (portMatches){
                        port = if (uri.port == -1) {
//              if (url[url.lastIndex].toString() == ":"){
//                url = url.dropLast(1)
//              }
                            ""
                        } else uri.port.toString()
                    }
                }catch (e: Exception){
                    e.printStackTrace()
                }

            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            placeholder = {Text(text = "http://example.com")},
            singleLine = true,
            isError = ! urlValid
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            label = {Text(text = stringResource(Res.string.port), style = TextStyle(fontWeight = FontWeight.Bold))},
            value = port,
            onValueChange = {
                port = it
                portValid = (it.isBlank() || checkPort(it))
                // add port to url if not empty
                url = if (portValid && it.isNotEmpty()) {
                    if (url.contains(Regex(":\\d+")).not()) {

                        val fixedUrl = try {
                            val uri = Url(if (url.startsWith("http://") || url.startsWith("https://")) url else "http://" + (if (url.startsWith("//")) url.drop(2) else url))
                            if (uri.encodedPath.isBlank()) {
                                "$uri:$it"
                            } else {
                                val p = uri.encodedPath
                                val pathIndex = uri.toString().indexOf(p)
                                val urlWithoutPath = uri.toString().substring(0, pathIndex)
                                val urlWithPort = "$urlWithoutPath:$it"
                                val urlWithPortAndPath = "$urlWithPort$p"
                                urlWithPortAndPath
                            }

                        }catch (e: Exception){
                            e.printStackTrace()
                            url
                        }
                        fixedUrl
                    } else {
                        url.replace(Regex(":\\d+"), ":$it")
                    }
                }else {
                    url.replace(Regex(":\\d+"), "")
                }
                urlValid = checkUrl(url)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            placeholder = {HintText(text = "2283")},
            singleLine = true,
            isError = ! portValid
        )
        Spacer(modifier = Modifier.height(16.dp))

        val authTypes = remember { mapOf(IMMICH_AUTH_TYPE_API_KEY to Res.string.auth_type_apikey, IMMICH_AUTH_TYPE_BEARER to Res.string.auth_type_user_and_pass) }

        LargeDropdownMenu(
            label = stringResource(Res.string.auth_type),
            items = authTypes.map { stringResource(it.value) },
            selectedIndex = authTypes.keys.indexOf(authType),
            onItemSelected = { index, _ -> authType = authTypes.keys.elementAt(index) },
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (authType == IMMICH_AUTH_TYPE_API_KEY){
            OutlinedTextField(
                shape = RoundedCornerShape(Dimen.textFiledCorners),
                label = {
                    Text(
                        text = stringResource(Res.string.your_api_key),
                        style = TextStyle(fontWeight = FontWeight.Bold)
                    )
                },
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    apiKeyChanged = true
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )
        }

        if (authType == IMMICH_AUTH_TYPE_BEARER){
            OutlinedTextField(
                shape = RoundedCornerShape(Dimen.textFiledCorners),
                label = {
                    Text(
                        text = stringResource(Res.string.user),
                        style = TextStyle(fontWeight = FontWeight.Bold)
                    )
                },
                value = useremail,
                onValueChange = { useremail = it },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                placeholder = { Text(text = "") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            PasswordInput(
                modifier = Modifier.fillMaxWidth(),
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
        }

        Spacer(modifier = Modifier.height(16.dp))


        var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
        var selectedAlbum by remember { mutableStateOf<Album?>(null) }

        LaunchedEffect(immichSource, secretsLoaded) {
            if (immichSource == null || !secretsLoaded) return@LaunchedEffect
            loadAlbums(url, authType, apiKey, useremail, effectivePasswordPlain())
                .onSuccess { loaded ->
                    albums = loaded
                    selectedAlbum = albums.find { it.albumName == album }
                    album = selectedAlbum?.albumName ?: ""
                }
                .onFailure { error ->
                    error.printStackTrace()
                    ToastUtil.error(connectionFailureMessage(error))
                }
        }

        if (albums.isNotEmpty()){
            LargeDropdownMenu(
                label = stringResource(Res.string.album),
                items = albums.map { it.albumName },
                selectedIndex = albums.indexOfFirst{
                    it.id == selectedAlbum?.id
                },
                onItemSelected = { index, _ ->
                    selectedAlbum = albums[index]
                    album = selectedAlbum?.albumName ?: ""
                },
            )
        }else {
            OutlinedTextField(
                shape = RoundedCornerShape(Dimen.textFiledCorners),
                label = {
                    Text(
                        text = stringResource(Res.string.album),
                        style = TextStyle(fontWeight = FontWeight.Bold)
                    )
                },
                value = album,
                onValueChange = {
                    album = it
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                singleLine = true,
            )
        }


        Spacer(modifier = Modifier.height(8.dp))


        var checkingState by remember { mutableStateOf(false) }

        Row {
            ElevatedButton(onClick = {
                if (checkAndFix() && !checkingState) {
                    scope.launch {
                        checkingState = true
                        val finalUser = if (authType == IMMICH_AUTH_TYPE_API_KEY) "" else useremail
                        val finalPlainPassword = if (authType == IMMICH_AUTH_TYPE_API_KEY) "" else effectivePasswordPlain()
                        val finalStoredPassword = if (authType == IMMICH_AUTH_TYPE_API_KEY) "" else effectiveStoredPassword()
                        val finalApiKey = if (authType == IMMICH_AUTH_TYPE_BEARER) "" else apiKey
                        val immich = ImmichSource(
                            name = name.encodeName(),
                            url = url,
                            port = port.toIntOrNull() ?: 2283,
                            user = finalUser,
                            pass = finalStoredPassword,
                            apiKey = finalApiKey,
                            album = album,
                            authType = authType
                        )
//                        val result = onTestClick.invoke(immich)
//                        result?.onSuccess {
//
//                        }

//                        result?.onFailure {
//                            // Handle failure, e.g., show a toast or dialog
//                            checkingState = false
//                            // You can use a Snackbar or Toast to show the error message
//                            // Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
//                        }

                        tempImmich = immich

                        try {
                            loadAlbums(url, authType, finalApiKey, finalUser, finalPlainPassword)
                                .onSuccess { loaded ->
                                    albums = loaded
                                    selectedAlbum = albums.find { it.albumName == album }
                                        ?: albums.singleOrNull()
                                    album = selectedAlbum?.albumName ?: ""
                                    ToastUtil.success(Res.string.connection_successful)
                                }
                                .onFailure { error ->
                                    error.printStackTrace()
                                    ToastUtil.error(connectionFailureMessage(error))
                                }
                        } finally {
                            checkingState = false
                        }
                    }
                }
            }, modifier = Modifier.padding(10.dp), enabled = secretsLoaded && !checkingState){
                if (checkingState){
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
                    if (checkAndFix()){
                        val finalUser = if (authType == IMMICH_AUTH_TYPE_API_KEY) "" else useremail
                        val finalStoredPassword = if (authType == IMMICH_AUTH_TYPE_API_KEY) "" else effectiveStoredPassword()
                        val finalApiKey = if (authType == IMMICH_AUTH_TYPE_BEARER) "" else apiKey
                        val immich = ImmichSource(
                            name = name.encodeName(),
                            url = url,
                            port = port.toIntOrNull() ?: 2283,
                            user = finalUser,
                            pass = finalStoredPassword,
                            apiKey = finalApiKey,
                            album = album,
                            authType = authType
                        )
                        onSaveClick.invoke(immich)
                    }
                }
            }, modifier = Modifier.padding(10.dp), enabled = secretsLoaded) {
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
fun ImmichConfigPagePreview() {
    ImmichConfigPage(onTestClick = { null }, onSaveClick = {})
}
