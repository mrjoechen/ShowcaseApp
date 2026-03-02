import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
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
import androidx.savedstate.read
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import com.alpha.showcase.common.addPlatformComponents
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.maxBitmapSize
import coil3.request.crossfade
import coil3.size.Size as CoilSize
import coil3.util.Logger
import coil3.util.Logger.Level
import com.alpha.showcase.common.components.BackHandler
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.theme.AppTheme
import com.alpha.showcase.common.toast.ToastHost
import com.alpha.showcase.common.ui.ext.handleBackKey
import com.alpha.showcase.common.ui.play.PlayPage
import com.alpha.showcase.common.ui.settings.SettingsListView
import com.alpha.showcase.common.ui.settings.SettingsViewModel
import com.alpha.showcase.common.ui.source.SourceListView
import com.alpha.showcase.common.ui.source.SourceViewModel
import com.alpha.showcase.common.ui.config.ConfigScreen
import com.alpha.showcase.common.ui.focusScaleEffect
import com.alpha.showcase.common.ui.view.DURATION_ENTER
import com.alpha.showcase.common.ui.view.DURATION_EXIT
import com.alpha.showcase.common.ui.view.LottieAssetLoader
import com.alpha.showcase.common.ui.view.rememberMobileHaptic
import com.alpha.showcase.common.ui.vm.UiState
import com.alpha.showcase.common.utils.Log
import com.alpha.showcase.common.utils.Supabase
import com.alpha.showcase.common.utils.decodeBase64UrlSafe
import com.alpha.showcase.common.utils.encodeBase64UrlSafe
import io.github.vinceglb.confettikit.compose.ConfettiKit
import io.github.vinceglb.confettikit.core.Angle
import io.github.vinceglb.confettikit.core.Party
import io.github.vinceglb.confettikit.core.Position
import io.github.vinceglb.confettikit.core.Spread
import io.github.vinceglb.confettikit.core.emitter.Emitter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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

val LocalImageLoader = compositionLocalOf<ImageLoader?> {
    error("Please provide ImageLoader!")
}

