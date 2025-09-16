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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.alpha.showcase.common.networkfile.storage.remote.GiteeSource
import com.alpha.showcase.common.theme.Dimen
import com.alpha.showcase.common.utils.checkPath
import com.alpha.showcase.common.utils.checkUrl
import com.alpha.showcase.common.utils.decodeName
import com.alpha.showcase.common.utils.encodeName
import com.alpha.showcase.common.utils.isBranchNameValid
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.branche_name
import showcaseapp.composeapp.generated.resources.gitee_access_token_tips_title
import showcaseapp.composeapp.generated.resources.gitee_acess_token_tips_1
import showcaseapp.composeapp.generated.resources.gitee_acess_token_tips_2
import showcaseapp.composeapp.generated.resources.gitee_acess_token_tips_3
import showcaseapp.composeapp.generated.resources.gitee_acess_token_tips_4
import showcaseapp.composeapp.generated.resources.gitee_acess_token_tips_5
import showcaseapp.composeapp.generated.resources.leave_blank_is_default_branch
import showcaseapp.composeapp.generated.resources.name_require_hint
import showcaseapp.composeapp.generated.resources.repo_name
import showcaseapp.composeapp.generated.resources.repo_owner
import showcaseapp.composeapp.generated.resources.repo_url_require_hint
import showcaseapp.composeapp.generated.resources.save
import showcaseapp.composeapp.generated.resources.sub_folder_hint
import showcaseapp.composeapp.generated.resources.test_connection


