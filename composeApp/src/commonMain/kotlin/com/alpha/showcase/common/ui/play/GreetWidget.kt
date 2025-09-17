@file:OptIn(ExperimentalTime::class)

package com.alpha.showcase.common.ui.play

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.ExperimentalTime

@Preview
@Composable
fun GreetingWidget() {
    val greeting = remember { getGreeting() }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = greeting)
    }
}

fun getGreeting(): String {
    val hourOfDay = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time.hour
    return when {
        hourOfDay in 6..11 -> "Good morning"
        hourOfDay < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}