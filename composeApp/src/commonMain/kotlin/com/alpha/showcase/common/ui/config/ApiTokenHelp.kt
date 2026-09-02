package com.alpha.showcase.common.ui.config

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.ui.view.TextWithHyperlink
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.get_token
import showcaseapp.composeapp.generated.resources.pexels_api_key_help
import showcaseapp.composeapp.generated.resources.tmdb_api_token_help
import showcaseapp.composeapp.generated.resources.unsplash_api_key_help

enum class ApiTokenProvider(val tokenUrl: String) {
    Tmdb("https://www.themoviedb.org/settings/api"),
    Unsplash("https://unsplash.com/oauth/applications"),
    Pexels("https://www.pexels.com/api/key/"),
}

@Composable
fun ApiTokenHelp(
    provider: ApiTokenProvider,
    modifier: Modifier = Modifier,
) {
    val linkText = stringResource(Res.string.get_token)
    val fullText = when (provider) {
        ApiTokenProvider.Tmdb -> stringResource(Res.string.tmdb_api_token_help)
        ApiTokenProvider.Unsplash -> stringResource(Res.string.unsplash_api_key_help)
        ApiTokenProvider.Pexels -> stringResource(Res.string.pexels_api_key_help)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextWithHyperlink(
            modifier = Modifier.width(280.dp),
            fullText = fullText,
            linkText = linkText,
            url = provider.tokenUrl,
            textAlign = TextAlign.Center,
        )
    }
}
