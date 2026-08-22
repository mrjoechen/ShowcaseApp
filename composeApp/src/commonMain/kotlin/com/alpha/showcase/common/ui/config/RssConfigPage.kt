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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.networkfile.storage.remote.RssSource
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.utils.checkName
import com.alpha.showcase.common.utils.decodeName
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.ic_rss
import showcaseapp.composeapp.generated.resources.rss_feed_hint
import showcaseapp.composeapp.generated.resources.rss_feed_url
import showcaseapp.composeapp.generated.resources.save
import showcaseapp.composeapp.generated.resources.source_name
import showcaseapp.composeapp.generated.resources.test_connection

@Composable
fun RssConfigPage(
    rssSource: RssSource? = null,
    onTestClick: suspend (RssSource) -> Result<Any>?,
    onSaveClick: suspend (RssSource) -> Unit,
) {
    val editMode = rssSource != null
    var name by rememberSaveable { mutableStateOf(rssSource?.name?.decodeName().orEmpty()) }
    var url by rememberSaveable { mutableStateOf(rssSource?.url.orEmpty()) }
    var nameValid by rememberSaveable { mutableStateOf(true) }
    var urlValid by rememberSaveable { mutableStateOf(true) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    fun buildSource(): RssSource? {
        nameValid = checkName(name)
        urlValid = isValidHttpUrl(url.trim())
        if (!nameValid || !urlValid) return null
        return RssConfigDraft(name, url).toSource()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_rss),
            contentDescription = "RSS Feed",
            tint = Color.Unspecified,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            modifier = Modifier.focusRequester(focusRequester),
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            value = name,
            onValueChange = { name = it; nameValid = checkName(it) },
            label = { Text(stringResource(Res.string.source_name)) },
            isError = !nameValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            value = url,
            onValueChange = { url = it.trim(); urlValid = it.isBlank() || isValidHttpUrl(it) },
            label = { Text(stringResource(Res.string.rss_feed_url)) },
            placeholder = { Text("https://example.com/feed.xml") },
            isError = !urlValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.rss_feed_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ElevatedButton(
                onClick = {
                    if (!checking) buildSource()?.let { source ->
                        scope.launch {
                            checking = true
                            try {
                                onTestClick(source)
                            } finally {
                                checking = false
                            }
                        }
                    }
                },
            ) {
                if (checking) {
                    Box { CircularProgressIndicator(Modifier.padding(5.dp).size(Dimen.spaceL), strokeWidth = 2.dp) }
                }
                Text(stringResource(Res.string.test_connection))
            }
            ElevatedButton(onClick = { buildSource()?.let { source -> scope.launch { onSaveClick(source) } } }) {
                Text(stringResource(Res.string.save), maxLines = 1)
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (!editMode) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}
