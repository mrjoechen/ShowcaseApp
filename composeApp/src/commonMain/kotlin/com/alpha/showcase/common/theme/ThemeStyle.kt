package com.alpha.showcase.common.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.theme_style_atlas
import showcaseapp.composeapp.generated.resources.theme_style_aurora
import showcaseapp.composeapp.generated.resources.theme_style_coast
import showcaseapp.composeapp.generated.resources.theme_style_forest
import showcaseapp.composeapp.generated.resources.theme_style_herbarium
import showcaseapp.composeapp.generated.resources.theme_style_journal
import showcaseapp.composeapp.generated.resources.theme_style_noir
import showcaseapp.composeapp.generated.resources.theme_style_oled
import showcaseapp.composeapp.generated.resources.theme_style_orchid
import showcaseapp.composeapp.generated.resources.theme_style_signal
import showcaseapp.composeapp.generated.resources.theme_style_sunset

enum class AppThemeStyle(
    val value: Int,
    val labelRes: StringResource,
) {
    Coast(0, Res.string.theme_style_coast),
    Forest(1, Res.string.theme_style_forest),
    Sunset(2, Res.string.theme_style_sunset),
    Orchid(3, Res.string.theme_style_orchid),
    Noir(4, Res.string.theme_style_noir),
    Oled(5, Res.string.theme_style_oled),
    Aurora(6, Res.string.theme_style_aurora),
    Atlas(7, Res.string.theme_style_atlas),
    Herbarium(8, Res.string.theme_style_herbarium),
    Journal(9, Res.string.theme_style_journal),
    Signal(10, Res.string.theme_style_signal),
    ;

    @Composable
    fun label(): String = stringResource(labelRes)

    fun previewColors(): List<Color> = when (this) {
        Coast -> listOf(Blue40, DarkBlue40, Yellow80)
        Forest -> listOf(Green40, Teal40, Orange80)
        Sunset -> listOf(Sunset40, Gold40, Berry80)
        Orchid -> listOf(OrchidPurple40, OrchidPink40, OrchidCyan80)
        Noir -> listOf(Noir40, Copper40, Stone80)
        Oled -> listOf(Color.Black, OledCyan80, OledMint80)
        Aurora -> listOf(Aurora40, AuroraIndigo40, AuroraMint80)
        Atlas -> listOf(MonoNeutralWhite, InkCobalt, InkTerracotta)
        Herbarium -> listOf(MonoPaleBeige, InkBotanicalGreen, InkOxblood)
        Journal -> listOf(MonoCoolGray, InkMintGreen, InkWarmCharcoal)
        Signal -> listOf(MonoCoolGray, InkCharcoal, InkSignalRed)
    }

    companion object {
        val default = Coast

        fun fromValue(value: Int): AppThemeStyle {
            return entries.firstOrNull { it.value == value } ?: default
        }
    }
}
