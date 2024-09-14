package com.alpha.showcase.common.ui.settings

import androidx.compose.runtime.Composable
import com.alpha.showcase.common.ui.play.DEFAULT_PERIOD
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.display_mode_center
import showcaseapp.composeapp.generated.resources.display_mode_full
import showcaseapp.composeapp.generated.resources.display_mode_full_screen
import showcaseapp.composeapp.generated.resources.display_orientation_horizontal
import showcaseapp.composeapp.generated.resources.display_orientation_vertical
import showcaseapp.composeapp.generated.resources.frame_wall_fix_size
import showcaseapp.composeapp.generated.resources.frame_wall_random_size
import showcaseapp.composeapp.generated.resources.sort_rule_date_asc
import showcaseapp.composeapp.generated.resources.sort_rule_date_desc
import showcaseapp.composeapp.generated.resources.sort_rule_name_asc
import showcaseapp.composeapp.generated.resources.sort_rule_name_desc
import showcaseapp.composeapp.generated.resources.sort_rule_random
import showcaseapp.composeapp.generated.resources.time_unit_minute
import showcaseapp.composeapp.generated.resources.time_unit_second


sealed class Select<T>(val value: T, val title: String, val resString: StringResource){
    fun toPair() = value to title

    @Composable
    fun toPairWithResString() = value to stringResource(resString)
}

sealed class DisplayMode(type: Int, title: String, resString: StringResource): Select<Int>(type, title, resString){
    object FitScreen : DisplayMode(0, "Full screen", Res.string.display_mode_full_screen)
    object Full : DisplayMode(0, "Full", Res.string.display_mode_full)

    object CenterCrop: DisplayMode(1, "Center", Res.string.display_mode_center)
    companion object {
        const val key: String = "DisplayMode"
        fun fromValue(type: Int): DisplayMode{
            return when(type){
                0 -> FitScreen
                1 -> CenterCrop
                else -> FitScreen
            }
        }
    }
}

sealed class Orientation(type: Int, title: String, resString: StringResource): Select<Int>(type, title, resString){
    data object Horizontal : Orientation(0, "Horizontal", Res.string.display_orientation_horizontal)
    data object Vertical : Orientation(1, "Vertical", Res.string.display_orientation_vertical)
    companion object {
        const val key: String = "Orientation"
        fun fromValue(type: Int): Orientation{
            return when(type){
                0 -> Horizontal
                1 -> Vertical
                else -> Horizontal
            }
        }
    }
}

sealed class AutoPlay {
    companion object {
        const val key: String = "AutoPlay"
    }
}

sealed class AutoPlayDuration {
    companion object {
        const val key: String = "AutoPlayDuration"
    }
}

sealed class IntervalTimeUnit(type: Int, title: String, resString: StringResource): Select<Int>(type, title, resString){
    object S: IntervalTimeUnit(0, "Second", Res.string.time_unit_second)
    object M: IntervalTimeUnit(1, "Minute", Res.string.time_unit_minute)
    companion object {
        const val key: String = "IntervalTimeUnit"
        fun fromValue(type: Int): IntervalTimeUnit{
            return when(type){
                0 -> S
                1 -> M
                else -> S
            }
        }
    }
}

fun getInterval(timeUnit: Int, interval: Int): Long{
    val l = when (timeUnit) {
        0 -> interval * 1000L
        1 -> interval * 1000 * 60L
        else -> interval * 1000L
    }
    return if (l <= 0) DEFAULT_PERIOD else l
}


sealed class ShowTimeProgressIndicator {
    companion object {
        const val key: String = "ShowTimeProgressIndicator"
    }
}

sealed class ShowTimeAndDate {
    companion object {
        const val key: String = "ShowTimeAndDate"
    }
}

sealed class ShowContentMetaInfo {
    companion object {
        const val key: String = "ShowContentMetaInfo"
    }
}

sealed class SortRule(type: Int, title: String, resString: StringResource) :
    Select<Int>(type, title, resString) {
    object Random : SortRule(0, "Random", Res.string.sort_rule_random)
    object NameAsc : SortRule(1, "Name Asc", Res.string.sort_rule_name_asc)
    object NameDesc: SortRule(2, "Name Desc", Res.string.sort_rule_name_desc)
    object DateAsc: SortRule(3, "Date Asc", Res.string.sort_rule_date_asc)
    object DateDesc: SortRule(4, "Date Desc", Res.string.sort_rule_date_desc)

    companion object {
        const val key: String = "SortRule"
        fun fromValue(type: Int): SortRule{
            return when(type){
                0 -> Random
                1 -> NameAsc
                2 -> NameDesc
                3 -> DateAsc
                4 -> DateDesc
                else -> Random
            }
        }
    }
}


sealed class Interval {
    companion object {
        const val key: String = "Interval"
    }
}

sealed class BentoStyle {
    companion object {
        const val key: String = "BentoStyle"
    }
}

sealed class MatrixSize {

    companion object {
        const val Row: String = "MatrixSizeRow"
        const val Column: String = "MatrixSizeColumn"
    }
}

sealed class FrameWallMode(type: Int, title: String, resString: StringResource): Select<Int>(type, title, resString){
    object FixSize: FrameWallMode(0, "Fix size", Res.string.frame_wall_fix_size)
    object Random: FrameWallMode(1, "Random", Res.string.frame_wall_random_size)

    companion object {
        const val key: String = "FrameWallMode"
        fun fromValue(type: Int): FrameWallMode{
            return when(type){
                0 -> FixSize
                1 -> Random
                else -> FixSize
            }
        }
    }
}