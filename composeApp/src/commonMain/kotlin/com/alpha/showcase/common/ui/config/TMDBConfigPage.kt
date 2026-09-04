package com.alpha.showcase.common.ui.config

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.alpha.showcase.common.networkfile.storage.remote.TMDBSource
import com.alpha.showcase.common.repo.ExternalImageApiKeyEdit
import com.alpha.showcase.common.repo.ImageType
import com.alpha.showcase.common.repo.Language
import com.alpha.showcase.common.repo.Region
import com.alpha.showcase.common.repo.TMDBSourceType
import com.alpha.showcase.common.repo.TmdbConfigDraft
import com.alpha.showcase.common.repo.shouldRequestTmdbApiToken
import showcaseapp.composeapp.generated.resources.Res
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.ui.view.EXISTING_PASSWORD_PLACEHOLDER
import com.alpha.showcase.common.ui.view.PasswordInput
import com.alpha.showcase.common.utils.ToastUtil
import com.alpha.showcase.common.utils.decodeName
import com.alpha.showcase.common.utils.encodeName
import com.alpha.showcase.common.ui.view.LargeDropdownMenu
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.choose_image_type
import showcaseapp.composeapp.generated.resources.choose_language
import showcaseapp.composeapp.generated.resources.choose_region
import showcaseapp.composeapp.generated.resources.choose_type
import showcaseapp.composeapp.generated.resources.language_of_film_content
import showcaseapp.composeapp.generated.resources.name_is_invalid
import showcaseapp.composeapp.generated.resources.name_require_hint
import showcaseapp.composeapp.generated.resources.please_enter_your_api_token
import showcaseapp.composeapp.generated.resources.save
import showcaseapp.composeapp.generated.resources.show_movie_from_selected_region
import showcaseapp.composeapp.generated.resources.test_connection
import showcaseapp.composeapp.generated.resources.tmdb_api_read_access_token

