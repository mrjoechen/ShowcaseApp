package com.alpha.showcase.common.ui.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key.Companion.R
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.networkfile.storage.remote.WeiboSource
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.ui.view.HintText
import com.alpha.showcase.common.utils.ToastUtil
import com.alpha.showcase.common.utils.checkName
import com.alpha.showcase.common.utils.decodeName
import com.alpha.showcase.common.utils.encodeName
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.name_is_invalid
import showcaseapp.composeapp.generated.resources.save
import showcaseapp.composeapp.generated.resources.source_name
import showcaseapp.composeapp.generated.resources.test_connection
import showcaseapp.composeapp.generated.resources.weibo_original
import showcaseapp.composeapp.generated.resources.weibo_retweet
import showcaseapp.composeapp.generated.resources.weibo_type_must_selected
import showcaseapp.composeapp.generated.resources.weibo_uid_tips_1
import showcaseapp.composeapp.generated.resources.weibo_uid_tips_2
import showcaseapp.composeapp.generated.resources.weibo_uid_tips_3
import showcaseapp.composeapp.generated.resources.weibo_uid_tips_4
import showcaseapp.composeapp.generated.resources.weibo_uid_tips_title

@Composable
fun WeiboConfigPage(
    weiboSource: WeiboSource? = null,
    onTestClick: suspend (WeiboSource) -> Result<Any>?,
    onSaveClick: suspend (WeiboSource) -> Unit
) {
    var uid by rememberSaveable(key = "uid") { mutableStateOf(weiboSource?.uid?:"") }
    var name by rememberSaveable(key = "name") { mutableStateOf(weiboSource?.name?.decodeName()?:"") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var nameValid by rememberSaveable(key = "nameValid") { mutableStateOf(true) }
    var containOrigin by rememberSaveable(key = "containOrigin") { mutableStateOf(weiboSource?.containOriginal?:true) }
    var repostSelected by rememberSaveable(key = "repostSelected") { mutableStateOf(weiboSource?.containRetweet?:false) }

    val selectValid by remember {
        derivedStateOf {
            containOrigin || repostSelected
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

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            label = {Text(text = stringResource(Res.string.source_name), style = TextStyle(fontWeight = FontWeight.Bold))},
            value = name,
            onValueChange = {
                name = it
                nameValid = checkName(it)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            placeholder = { HintText(text = "Weibo") },
            singleLine = true,
            isError = ! nameValid
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            value = uid,
            onValueChange = { 
                uid = it
                errorMessage = null
            },
            label = { Text("User ID") },
            placeholder = { Text("例如: 123456789") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = errorMessage != null,
            supportingText = errorMessage?.let { { Text(it) } }
        )
        Spacer(modifier = Modifier.height(16.dp))


        Row {

            FilterChip(
                modifier = Modifier.padding(4.dp, 6.dp),
                onClick = { containOrigin = !containOrigin },
                label = {
                    Text(stringResource(Res.string.weibo_original))
                },
                selected = containOrigin,
                leadingIcon = if (containOrigin) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Done,
                            contentDescription = "Select original weibo",
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else {
                    null
                },
            )

            FilterChip(
                modifier = Modifier.padding(4.dp, 6.dp),
                onClick = { repostSelected = !repostSelected },
                label = {
                    Text(stringResource(Res.string.weibo_retweet))
                },
                selected = repostSelected,
                leadingIcon = if (repostSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Done,
                            contentDescription = "Select retweet weibo",
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else {
                    null
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TipsCard()

        Spacer(modifier = Modifier.height(8.dp))

        var checkingState by remember { mutableStateOf(false) }

        Row {
            ElevatedButton(onClick = {
                if (!checkingState) {
                    scope.launch {
                        checkingState = true
                        try{
                            if (uid.isEmpty() || name.isEmpty()) {
                                ToastUtil.error(Res.string.name_is_invalid)
                            } else if (!selectValid){
                                ToastUtil.error(Res.string.weibo_type_must_selected)
                            } else{
                                checkingState = true
                                onTestClick.invoke(
                                    WeiboSource(
                                        name.encodeName(),
                                        uid
                                    )
                                )
                                checkingState = false
                            }
                        }catch (ex: Exception){
                            ex.printStackTrace()
                            ToastUtil.error(ex.message?:" Error")
                        }

                        checkingState = false
                    }
                }
            }, modifier = Modifier.padding(10.dp)){
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
                when {
                    uid.isBlank() -> {
                        errorMessage = "请输入用户UID"
                    }
                    name.isBlank() -> {
                        errorMessage = "请输入源名称"
                    }
                    !uid.matches(Regex("\\d+")) -> {
                        errorMessage = "UID应该是纯数字"
                    }
                    else -> {
                        if (selectValid){
                            isLoading = true
                            val weiboSource = WeiboSource(
                                uid = uid.trim(),
                                name = name.encodeName(),
                                containOriginal = containOrigin,
                                containRetweet = repostSelected
                            )

                            scope.launch {
                                onSaveClick(weiboSource)
                            }
                        }else {
                            ToastUtil.error(Res.string.weibo_type_must_selected)
                        }

                    }
                }
            }, modifier = Modifier.padding(10.dp)) {
                Text(text = stringResource(Res.string.save), maxLines = 1)
            }
        }
    }
}


@Composable
fun TextWithHyperlink(
    modifier: Modifier = Modifier,
    fullText: String,
    linkText: String,
    url: String
) {
    // 1. Build the AnnotatedString using the new withLink method
    val annotatedString = buildAnnotatedString {
        // Find the start and end indices of the link text
        val startIndex = fullText.indexOf(linkText)
        if (startIndex == -1) {
            // If link text is not found, append the full text as is
            append(fullText)
            return@buildAnnotatedString
        }
        val endIndex = startIndex + linkText.length

        // Append the text before the link
        append(fullText.substring(0, startIndex))

        // 2. Use `withLink` to attach a LinkAnnotation to a specific part of the text
        withLink(
            link = LinkAnnotation.Url(
                url = url,
                // You can optionally customize the style for different interaction states
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                )
            )
        ) {
            // This is the text that will be clickable
            append(linkText)
        }

        // Append the text after the link
        append(fullText.substring(endIndex))
    }

    // 3. Use a standard `Text` composable. It will automatically handle the click.
    // Wrap it in a SelectionContainer to allow users to copy text.
    SelectionContainer {
        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
    }
}

@Preview
@Composable
fun TipsCard(){
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(Res.string.weibo_uid_tips_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.weibo_uid_tips_1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(Res.string.weibo_uid_tips_2),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(Res.string.weibo_uid_tips_3),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val link = "https://weibo.com/u/1644225642"
            MaterialTheme {
                TextWithHyperlink(
                    fullText = stringResource(Res.string.weibo_uid_tips_4),
                    linkText = link,
                    url = link
                )
            }
        }
    }
}


// --- Example Usage ---
@Composable
private fun PreviewTextWithHyperlink() {
    val originalText = "National Geographic https://weibo.com/u/1644225642"
    val link = "https://weibo.com/u/1644225642"

    MaterialTheme {
        TextWithHyperlink(
            fullText = originalText,
            linkText = link,
            url = link
        )
    }
}