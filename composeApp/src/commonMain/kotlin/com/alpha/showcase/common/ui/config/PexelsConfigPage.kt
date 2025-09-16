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
import com.alpha.showcase.common.networkfile.storage.remote.PEXELS
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.repo.PexelsTypes
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
import showcaseapp.composeapp.generated.resources.ic_pexels
import showcaseapp.composeapp.generated.resources.name_is_invalid
import showcaseapp.composeapp.generated.resources.name_require_hint
import showcaseapp.composeapp.generated.resources.save
import showcaseapp.composeapp.generated.resources.test_connection

@Composable
fun PexelsConfigPage(
    pexelsSource: PexelsSource? = null,
    onTestClick: suspend (PexelsSource) -> Result<Any>?,
    onSaveClick: suspend (PexelsSource) -> Unit
) {

    var checkingState by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var selectedTypeIndex by remember {
        mutableIntStateOf(pexelsSource?.photoType?.let {
            PexelsTypes.indexOfFirst { type -> type.type == it }
        } ?: 0)
    }

    var name by rememberSaveable(key = "name") {
        mutableStateOf(pexelsSource?.name?.decodeName() ?: "")
    }

    val focusRequester = remember { FocusRequester() }

    Column(
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(16.dp)
    ) {

        Icon(
            modifier = Modifier.size(96.dp),
            painter = painterResource(Res.drawable.ic_pexels),
            contentDescription = PEXELS.typeName
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
            items = PexelsTypes.map { stringResource(it.titleRes) },
            selectedIndex = selectedTypeIndex,
            onItemSelected = { index, _ -> selectedTypeIndex = index }
        )

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
                                PexelsSource(
                                    name.encodeName(),
                                    PexelsTypes[selectedTypeIndex].type
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
                            PexelsSource(
                                name.encodeName(),
                                PexelsTypes[selectedTypeIndex].type
                            )
                        )
                    }
                }

            }, modifier = Modifier.padding(10.dp)) {
                Text(text = stringResource(Res.string.save), maxLines = 1)
            }
        }
    }

}
