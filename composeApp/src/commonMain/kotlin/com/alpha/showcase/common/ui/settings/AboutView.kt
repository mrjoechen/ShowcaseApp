package com.alpha.showcase.common.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowCircleUp
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.showcase.common.gitHash
import com.alpha.showcase.common.update.AppUpdateManager
import com.alpha.showcase.common.update.UpdateCheckResult
import com.alpha.showcase.common.update.UpdateInfo
import com.alpha.showcase.common.ui.dialog.FeedbackDialog
import com.alpha.showcase.common.ui.view.IconItem
import com.alpha.showcase.common.ui.view.rememberMobileHaptic
import com.alpha.showcase.common.utils.Analytics
import com.alpha.showcase.common.utils.ToastUtil
import com.alpha.showcase.common.versionCode
import com.alpha.showcase.common.versionName
import isAndroid
import isIos
import isMobile
import isWeb
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.app_update_failed
import showcaseapp.composeapp.generated.resources.cancel
import showcaseapp.composeapp.generated.resources.check_for_update
import showcaseapp.composeapp.generated.resources.feedback
import showcaseapp.composeapp.generated.resources.ic_telegram_app
import showcaseapp.composeapp.generated.resources.loading
import showcaseapp.composeapp.generated.resources.new_update_release
import showcaseapp.composeapp.generated.resources.open_source_license
import showcaseapp.composeapp.generated.resources.privacy_policy
import showcaseapp.composeapp.generated.resources.rate
import showcaseapp.composeapp.generated.resources.share
import showcaseapp.composeapp.generated.resources.showcase_about
import showcaseapp.composeapp.generated.resources.telegram_channel
import showcaseapp.composeapp.generated.resources.thanks
import showcaseapp.composeapp.generated.resources.up_to_date
import showcaseapp.composeapp.generated.resources.update

/**
 *  - About
 *      - Readme
 *      - License
 *      - ChangeLog
 *      - Telegram
 *      - Thanks (Resources and Open Source Libraries)
 *      - Rate
 *      - Feedback
 *      - Donate
 *      - Version
 */


private const val play_store = "https://play.google.com/store/apps/details?id=com.alpha.showcase"
private const val app_store = "https://apps.apple.com/cn/app/id6744004121"


private const val resUrl = "https://github.com/mrjoechen/ShowcaseApp/blob/main/README.md"
private const val telegramChannelUrl = "https://t.me/showcase_app_release"
private const val privacyPolicyUrl = "https://mrjoechen.github.io/ShowcaseApp/privacypolicy"

const val GPL_V3 = "GNU General Public License v3.0"
const val GPL_V2 = "GNU General Public License v2.0"
const val APACHE_V2 = "Apache License 2.0"
const val MIT = "MIT License"
const val UNLICENSE = "The Unlicense"
const val BSD = "BSD 3-Clause License"
const val OFL = "SIL Open Font License 1.1"
const val ISC = "ISC License"
const val SERVICE_TOS = "Service API (Terms of Use)"
const val COMMERCIAL_SERVICE_TOS = "Commercial Service API (Terms of Use)"

data class LibraryDeclaration(
    val name: String,
    val url: String,
    val license: String
)