@Composable
fun GiteeConfigPage(
    giteeSource: GiteeSource? = null,
    onTestClick: suspend (GiteeSource) -> Result<Any>?,
    onSaveClick: suspend (GiteeSource) -> Unit
) {

    var name by rememberSaveable(key = "name") {
        mutableStateOf(giteeSource?.name?.decodeName() ?: "")
    }
    var repoUrl by rememberSaveable(key = "repoUrl") {
        mutableStateOf(giteeSource?.repoUrl ?: "")
    }

    var repoUrlValid by rememberSaveable(key = "repoUrlValid") {
        mutableStateOf(true)
    }

    var owner by rememberSaveable(key = "owner") {
        mutableStateOf(giteeSource?.repoUrl?.let {
            getGiteeOwnerAndRepo(it)?.first ?: ""
        } ?: "")
    }

    var repo by rememberSaveable(key = "repo") {
        mutableStateOf(giteeSource?.repoUrl?.let {
            getGiteeOwnerAndRepo(it)?.second ?: ""
        } ?: "")
    }

    var token by rememberSaveable(key = "access token") {
        mutableStateOf(
             giteeSource?.token ?: ""
        )
    }
    var path by rememberSaveable(key = "path") {
        mutableStateOf(giteeSource?.path ?: "")
    }

    var pathValid by rememberSaveable(key = "pathValid") {
        mutableStateOf(true)
    }

    var ownerValid by rememberSaveable(key = "ownerValid") {
        mutableStateOf(true)
    }

    var repoValid by rememberSaveable(key = "repoValid") {
        mutableStateOf(true)
    }

    var branchName by rememberSaveable(key = "branchName") {
        mutableStateOf(giteeSource?.branchName ?: "")
    }

    var branchValid by rememberSaveable(key = "branchValid") {
        mutableStateOf(true)
    }

    val focusRequester = remember { FocusRequester() }

    var showAccessTokenDialog by rememberSaveable(key = "showAccessTokenDialog") {
        mutableStateOf(
            false
        )
    }

    val editMode = giteeSource != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var checkingState by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        OutlinedTextField(
            modifier = Modifier.focusRequester(focusRequester),
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            value = name,
            onValueChange = {
                name = it
            },
            singleLine = true,
            label = { Text(stringResource(Res.string.name_require_hint)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            value = repoUrl,
            onValueChange = {
                repoUrl = it
                if (name.isEmpty()) {
                    if (it.isNotBlank() && isGiteeRepoUrl(it)) {
                        name = it.substringAfterLast("/").substringBeforeLast(".") // remove .git
                    }
                }

                if (isGiteeRepoUrl(it)) {
                    getGiteeOwnerAndRepo(it)?.run {
                        owner = first
                        repo = second
                        ownerValid = isValidOwner(owner)
                        repoValid = isValidRepoName(repo)
                    }
                    repoUrlValid = true
                } else {
                    repoUrlValid = false
                }
            },
            isError = !repoUrlValid,
            label = { Text(stringResource(Res.string.repo_url_require_hint)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            value = owner,
            onValueChange = {
                owner = it
                generateGiteeRepoUrl(owner, repo).apply {
                    repoUrl = this
                    repoUrlValid = isGiteeRepoUrl(repoUrl)
                }
                ownerValid = isValidOwner(it)
            },
            isError = !ownerValid,
            label = { Text(stringResource(Res.string.repo_owner)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            value = repo,
            onValueChange = {
                repo = it
                generateGiteeRepoUrl(owner, repo).apply {
                    repoUrl = this
                    repoUrlValid = isGiteeRepoUrl(repoUrl)
                }

                repoValid = isValidRepoName(it)
            },
            isError = !repoValid,
            label = { Text(stringResource(Res.string.repo_name)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
        )


        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            value = branchName,
            onValueChange = {
                branchName = it.trim()
                isBranchNameValid(branchName).let { valid ->
                    branchValid = valid || branchName.isBlank()
                }
            },
            isError = !branchValid,
            label = { Text(stringResource(Res.string.branche_name) + stringResource(Res.string.leave_blank_is_default_branch)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            value = path,
            onValueChange = {
                path = it.trim()
                checkPath(path).let { valid ->
                    pathValid = valid
                }
            },
            isError = !pathValid,
            label = { Text(stringResource(Res.string.sub_folder_hint)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
        )


        OutlinedTextField(
            shape = RoundedCornerShape(Dimen.textFiledCorners),
            label = { Text("Access Token", style = TextStyle(fontWeight = FontWeight.Bold)) },
            value = token,
            onValueChange = {
                token = it
            },
            placeholder = {Text(text = "")},
            trailingIcon = {
                IconButton(onClick = {
                    showAccessTokenDialog = true
                }) {
                    Icon(Icons.Outlined.Info, contentDescription = "Gitee Access Token")
                }
            },
            singleLine = true,
            modifier = Modifier.padding(16.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row {
            ElevatedButton(onClick = {
                if (!checkingState && checkUrl(repoUrl, true)) {
                    scope.launch {
                        checkingState = true
                        onTestClick.invoke(
                            GiteeSource(
                                name.encodeName(),
                                repoUrl,
                                token,
                                path,
                                branchName
                            )
                        )
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
                    if (checkUrl(repoUrl, true)) {
                        onSaveClick.invoke(
                            GiteeSource(
                                name.encodeName(),
                                repoUrl,
                                token,
                                path,
                                branchName
                            )
                        )
                    }
                }
            }, modifier = Modifier.padding(10.dp)) {
                Text(text = stringResource(Res.string.save), maxLines = 1)
            }
        }


        Spacer(modifier = Modifier.height(32.dp))

    }


    if (showAccessTokenDialog) {
        GiteeAccessTokenDialog {
            showAccessTokenDialog = false
        }
    }
    if (!editMode) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}


@Composable
fun GiteeAccessTokenDialog(onDismiss: () -> Unit = {}) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.gitee_access_token_tips_title),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                Modifier
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {

                Divider(color = Color.Gray.copy(0.3f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(Res.string.gitee_acess_token_tips_1))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(Res.string.gitee_acess_token_tips_2))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(Res.string.gitee_acess_token_tips_3))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(Res.string.gitee_acess_token_tips_4))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(Res.string.gitee_acess_token_tips_5))
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color.Gray.copy(0.3f), thickness = 0.5.dp)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // do something with the access token
                    onDismiss()
                }
            ) {
                Text(text = "OK")
            }
        }
    )
}


private fun isGiteeRepoUrl(url: String): Boolean {
    // 匹配Gitee仓库链接的正则表达式
    val pattern = Regex("^https?://gitee\\.com/([A-Za-z0-9-]+)/([A-Za-z0-9-_]+)$")
    return pattern.matches(url)
}

private fun getGiteeRepoName(url: String): String? {
    val pattern = Regex("^https?://gitee\\.com/([A-Za-z0-9-]+)/([A-Za-z0-9-_]+)$")
    val matchResult = pattern.find(url)
    return matchResult?.groupValues?.get(2)
}

private fun getGiteeOwnerAndRepo(repoUrl: String): Pair<String, String>? {
    val regex = "https://gitee.com/(.*)/(.*)".toRegex()
    val matchResult = regex.find(repoUrl)

    if (matchResult != null) {
        val owner = matchResult.groupValues[1]
        val repo = matchResult.groupValues[2]
        return owner to repo
    }
    return null
}


fun generateGiteeRepoUrl(owner: String, repoName: String): String {
    val validOwner = isValidOwner(owner)
    val validRepoName = isValidRepoName(repoName)
//    if (! validOwner || ! validRepoName) {
//        return null
//    }
    return "https://gitee.com/${if (validOwner) owner else ""}/${if (validRepoName) repoName else ""}"
}

