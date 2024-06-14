package com.alpha.showcase.common.ui.view

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
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