@Composable
fun TMDBConfigPage(
    tmdbSource: TMDBSource? = null,
    onTestClick: suspend (TMDBSource) -> Result<Any>?,
    onSaveClick: suspend (TMDBSource) -> Unit
) {

    var checkingState by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val editMode = tmdbSource != null
    val existingStoredApiToken = tmdbSource?.apiToken
    val userApiTokenRequired = shouldRequestTmdbApiToken()

    var selectedTypeIndex by remember {
        mutableIntStateOf(tmdbSource?.contentType?.let {
            movieTypes.indexOfFirst { type -> type.type == it }
        } ?: 0)
    }
    var selectedLanguageIndex by remember {
        mutableIntStateOf(tmdbSource?.language?.let {
            languages.indexOfFirst { type -> type.value == it }
        } ?: 0)
    }
    var selectedRegionIndex by remember {
        mutableIntStateOf(tmdbSource?.region?.let {
            regions.indexOfFirst { type -> type.value == it }
        } ?: 0)
    }
    var selectedImageTypeIndex by remember {
        mutableIntStateOf(tmdbSource?.imageType?.let {
            imageTypes.indexOfFirst { type -> type.value == it }
        } ?: 0)
    }

    var name by rememberSaveable(key = "name") {
        mutableStateOf(tmdbSource?.name?.decodeName() ?: "")
    }
    var apiToken by remember(existingStoredApiToken) { mutableStateOf("") }
    var apiTokenChanged by rememberSaveable {
        mutableStateOf(existingStoredApiToken.isNullOrBlank())
    }
    var apiTokenVisible by rememberSaveable { mutableStateOf(false) }

    val movieType by remember {
        derivedStateOf {
            movieTypes[selectedTypeIndex]
        }
    }
    val language by remember {
        derivedStateOf {
            languages[selectedLanguageIndex]
        }
    }
    val region by remember {
        derivedStateOf {
            regions[selectedRegionIndex]
        }
    }

    val imageType by remember {
        derivedStateOf {
            imageTypes[selectedImageTypeIndex]
        }
    }

    val focusRequester = remember { FocusRequester() }
    val apiTokenEdit = ExternalImageApiKeyEdit(
        input = apiToken,
        existingStoredValue = existingStoredApiToken,
        changed = apiTokenChanged,
    )
    val nameInvalidText = stringResource(Res.string.name_is_invalid)
    val apiTokenRequiredText = stringResource(Res.string.please_enter_your_api_token)

    fun buildSource(): TMDBSource {
        return TmdbConfigDraft(
            name = name.encodeName(),
            contentType = movieType.type,
            language = language.value,
            region = region.value,
            imageType = imageType.value,
            apiTokenEdit = apiTokenEdit,
            storeApiToken = userApiTokenRequired,
        ).toSource { it }
    }

    fun isValid(): Boolean {
        return when {
            name.isBlank() -> {
                ToastUtil.error(nameInvalidText)
                false
            }

            userApiTokenRequired && apiTokenEdit.isMissing -> {
                ToastUtil.error(apiTokenRequiredText)
                false
            }

            else -> true
        }
    }

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
                .focusRequester(focusRequester),
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (userApiTokenRequired) {
            PasswordInput(
                password = if (apiTokenEdit.isLocked) EXISTING_PASSWORD_PLACEHOLDER else apiToken,
                passwordVisible = apiTokenVisible,
                editMode = editMode,
                readOnly = apiTokenEdit.isLocked,
                label = stringResource(Res.string.tmdb_api_read_access_token),
                onPasswordChange = { value ->
                    if (apiTokenEdit.isLocked) {
                        apiTokenChanged = true
                        apiToken = ""
                        apiTokenVisible = false
                    } else {
                        apiTokenChanged = true
                        apiToken = value.trim()
                    }
                },
                onPasswordVisibleChanged = { apiTokenVisible = it },
            )
            ApiTokenHelp(ApiTokenProvider.Tmdb)
        }

        LargeDropdownMenu(
            label = stringResource(Res.string.choose_type),
            items = movieTypes.map { stringResource(it.titleRes) },
            selectedIndex = selectedTypeIndex,
            onItemSelected = { index, _ -> selectedTypeIndex = index }
        )

        Spacer(modifier = Modifier.height(16.dp))

        LargeDropdownMenu(
            label = stringResource(Res.string.choose_language),
            items = languages.map { stringResource(it.res) },
            selectedIndex = selectedLanguageIndex,
            onItemSelected = { index, _ -> selectedLanguageIndex = index }
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.language_of_film_content),
            style = MaterialTheme.typography.labelSmall
        )

        Spacer(modifier = Modifier.height(4.dp))

        LargeDropdownMenu(
            label = stringResource(Res.string.choose_region),
            items = regions.map { stringResource(it.res) },
            selectedIndex = selectedRegionIndex,
            onItemSelected = { index, _ -> selectedRegionIndex = index }
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.show_movie_from_selected_region),
            style = MaterialTheme.typography.labelSmall
        )

        Spacer(modifier = Modifier.height(4.dp))

        LargeDropdownMenu(
            label = stringResource(Res.string.choose_image_type),
            items = imageTypes.map { stringResource(it.res) },
            selectedIndex = selectedImageTypeIndex,
            onItemSelected = { index, _ -> selectedImageTypeIndex = index }
        )

        Spacer(modifier = Modifier.height(8.dp))



        Row {
            ElevatedButton(onClick = {
                if (!checkingState) {
                    scope.launch {

                        if (isValid()) {
                            checkingState = true
                            try {
                                onTestClick(buildSource())
                            } finally {
                                checkingState = false
                            }
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
                if (isValid()) {
                    scope.launch {
                        onSaveClick(buildSource())
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


val movieTypes = listOf(
    TMDBSourceType.Upcoming,
    TMDBSourceType.NowPlaying,
    TMDBSourceType.TopRated,
    TMDBSourceType.Popular
)

val languages = Language.entries.toTypedArray()

val regions = Region.entries.toTypedArray()

val imageTypes = ImageType.entries.toTypedArray()