@Composable
@Preview
fun MainApp() {

    var showLaunchAnimation by remember {
        mutableStateOf(true)
    }


    var firstOpen by remember {
        mutableStateOf(true)
    }
    LaunchedEffect(Unit) {
        Supabase.test()
    }

    val generalPreference = SettingsViewModel.generalPreferenceFlow.collectAsState()

    val context = LocalPlatformContext.current

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .crossfade(true)
            .maxBitmapSize(CoilSize(2560, 2560))
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(4))
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(imageCache)
                    .maxSizeBytes((generalPreference.value as UiState.Content).data.cacheSize * 1024 * 1024L)
                    .build()
            }
            .logger(
                object : Logger {
                    override var minLevel = Level.Info

                    override fun log(
                        tag: String,
                        level: Logger.Level,
                        message: String?,
                        throwable: Throwable?
                    ) {
//                        message?.apply {
//                            Log.i(message)
//                        }

                        throwable?.apply {
                            throwable.printStackTrace()
                        }
                    }
                }
            )
            .components {
                add(SvgDecoder.Factory())
                addPlatformComponents()
            }
            .build()
    }

    setSingletonImageLoaderFactory { _ ->
        imageLoader
    }

    AppTheme {
        val scope = rememberCoroutineScope()
        CompositionLocalProvider(LocalImageLoader provides imageLoader) {

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
                        "${Screen.Play.route}/{sourceName}",
                        arguments = listOf(navArgument("sourceName") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val sourceName = remember(backStackEntry) {
                            runCatching {
                                backStackEntry.arguments
                                    ?.read { getStringOrNull("sourceName") }
                                    ?.takeIf { it.isNotBlank() }
                                    ?.decodeBase64UrlSafe()
                            }.getOrNull().orEmpty()
                        }
                        val source = remember<RemoteApi?>(sourceName) {
                            if (sourceName.isBlank()) null else SourceViewModel.getSource(sourceName)
                        }
                        if (source == null) {
                            LaunchedEffect(sourceName) {
                                navController.popBackStack()
                            }
                        } else {
                            PlayPage(source) {
                                if (navController.currentBackStackEntry?.destination?.route?.startsWith(
                                        Screen.Play.route
                                    ) == true
                                ) {
                                    navController.popBackStack()
                                }

                                if (SettingsViewModel.generalPreferenceFlow.value is UiState.Content) {
                                    val preference =
                                        (SettingsViewModel.generalPreferenceFlow.value as UiState.Content).data
                                    scope.launch {
                                        SettingsViewModel.updatePreference(
                                            preference.copy(
                                                latestSource = source.name
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    composable(
                        "${Screen.Config.route}/{type}?sourceName={sourceName}",
                        arguments = listOf(
                            navArgument("type") { type = NavType.IntType },
                            navArgument("sourceName") {
                                type = NavType.StringType; defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val configType =
                            backStackEntry.arguments?.read { getIntOrNull("type") } ?: 0
                        val sourceName = runCatching {
                            backStackEntry.arguments
                                ?.read { getStringOrNull("sourceName") }
                                ?.takeIf { it.isNotBlank() }
                                ?.decodeBase64UrlSafe()
                        }.getOrNull().orEmpty()
                        val editSource = remember(sourceName) {
                            if (sourceName.isBlank()) null else SourceViewModel.getSource(sourceName)
                        }
                        ConfigScreen(type = configType, editSource = editSource) {
                            navController.popBackStack()
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    SettingsViewModel.settingsFlow.combine(SettingsViewModel.generalPreferenceFlow) { settings, preference ->
                        if (settings is UiState.Content && preference is UiState.Content) {
                            settings.data.autoOpenLatestSource to preference.data.latestSource
                        } else false to ""
                    }.collectLatest {
                        val (autoOpen, latestSource) = it
                        if (autoOpen && latestSource.isNotBlank() && firstOpen) {
                            Log.d("autoOpen: $autoOpen, latestSource: $latestSource")
                            SourceViewModel.getSource(latestSource)?.apply {
                                navController.navigate("${Screen.Play.route}/${name.encodeBase64UrlSafe()}")
                            }
                            firstOpen = false
                        }
                    }
                }
                ToastHost()
            }

            FadeAnimatedVisibility(showLaunchAnimation) {
                LaunchAnimationScreen {
                    showLaunchAnimation = false
                }
            }
        }
    }
}

@Composable
fun LaunchAnimationScreen(onFinished: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ){
            LottieAssetLoader(
                lottieAsset = "lottie/lottie_launch.json",
                modifier = Modifier.fillMaxSize(0.3f),
                iterations = 1,
                contentScale = ContentScale.Fit,
                onFinished = onFinished
            )
        }
    }
}

@Composable
fun FadeAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    durationMillis: Int = 400,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(durationMillis = durationMillis)),
        exit = fadeOut(animationSpec = tween(durationMillis = durationMillis)),
        content = content
    )
}

@Composable
@Preview
fun HomePage(nav: NavController) {
    val greeting = remember {
        val greet = Greeting().greet()
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
    val basicHorizontalPadding by remember { mutableStateOf(if (isWeb() || isDesktop()) 20.dp else 0.dp) }
    val topPadding = if (isIos()) max(displayCutoutTop, statusBars) else 26.dp
    var showConfetti by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val logoScale by animateFloatAsState(if (isHovered) 1.05f else 1f)
    val settingIconScale by animateFloatAsState(if (settingSelected) 1.1f else 1f)
    val performHaptic = rememberMobileHaptic()

    var vertical by remember { mutableStateOf(false) }

    val horizontalPadding =
        if (isIos() && !vertical) basicHorizontalPadding + rememberCutout else basicHorizontalPadding

    Scaffold(
        modifier = Modifier.fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val width = coordinates.size.width
                val height = coordinates.size.height
                val isVertical = width <= height
                if (vertical != isVertical) {
                    vertical = isVertical
                }
            },
        topBar = {
            Surface {
                Row(
                    Modifier.fillMaxWidth()
                        .padding(horizontalPadding, topPadding, horizontalPadding, 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    Surface(
                        Modifier
                            .padding(16.dp, 12.dp).scale(logoScale),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            modifier = Modifier
                                .focusScaleEffect(
                                    enableShimmer = true,
                                    interactionSource = interactionSource
                                ).clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) {
                                    performHaptic()
                                    currentDestination = Screen.Sources
                                    showConfetti = true
                                }.padding(10.dp, 4.dp),
                            text = stringResource(Res.string.app_name),
                            fontSize = 32.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                        )

                    }
                    val rotation by animateFloatAsState(
                        targetValue = if (settingSelected) 90f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "icon rotation"
                    )
                    Surface(
                        Modifier.padding(12.dp, 0.dp).scale(settingIconScale),
                        shape = CircleShape,
                        tonalElevation = if (settingSelected) 1.dp else 0.dp,
                        shadowElevation = if (settingSelected) 1.dp else 0.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .clickable {
                                    performHaptic()
                                    currentDestination = if (!settingSelected) {
                                        Screen.Settings
                                    } else {
                                        Screen.Sources
                                    }
                                }
                                .handleBackKey {
                                    currentDestination = Screen.Sources
                                }
                                .padding(10.dp)) {
                            Icon(
                                modifier = Modifier.rotate(rotation),
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
                if (currentDestination != Screen.Sources) {
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
                        SourceListView(navController = nav) {
                            nav.navigate("${Screen.Play.route}/${it.name.encodeBase64UrlSafe()}")
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
            parties = listOf(
                Party(
                    speed = 0f,
                    maxSpeed = 15f,
                    damping = 0.9f,
                    angle = Angle.BOTTOM,
                    spread = Spread.ROUND,
                    colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
                    emitter = Emitter(duration = 5.seconds).perSecond(100),
                    position = Position.Relative(0.0, 0.0).between(Position.Relative(1.0, 0.0))
                )
            ),
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
    data object Sources :
        Screen("sources", Res.string.sources, Icons.Outlined.Folder, Icons.Filled.Folder)

    data object Settings :
        Screen("settings", Res.string.settings, Icons.Outlined.Settings, Icons.Filled.Settings)

    data object Play :
        Screen("Play", Res.string.auto_play, Icons.Outlined.PlayArrow, Icons.Filled.PlayArrow)

    data object Home :
        Screen("Home", Res.string.home, Icons.Outlined.Home, Icons.Filled.Home)

    data object Config :
        Screen("Config", Res.string.settings, Icons.Outlined.Settings, Icons.Filled.Settings)
}

val navItems = listOf(
    Screen.Sources,
    Screen.Settings,
)
