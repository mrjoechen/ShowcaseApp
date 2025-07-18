package com.alpha.showcase.common.ui.view

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp

@Composable
fun TextTitleLarge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color =  MaterialTheme.colorScheme.primary
) {
    Text(
        text = text,
        color = color,
        modifier = modifier
            .padding(horizontal = 18.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun TextTitleMedium(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = text,
        color = color,
        modifier = modifier
            .padding(horizontal = 18.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun TextTitleSmall(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = text,
        color = color,
        modifier = modifier
            .padding(horizontal = 18.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center
    )
}

@Composable
fun TextTitleLarge(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = text,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
fun TextTitleMedium(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = text,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
fun HintText(text: String){
    Text(text = text, color = if (isSystemInDarkTheme()) Color.DarkGray else Color.LightGray)
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
            link = androidx.compose.ui.text.LinkAnnotation.Url(
                url = url,
                // You can optionally customize the style for different interaction states
                styles = androidx.compose.ui.text.TextLinkStyles(
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