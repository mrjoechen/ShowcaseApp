package com.alpha.showcase.common.ui.settings

import androidx.compose.runtime.Composable


sealed class Select<T>(val value: T, val title: String, val resString: Int){
    fun toPair() = value to title
    
    @Composable
    fun toPairWithResString() = value to title
}

sealed class DisplayMode(type: Int, title: String, resString: Int): Select<Int>(type, title, resString){
    data object FitScreen: DisplayMode(0, "Full screen", 1)
    data object CenterCrop: DisplayMode(1, "Center", 2)
    companion object {
        const val key: String = "DisplayMode"
        fun fromValue(type: Int): DisplayMode {
            return when(type){
                0 -> FitScreen
                1 -> CenterCrop
                else -> FitScreen
            }
        }
    }
}

sealed class Orientation(type: Int, title: String, resString: Int): Select<Int>(type, title, resString){
    object Horizontal: Orientation(0, "Horizontal", 1)
    object Vertical: Orientation(1, "Vertical", 2)
    companion object {
        const val key: String = "Orientation"
        fun fromValue(type: Int): Orientation {
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

sealed class IntervalTimeUnit(type: Int, title: String, resString: Int): Select<Int>(type, title, resString){
    object S: IntervalTimeUnit(0, "Second", 1)
    object M: IntervalTimeUnit(1, "Minute", 2)
    companion object {
        const val key: String = "IntervalTimeUnit"
        fun fromValue(type: Int): IntervalTimeUnit {
            return when(type){
                0 -> S
                1 -> M
                else -> S
            }
        }
    }
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

sealed class SortRule(type: Int, title: String, resString: Int) :
    Select<Int>(type, title, resString) {
    object Random : SortRule(0, "Random", 1)
    object NameAsc : SortRule(1, "Name Asc", 2)
    object NameDesc: SortRule(2, "Name Desc", 3)
    object DateAsc: SortRule(3, "Date Asc", 4)
    object DateDesc: SortRule(4, "Date Desc", 5)

    companion object {
        const val key: String = "SortRule"
        fun fromValue(type: Int): SortRule {
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

sealed class MatrixSize {

    companion object {
        const val Row: String = "MatrixSizeRow"
        const val Column: String = "MatrixSizeColumn"
    }
}

sealed class FrameWallMode(type: Int, title: String, resString: Int): Select<Int>(type, title, resString){
    object FixSize: FrameWallMode(0, "Fix size", 1)
    object Random: FrameWallMode(1, "Random", 2)

    companion object {
        const val key: String = "FrameWallMode"
        fun fromValue(type: Int): FrameWallMode {
            return when(type){
                0 -> FixSize
                1 -> Random
                else -> FixSize
            }
        }
    }
}