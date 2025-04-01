import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.alpha.showcase.common.components.BackHandler
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
import com.alpha.showcase.common.theme.AppTheme
import com.alpha.showcase.common.toast.ToastHost
import com.alpha.showcase.common.ui.ext.handleBackKey
import com.alpha.showcase.common.ui.play.PlayPage
import com.alpha.showcase.common.ui.settings.SettingsListView
import com.alpha.showcase.common.ui.source.SourceListView
import com.alpha.showcase.common.ui.view.DURATION_ENTER
import com.alpha.showcase.common.ui.view.DURATION_EXIT
import com.alpha.showcase.common.ui.view.animatedComposable
import com.alpha.showcase.common.utils.Log
import com.alpha.showcase.common.utils.Supabase
import com.valentinilk.shimmer.shimmer
import io.github.vinceglb.confettikit.compose.ConfettiKit
import io.github.vinceglb.confettikit.core.Angle
import io.github.vinceglb.confettikit.core.Party
import io.github.vinceglb.confettikit.core.Position
import io.github.vinceglb.confettikit.core.Spread
import io.github.vinceglb.confettikit.core.emitter.Emitter
import io.ktor.util.decodeBase64String
import io.ktor.util.encodeBase64
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.app_name
import showcaseapp.composeapp.generated.resources.auto_play
import showcaseapp.composeapp.generated.resources.home
import showcaseapp.composeapp.generated.resources.settings
import showcaseapp.composeapp.generated.resources.sources
import kotlin.time.Duration.Companion.seconds


val imageCache = getPlatform().getConfigDirectory().toPath().resolve("image_cache")

@Composable
@Preview
fun MainApp() {

    LaunchedEffect(Unit){
        Supabase.test()
    }

    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(imageCache)
                    .maxSizePercent(0.05)
                    .build()
            }
            .build()
    }

    AppTheme {
        val navController = rememberNavController()

        Box(
            Modifier.fillMaxSize()
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                Modifier.fillMaxSize()
            ) {
                composable(
                    Screen.Home.route
                ) {
                    HomePage(navController)
                }
                composable(
                    "${Screen.Play.route}/{source}",
                    arguments = listOf(navArgument("source") { type = NavType.StringType })
                ) { backStackEntry ->
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
            ToastHost()
        }
    }
}

@Composable
@Preview
fun HomePage(nav: NavController) {
    val greeting = remember { val greet = Greeting().greet()
        Log.d(greet)
        greet
    }
    var currentDestination by remember {
        mutableStateOf<Screen>(Screen.Sources)
    }
    val settingSelected by remember {
        derivedStateOf { currentDestination == Screen.Settings }
    }

    val density = LocalDensity.current
    // 获取顶部安全区域的高度 (推荐方式，包含状态栏和刘海)
    val displayCutoutTop = (WindowInsets.displayCutout.getTop(density) / density.density).dp
    val statusBars = (WindowInsets.statusBars.getTop(density) / density.density).dp
    val rememberCutout by remember { mutableStateOf(displayCutoutTop) }
    val basicHorizontalPadding  by remember { mutableStateOf(if (isWeb() || isDesktop()) 20.dp else 0.dp) }
    val topPadding = if (isIos()) max(displayCutoutTop, statusBars) else 24.dp
    var showConfetti by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val logoScale by animateFloatAsState(if (isHovered) 1.1f else 1f)

    var vertical by remember { mutableStateOf(false) }

    val horizontalPadding = if (isIos() && !vertical)  basicHorizontalPadding + rememberCutout else basicHorizontalPadding

    Scaffold(
        modifier = Modifier.fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val width = coordinates.size.width
                val height = coordinates.size.height
                Log.d("width: $width, height: $height")
                if (width > height) {
                    vertical = false
                } else {
                    vertical = true
                }
            },
        topBar = {
        Surface {
            Row(
                Modifier.fillMaxWidth().padding(horizontalPadding, topPadding, horizontalPadding, 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    Modifier
                        .padding(16.dp, 12.dp)
                        .shimmer().scale(logoScale),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        modifier = Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = if (isDesktop()) null else LocalIndication.current
                        ) {
                            currentDestination = Screen.Sources
                            showConfetti = true
                        }.padding(10.dp, 5.dp),
                        text = stringResource(Res.string.app_name),
                        fontSize = 36.sp,
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

    AnimatedVisibility(showConfetti) {
        ConfettiKit(
            modifier = Modifier.fillMaxSize(),
            parties = listOf(Party(
                speed = 0f,
                maxSpeed = 15f,
                damping = 0.9f,
                angle = Angle.BOTTOM,
                spread = Spread.ROUND,
                colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
                emitter = Emitter(duration = 5.seconds).perSecond(100),
                position = Position.Relative(0.0, 0.0).between(Position.Relative(1.0, 0.0))
            )),
            onParticleSystemEnded = { _, activeSystems ->
                if (activeSystems == 0 && showConfetti) {
                    showConfetti = false
                }
            },
        )
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