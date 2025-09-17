@file:OptIn(ExperimentalTime::class)

package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.ExperimentalTime


val time_card_position_landscape =
    listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)

@Preview
@Composable
fun TimeCard() {

    var position by remember {
        mutableStateOf(Alignment.BottomEnd)
    }

    var date by remember {
        mutableStateOf(
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        )
    }

    var time by remember {
        mutableStateOf(
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time.toFormatString()
        )
    }

    var dayOfWeek by remember {
        mutableStateOf(
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).dayOfWeek.toString()
        )
    }

    var i by remember {
        mutableLongStateOf(0L)
    }


    LaunchedEffect(Unit) {
        while (true) {
            delay(10000)
            date =
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

            dayOfWeek = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).dayOfWeek.toString()

            time =
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time.toFormatString()
            i++


            if (i % 30 == 0L) {
                position = time_card_position_landscape.random()
                i = 0
            }
        }
    }

    AnimatedContent(
        targetState = position,
        transitionSpec = {
            fadeIn(animationSpec = tween(1000, delayMillis = 500))
                .togetherWith(fadeOut(animationSpec = tween(800)))
        },
        label = "position anim"
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = it) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
                modifier = Modifier
                    .padding(30.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = date,
                        modifier = Modifier.padding(4.dp),
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        text = dayOfWeek,
                        modifier = Modifier.padding(6.dp),
                        style = MaterialTheme.typography.titleSmall
                    )

                    Text(
                        text = time,
                        modifier = Modifier.padding(4.dp),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = .6f),
                                blurRadius = 30f,
                            )
                        )
                    )
                }
            }
        }
    }

}

fun LocalTime.toFormatString() = "${hour}:${if (minute < 10) "0$minute" else minute}"