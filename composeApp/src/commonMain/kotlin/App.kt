import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.FadeTransition
import com.alpha.showcase.common.theme.AppTheme
import com.alpha.showcase.common.ui.settings.SettingsListView
import com.alpha.showcase.common.ui.source.SourceListView
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.app_name
import showcaseapp.composeapp.generated.resources.settings
import showcaseapp.composeapp.generated.resources.sources

@Composable
@Preview
fun MainApp() {
    Navigator(HomeScreen()){navigator->
        FadeTransition(navigator)
    }
}

@Composable
@Preview
fun HomePage() {
    AppTheme {
        val greeting = remember { val greet = Greeting().greet()
            println(greet)
            greet
        }

        var currentDestination by remember {
            mutableStateOf<Screen>(Screen.Sources)
        }
        var settingSelected by remember {
            mutableStateOf(currentDestination == Screen.Settings)
        }

        Scaffold(topBar = {
            Surface {
                Row(
                    Modifier.padding(0.dp, 24.dp, 0.dp, 0.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        Modifier.padding(16.dp, 20.dp),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Box(modifier = Modifier.clickable(interactionSource = MutableInteractionSource(), indication = null) {
                            currentDestination = Screen.Sources
                        }) {
                            Text(
                                modifier = Modifier.padding(20.dp, 10.dp),
                                text = stringResource(Res.string.app_name),
                                fontSize = 32.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold
                            )
                        }

                    }

                    Surface(
                        Modifier.padding(20.dp, 0.dp),
                        shape = RoundedCornerShape(6.dp),
                        tonalElevation = if (settingSelected) 1.dp else 0.dp,
                        shadowElevation = if (settingSelected) 1.dp else 0.dp
                    ) {
                        Box(modifier = Modifier
                            .clickable {
                                settingSelected = !settingSelected
                                if (settingSelected){
                                    currentDestination = Screen.Settings
                                }else {
                                    currentDestination = Screen.Sources
                                }
                            }
                            .padding(10.dp)) {
                            Icon(
                                imageVector = if (settingSelected) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = Screen.Settings.route,
                                tint = if (settingSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }

                    }

                }
            }

        }) {

            Surface {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedVisibility(settingSelected, enter = fadeIn(), exit = fadeOut()) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            SettingsListView()
                        }
                    }

                    AnimatedVisibility(!settingSelected, enter = fadeIn(), exit = fadeOut()) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            SourceListView()
                        }

                    }
                }
            }
        }


    }
}


sealed class Screen(
    val route: String,
    val resourceString: StringResource,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    data object Sources : Screen("sources", Res.string.sources, Icons.Outlined.Folder, Icons.Filled.Folder)
    data object Settings :
        Screen("settings", Res.string.settings, Icons.Outlined.Settings, Icons.Filled.Settings)
}

val navItems = listOf(
    Screen.Sources,
    Screen.Settings,
)