import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.alpha.showcase.common.components.BackHandler
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
import com.alpha.showcase.common.theme.AppTheme
import com.alpha.showcase.common.ui.ext.handleBackKey
import com.alpha.showcase.common.ui.play.PlayPage
import com.alpha.showcase.common.ui.settings.SettingsListView
import com.alpha.showcase.common.ui.source.SourceListView
import com.alpha.showcase.common.ui.view.DURATION_ENTER
import com.alpha.showcase.common.ui.view.DURATION_EXIT
import com.alpha.showcase.common.utils.Supabase
import com.valentinilk.shimmer.shimmer
import io.ktor.util.decodeBase64String
import io.ktor.util.encodeBase64
import kotlinx.serialization.encodeToString
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.app_name
import showcaseapp.composeapp.generated.resources.auto_play
import showcaseapp.composeapp.generated.resources.home
import showcaseapp.composeapp.generated.resources.settings
import showcaseapp.composeapp.generated.resources.sources

@Composable
@Preview
fun MainApp() {

    LaunchedEffect(Unit){
        Supabase.test()
    }

    AppTheme {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            Modifier.fillMaxSize()
        ) {
            composable(Screen.Home.route) {
                HomePage(navController)
            }
            composable("${Screen.Play.route}/{source}", arguments = listOf(navArgument("source") { type = NavType.StringType })) { backStackEntry ->
                val sourceJson = remember(backStackEntry) {
                    backStackEntry.arguments?.getString("source")?.decodeBase64String() ?: "{}"
                }
                val source = remember<RemoteApi>(sourceJson) {
                    StorageSourceSerializer.sourceJson.decodeFromString(sourceJson)
                }
                PlayPage(source) {
                    if (navController.currentBackStackEntry?.destination?.route?.startsWith(Screen.Play.route) == true) {
                        navController.popBackStack()
                    }
                }
            }
        }
    }
}

@Composable
@Preview
fun HomePage(nav: NavController) {
    val greeting = remember { val greet = Greeting().greet()
        println(greet)
        greet
    }

    var currentDestination by remember {
        mutableStateOf<Screen>(Screen.Sources)
    }
    val settingSelected by remember {
        derivedStateOf { currentDestination == Screen.Settings }
    }

    val horizontalPadding  by remember { mutableStateOf(if (isWeb() || isDesktop()) 20.dp else 0.dp) }
    val verticalPadding  by remember { mutableStateOf(32.dp) }

    Scaffold(topBar = {
        Surface {
            Row(
                Modifier.fillMaxWidth().padding(horizontalPadding, verticalPadding, horizontalPadding, 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    Modifier.padding(20.dp).shimmer(),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        modifier = Modifier.clickable {
                            currentDestination = Screen.Sources
                        }.padding(10.dp, 5.dp),
                        text = stringResource(Res.string.app_name),
                        fontSize = 32.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                    )

                }

                Surface(
                    Modifier.padding(12.dp, 0.dp),
                    shape = RoundedCornerShape(6.dp),
                    tonalElevation = if (settingSelected) 1.dp else 0.dp,
                    shadowElevation = if (settingSelected) 1.dp else 0.dp
                ) {
                    Box(modifier = Modifier
                        .clickable {
                            currentDestination = if (!settingSelected){
                                Screen.Settings
                            }else {
                                Screen.Sources
                            }
                        }
                        .handleBackKey {
                            currentDestination = Screen.Sources
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
            BackHandler(onBack = {
                if (currentDestination != Screen.Sources){
                    currentDestination = Screen.Sources
                    return@BackHandler true
                }
                false
            })
            Column(
                Modifier.fillMaxSize().padding(horizontalPadding, 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                AnimatedVisibility(
                    !settingSelected,
                    enter = fadeIn(animationSpec = tween(DURATION_ENTER)),
                    exit = fadeOut(animationSpec = tween(DURATION_EXIT))
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SourceListView {
                            nav.navigate(
                                "${Screen.Play.route}/${
                                    StorageSourceSerializer.sourceJson.encodeToString(
                                        it
                                    ).encodeBase64()
                                }"
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    settingSelected,
                    enter = fadeIn(animationSpec = tween(DURATION_ENTER)),
                    exit = fadeOut(animationSpec = tween(DURATION_EXIT))
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SettingsListView()
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
    data object Play :
        Screen("Play", Res.string.auto_play, Icons.Outlined.PlayArrow, Icons.Filled.PlayArrow)
    data object Home :
        Screen("Home", Res.string.home, Icons.Outlined.Home, Icons.Filled.Home)
}

val navItems = listOf(
    Screen.Sources,
    Screen.Settings,
)