@Composable
fun AboutView() {

    var showOpenSourceDialog by remember {
        mutableStateOf(false)
    }
    var showFeedbackDialog by remember {
        mutableStateOf(false)
    }
    var latestUpdate by remember {
        mutableStateOf<UpdateInfo?>(null)
    }
    var checkingUpdate by remember {
        mutableStateOf(false)
    }
    var updating by remember {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()

    Column {
        val uriHandler = LocalUriHandler.current
        fun openUrl(url: String) {
            try{
                uriHandler.openUri(url)
            }catch (ex: Exception){
                ex.printStackTrace()
                // todo gen qrcode and show
            }
        }

        IconItem(
            Icons.Outlined.Info,
            desc = stringResource(Res.string.showcase_about),
            onClick = {
                openUrl(resUrl)
            }){

            Text(
                text = "${versionName}.${gitHash}(${versionCode})",
                color = LocalContentColor.current.copy(0.6f)
            )
        }

        IconItem(
            Res.drawable.ic_telegram_app,
            desc = stringResource(Res.string.telegram_channel),
            onClick = {
                openUrl(telegramChannelUrl)
            }
        )

        IconItem(
            Icons.Outlined.Feedback,
            desc = stringResource(Res.string.feedback),
            onClick = {
//                openUrl(telegramChannelUrl)
                showFeedbackDialog = !showFeedbackDialog
            }
        )

        IconItem(
            Icons.Outlined.TipsAndUpdates,
            desc = stringResource(Res.string.thanks),
            onClick = {
                showOpenSourceDialog = !showOpenSourceDialog
            }
        )

        IconItem(
            Icons.Outlined.PrivacyTip,
            desc = stringResource(Res.string.privacy_policy),
            onClick = {
                openUrl(privacyPolicyUrl)
            }
        )

//        SwitchItem(
//            Icons.Outlined.Autorenew,
//            false,
//            stringResource(Res.string.auto_update)
//        ) {
//
//        }
        if(isMobile()){
            IconItem(
                Icons.Outlined.ThumbUp,
                desc = stringResource(Res.string.rate),
                onClick = {
                    if(isAndroid()){
                        openUrl(play_store)
                    }
                    else if (isIos()){
                        openUrl(app_store)
                    }
                }
            )
        }



        if (!isWeb()){
            IconItem(
                Icons.Outlined.ArrowCircleUp,
                desc = stringResource(Res.string.check_for_update),
                onClick = {
                    if (checkingUpdate || updating) return@IconItem
                    scope.launch {
                        checkingUpdate = true
                        AppUpdateManager.checkForUpdate()
                            .onSuccess { result ->
                                when (result) {
                                    UpdateCheckResult.UpToDate -> {
                                        ToastUtil.toast(Res.string.up_to_date)
                                    }

                                    is UpdateCheckResult.Available -> {
                                        latestUpdate = result.info
                                    }
                                }
                            }
                            .onFailure {
                                ToastUtil.error(Res.string.app_update_failed)
                            }
                        checkingUpdate = false
                    }
                }
            ) {
                if (checkingUpdate) {
                    Text(
                        text = stringResource(Res.string.loading),
                        color = LocalContentColor.current.copy(0.6f)
                    )
                }
            }
        }

        IconItem(
            Icons.Outlined.IosShare,
            desc = stringResource(Res.string.share),
            onClick = {

            }
        )

//        IconItem(
//            icon = Icons.Outlined.NewReleases,
//            desc = stringResource(id = R.string.membership),
//            onClick = {
//                openBottomBilling = !openBottomBilling
//            })
//
//        MemberBillingList(openBottomBilling){
//            openBottomBilling = false
//        }
    }

    latestUpdate?.let { updateInfo ->
        AlertDialog(
            onDismissRequest = {
                if (!updating) {
                    latestUpdate = null
                }
            },
            title = {
                Text("${stringResource(Res.string.new_update_release)} ${updateInfo.tagName}")
            },
            text = {
                Column {
                    if (updateInfo.releaseTitle.isNotBlank() && updateInfo.releaseTitle != updateInfo.tagName) {
                        Text(
                            text = updateInfo.releaseTitle,
                            fontSize = 14.sp,
                            color = LocalContentColor.current.copy(0.8f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.sizeIn(maxHeight = 300.dp, maxWidth = 500.dp)
                    ) {
                        item {
                            Text(
                                text = updateInfo.releaseNotes.ifBlank { "-" },
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !updating && updateInfo.canInstall,
                    onClick = {
                        if (updating) return@TextButton
                        scope.launch {
                            updating = true
                            AppUpdateManager.installUpdate(updateInfo)
                                .onSuccess {
                                    latestUpdate = null
                                }
                                .onFailure { error ->
                                    ToastUtil.error(error.message ?: "Update failed")
                                }
                            updating = false
                        }
                    }
                ) {
                    Text(
                        text = if (updating) stringResource(Res.string.loading) else stringResource(Res.string.update)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !updating,
                    onClick = {
                        latestUpdate = null
                    }
                ) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    if (showOpenSourceDialog) {
        OpenSourceListDialog {
            showOpenSourceDialog = false
        }
    }


    if (showFeedbackDialog){
        FeedbackDialog(
            onFeedback = { feedback, email ->
                Analytics.getInstance().sendUserFeedback(feedback, email)
                ToastUtil.success("Thank you for your feedback!")
            },
            onDismiss = {
                showFeedbackDialog = false
            }
        )
    }

}


@Preview
@Composable
fun OpenSourceListDialog(onDismiss: () -> Unit = {}) {
    val uriHandler = LocalUriHandler.current
    val performHaptic = rememberMobileHaptic()
    fun openUrl(url: String) {
        uriHandler.openUri(url)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.open_source_license)) },
        text = {
            HorizontalDivider(thickness = 0.5.dp, color = Color.Gray.copy(0.4f))
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(Modifier.sizeIn(maxHeight = 300.dp, maxWidth = 500.dp)) {
                items(openSourceLibraries) { library ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10))
                            .clickable {
                                performHaptic()
                                openUrl(library.url)
                            }
                            .padding(8.dp)
                    ) {
                        Text(
                            text = library.name,
                            modifier = Modifier.padding(vertical = 3.dp),
                            fontSize = 15.sp
                        )
                        Text(text = library.url, fontSize = 12.sp)
                        Text(
                            text = library.license,
                            modifier = Modifier.padding(vertical = 2.dp),
                            fontSize = 10.sp
                        )

                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    performHaptic()
                    onDismiss()
                }
            ) {
                Text(text = "OK")
            }
        }
    )
}


val openSourceLibraries = listOf(
    LibraryDeclaration("AndroidX (Activity/AppCompat/Core/Lifecycle/Navigation/Room/SQLite)", "https://developer.android.com/jetpack/androidx", APACHE_V2),
    LibraryDeclaration("Apache Commons Net", "https://commons.apache.org/proper/commons-net/", APACHE_V2),
    LibraryDeclaration("appdirs", "https://github.com/harawata/appdirs", APACHE_V2),
    LibraryDeclaration("Coil", "https://github.com/coil-kt/coil", APACHE_V2),
    LibraryDeclaration("Compottie", "https://github.com/alexzhirkevich/compottie", MIT),
    LibraryDeclaration("compose-shimmer", "https://github.com/valentinilk/compose-shimmer", APACHE_V2),
    LibraryDeclaration("ConfettiKit", "https://github.com/vinceglb/ConfettiKit", MIT),
    LibraryDeclaration("cryptography-kotlin", "https://github.com/whyoleg/cryptography-kotlin", APACHE_V2),
    LibraryDeclaration("AndroidX", "https://developer.android.com/jetpack/androidx", APACHE_V2),
    LibraryDeclaration("AndroidX Work Manager", "https://developer.android.com/topic/libraries/architecture/workmanager", APACHE_V2),
    LibraryDeclaration("Compose", "https://developer.android.com/jetpack/compose", APACHE_V2),
    LibraryDeclaration("Google Android Material", "https://github.com/material-components/material-components-android", APACHE_V2),
    LibraryDeclaration("Hilt", "https://github.com/google/dagger/tree/main/java/dagger/hilt/android", APACHE_V2),
    LibraryDeclaration("Icons8", "https://icons8.com/", "Universal Multimedia Licensing Agreement for Icons8"),
    LibraryDeclaration("Kotlinx Coroutines", "https://github.com/Kotlin/kotlinx.coroutines", APACHE_V2),
    LibraryDeclaration("Kotlinx Serialization JSON", "https://github.com/Kotlin/kotlinx.serialization", APACHE_V2),
    LibraryDeclaration("Landscape", "https://github.com/skydoves/landscape", APACHE_V2),
    LibraryDeclaration("LeakCanary", "https://square.github.io/leakcanary", APACHE_V2),
    LibraryDeclaration("Lottie", "https://github.com/airbnb/lottie-android", APACHE_V2),
    LibraryDeclaration("Kage", "https://github.com/zhaobozhen/Kage", APACHE_V2),
    LibraryDeclaration("Microsoft App Center", "https://github.com/microsoft/appcenter-sdk-android", MIT),
    LibraryDeclaration("OkHttp", "https://square.github.io/okhttp", APACHE_V2),
    LibraryDeclaration("Okio", "https://square.github.io/okio", APACHE_V2),
    LibraryDeclaration("Once", "https://github.com/jonfinerty/Once", APACHE_V2),
    LibraryDeclaration("ProtoBuf", "https://github.com/protocolbuffers/protobuf", APACHE_V2),
    LibraryDeclaration("Rclone", "https://github.com/rclone/rclone", MIT),
    LibraryDeclaration("Retrofit", "https://square.github.io/retrofit/", APACHE_V2),
    LibraryDeclaration("Timber", "https://github.com/JakeWharton/timber", APACHE_V2),
    LibraryDeclaration("Toasty", "https://github.com/GrenderG/Toasty", APACHE_V2),
    LibraryDeclaration("Smiley Sans", "https://github.com/atelier-anchor/smiley-sans", OFL),
    LibraryDeclaration("Konfetti", "https://github.com/DanielMartinus/Konfetti", ISC),
    LibraryDeclaration("PlatinumMedia", "https://github.com/huzongyao/PlatinumMedia", ""),
    LibraryDeclaration("FlipBoardAnimation", "https://github.com/sinasamaki/FlipBoardAnimation", ""),
    LibraryDeclaration("Pager-Animations", "https://www.sinasamaki.com/pager-animations/", ""),
    LibraryDeclaration("FileKit", "https://github.com/vinceglb/FileKit", MIT),
    LibraryDeclaration("FlatLaf", "https://www.formdev.com/flatlaf/", APACHE_V2),
    LibraryDeclaration("JetBrains Compose Multiplatform", "https://www.jetbrains.com/lp/compose-multiplatform/", APACHE_V2),
    LibraryDeclaration("JSch", "http://www.jcraft.com/jsch/", BSD),
    LibraryDeclaration("Koin", "https://github.com/InsertKoinIO/koin", APACHE_V2),
    LibraryDeclaration("KStore", "https://github.com/xxfast/KStore", APACHE_V2),
    LibraryDeclaration("KSoup", "https://github.com/fleeksoft/ksoup", MIT),
    LibraryDeclaration("Ktor", "https://github.com/ktorio/ktor", APACHE_V2),
    LibraryDeclaration("Kotlinx AtomicFU", "https://github.com/Kotlin/kotlinx-atomicfu", APACHE_V2),
    LibraryDeclaration("Kotlinx Datetime", "https://github.com/Kotlin/kotlinx-datetime", APACHE_V2),
    LibraryDeclaration("Kotlinx Serialization", "https://github.com/Kotlin/kotlinx.serialization", APACHE_V2),
    LibraryDeclaration("Lottie Android", "https://github.com/airbnb/lottie-android", APACHE_V2),
    LibraryDeclaration("Napier", "https://github.com/AAkira/Napier", APACHE_V2),
    LibraryDeclaration("Rapid7 DCERPC", "https://github.com/rapid7/dcerpc", BSD),
    LibraryDeclaration("Sentry Kotlin Multiplatform", "https://github.com/getsentry/sentry-kotlin-multiplatform", MIT),
    LibraryDeclaration("SMBJ", "https://github.com/hierynomus/smbj", APACHE_V2),
    LibraryDeclaration("Supabase Kotlin", "https://github.com/supabase-community/supabase-kt", MIT),
    LibraryDeclaration("xmlutil", "https://github.com/pdvrieze/xmlutil", APACHE_V2),
    LibraryDeclaration("Gitee API", "https://gitee.com/api/v5/swagger", SERVICE_TOS),
    LibraryDeclaration("GitHub REST API", "https://docs.github.com/en/rest", SERVICE_TOS),
    LibraryDeclaration("Immich API", "https://immich.app/docs/api/", SERVICE_TOS),
    LibraryDeclaration("IPGeolocation API", "https://ipgeolocation.io/", COMMERCIAL_SERVICE_TOS),
    LibraryDeclaration("Open-Meteo API", "https://open-meteo.com/", SERVICE_TOS),
    LibraryDeclaration("Pexels API", "https://www.pexels.com/api/", SERVICE_TOS),
    LibraryDeclaration("Supabase Service", "https://supabase.com/", COMMERCIAL_SERVICE_TOS),
    LibraryDeclaration("TMDB API", "https://developer.themoviedb.org/docs", SERVICE_TOS),
    LibraryDeclaration("Unsplash API", "https://unsplash.com/developers", SERVICE_TOS),
    LibraryDeclaration("AndroidX DataStore", "https://developer.android.com/jetpack/androidx/releases/datastore", APACHE_V2),
    LibraryDeclaration("AndroidX Media3", "https://github.com/androidx/media", APACHE_V2),
    LibraryDeclaration("AndroidX Paging", "https://developer.android.com/topic/libraries/architecture/paging", APACHE_V2),
    LibraryDeclaration("AndroidX Room", "https://developer.android.com/jetpack/androidx/releases/room", APACHE_V2),
    LibraryDeclaration("AWS Android SDK", "https://github.com/aws-amplify/aws-sdk-android", APACHE_V2),
    LibraryDeclaration("avif-coder", "https://github.com/awxkee/avif-coder", APACHE_V2),
    LibraryDeclaration("Cascade", "https://github.com/saket/cascade", APACHE_V2),
    LibraryDeclaration("compose-shimmer", "https://github.com/valentinilk/compose-shimmer", MIT),
    LibraryDeclaration("DCERPC (smbj-rpc)", "https://github.com/rapid7/smbj-rpc", APACHE_V2),
    LibraryDeclaration("DiskLruCache", "https://github.com/JakeWharton/DiskLruCache", APACHE_V2),
    LibraryDeclaration("Firebase", "https://github.com/firebase/firebase-android-sdk", APACHE_V2),
    LibraryDeclaration("Flexbox", "https://github.com/google/flexbox-layout", APACHE_V2),
    LibraryDeclaration("Glide", "https://github.com/bumptech/glide", BSD),
    LibraryDeclaration("Google Play Billing", "https://developer.android.com/google/play/billing", APACHE_V2),
    LibraryDeclaration("ipgeolocation", "https://ipgeolocation.io/", "ipgeolocation.io API"),
    LibraryDeclaration("JSch", "https://github.com/mwiede/jsch", BSD),
    LibraryDeclaration("Jsoup", "https://jsoup.org/", MIT),
    LibraryDeclaration("kotlinx.collections.immutable", "https://github.com/Kotlin/kotlinx.collections.immutable", APACHE_V2),
    LibraryDeclaration("Metadata Extractor", "https://github.com/drewnoakes/metadata-extractor", APACHE_V2),
    LibraryDeclaration("meting-api", "https://github.com/injahow/meting-api", MIT),
    LibraryDeclaration("NanoHttpd", "https://github.com/NanoHttpd/nanohttpd", BSD),
    LibraryDeclaration("Nextlib", "https://github.com/anilbeesetti/nextlib", APACHE_V2),
    LibraryDeclaration("OpenWeatherMap", "https://openweathermap.org/", "OpenWeatherMap API"),
    LibraryDeclaration("Reorderable", "https://github.com/Calvin-LL/Reorderable", APACHE_V2),
    LibraryDeclaration("RSS Parser", "https://github.com/prof18/RSS-Parser", APACHE_V2),
    LibraryDeclaration("Sardine Android", "https://github.com/thegrizzlylabs/sardine-android", APACHE_V2),
    LibraryDeclaration("Sentry", "https://github.com/getsentry/sentry-java", MIT),
    LibraryDeclaration("SLF4J", "https://www.slf4j.org/", MIT),
    LibraryDeclaration("Supabase", "https://github.com/supabase-community/supabase-kt", MIT),
    LibraryDeclaration("ZXing", "https://github.com/zxing/zxing", APACHE_V2),
    LibraryDeclaration("Zoomable", "https://github.com/usuiat/Zoomable", APACHE_V2)
)
