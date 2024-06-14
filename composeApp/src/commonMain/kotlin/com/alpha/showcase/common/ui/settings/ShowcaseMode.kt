package com.alpha.showcase.common.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.data.GeneralPreference
import com.alpha.showcase.common.data.GeneralPreferenceKey
import com.alpha.showcase.ui.settings.SlideModeView
import com.alpha.showcase.common.data.Settings
import showcaseapp.composeapp.generated.resources.Res
import com.alpha.showcase.common.ui.view.CheckItem
import com.alpha.showcase.common.ui.view.SwitchItem
import com.alpha.showcase.common.ui.view.TextTitleMedium

const val SHOWCASE_MODE_SLIDE = 0
const val SHOWCASE_MODE_FRAME_WALL = 1
const val SHOWCASE_MODE_FADE = 2
const val SHOWCASE_SCROLL_FADE = 3
const val SHOWCASE_SQUARE = 4

@Composable
fun ShowcaseSettings(
  settings: Settings = Settings.getDefaultInstance(),
  generalPreference: GeneralPreference = GeneralPreference(1, 0),
  onSettingChanged: (Settings) -> Unit
) {

  val styleList = listOf(
    ShowcaseMode.Slide.toPairWithResString(),
    ShowcaseMode.FrameWall.toPairWithResString(),
    ShowcaseMode.Fade.toPairWithResString()
  )

  Column {
    Spacer(modifier = Modifier.height(10.dp))
    TextTitleMedium(text = "Style")

    CheckItem(
      when(settings.showcaseMode) {
        SHOWCASE_MODE_SLIDE -> {
          Icons.Outlined.Style
        }

        SHOWCASE_MODE_FRAME_WALL -> {
          Icons.Outlined.Style
        }

        else -> {
          Icons.Outlined.Style
        }
      },
      ShowcaseMode.fromValue(settings.showcaseMode).toPairWithResString(),
      "Style",
      styleList,
      onCheck = {
        onSettingChanged(settings.copy(showcaseMode = it.first))
      }
    )


    when(settings.showcaseMode) {
      SHOWCASE_MODE_SLIDE ->
        SlideModeView(settings.slideMode) { key, value ->
          var slideMode = settings.slideMode
          when(key) {
            DisplayMode.key -> {
              slideMode = slideMode.copy(
                displayMode = value as Int
              )
            }

            Orientation.key -> {
              slideMode = slideMode.copy(
                orientation = value as Int
              )
            }

            AutoPlayDuration.key -> {
              slideMode = slideMode.copy(
                intervalTime = value as Int
              )
            }

            IntervalTimeUnit.key -> {
              slideMode = slideMode.copy(
                intervalTimeUnit = value as Int
              )
            }

            ShowTimeProgressIndicator.key -> {
              slideMode = slideMode.copy(
                showTimeProgressIndicator = value as Boolean
              )
            }

            ShowContentMetaInfo.key -> {
              slideMode = slideMode.copy(
                showContentMetaInfo = value as Boolean
              )
            }

            SortRule.key -> {
              slideMode = slideMode.copy(
                sortRule = value as Int
              )
            }
          }
          val copy = settings.copy(
            slideMode = slideMode
          )

          onSettingChanged(copy)
        }

      SHOWCASE_MODE_FRAME_WALL ->
        FrameWallModeView(settings.frameWallMode) {key, value ->
          var frameWallMode = settings.frameWallMode
          when(key) {
            FrameWallMode.key -> {
              frameWallMode = frameWallMode.copy(
                frameStyle = value as Int
              )
            }

            MatrixSize.Row -> {
              frameWallMode = frameWallMode.copy(
                matrixSizeRows = value as Int
              )
            }

            MatrixSize.Column -> {
              frameWallMode = frameWallMode.copy(
                matrixSizeColumns = value as Int
              )
            }

            Interval.key -> {
              frameWallMode = frameWallMode.copy(
                interval = value as Int
              )
            }
          }

          val copy = settings.copy(
            frameWallMode = frameWallMode
          )
          onSettingChanged(copy)

        }

      SHOWCASE_MODE_FADE ->
        FadeModeView(settings.fadeMode) {key, value ->

          var fadeMode = settings.fadeMode
          when(key) {

            DisplayMode.key -> {
              fadeMode = fadeMode.copy(
                displayMode = value as Int
              )
            }

            AutoPlayDuration.key -> {
              fadeMode = fadeMode.copy(
                intervalTime = value as Int
              )
            }

            IntervalTimeUnit.key -> {
              fadeMode = fadeMode.copy(
                intervalTimeUnit = value as Int
              )
            }

            ShowTimeProgressIndicator.key -> {
              fadeMode = fadeMode.copy(
                showTimeProgressIndicator = value as Boolean
              )
            }

            ShowContentMetaInfo.key -> {
              fadeMode = fadeMode.copy(
                showContentMetaInfo = value as Boolean
              )
            }

            SortRule.key -> {
              fadeMode = fadeMode.copy(
                sortRule = value as Int
              )
            }
          }

          val copy = settings.copy(
            fadeMode = fadeMode
          )
          onSettingChanged(copy)
        }

      SHOWCASE_SCROLL_FADE -> {
        //                ScrollModeView()
      }

      else -> {

      }
    }

    SwitchItem(
      Icons.Outlined.AccessTime,
      check = settings.showTimeAndDate,
      desc = "Show time and date",
      onCheck = {
        onSettingChanged(settings.copy(showTimeAndDate = it))
      }
    )

    GeneralView(generalPreference) {key, value ->
      when(key) {

        GeneralPreferenceKey.Language -> {

        }

        GeneralPreferenceKey.AnonymousUsage -> {
        }

        GeneralPreferenceKey.DarkMode -> {

        }

        GeneralPreferenceKey.CacheSize -> {
        }

        else -> {

        }
      }
    }

    SourcePreferenceView(settings) { key, value ->
      val settingsBuilder: Settings

      when (key) {

        SourcePreferenceItem.RecursiveDir -> {
          settingsBuilder = settings.copy(recursiveDirContent = value as Boolean)
        }

        SourcePreferenceItem.AutoRefresh -> {
          settingsBuilder = settings.copy(autoRefresh = value as Boolean)
        }

        SourcePreferenceItem.AutoOpenLatestSource -> {
          settingsBuilder = settings.copy(autoOpenLatestSource = value as Boolean)
        }

        SourcePreferenceItem.SupportVideo -> {
          settingsBuilder = settings.copy(supportVideo = value as Boolean)
        }

        else -> {
          settingsBuilder = settings
        }
      }

      onSettingChanged(settingsBuilder)
    }

    AboutView()

  }
}


sealed class ShowcaseMode(type: Int, title: String, resString: Int):
  Select<Int>(type, title, resString) {
  data object Slide: ShowcaseMode(SHOWCASE_MODE_SLIDE, "Slide", 1)
  data object FrameWall: ShowcaseMode(SHOWCASE_MODE_FRAME_WALL, "Frame wall", 2)
  data object Fade: ShowcaseMode(SHOWCASE_MODE_FADE, "Fade", 3)

  companion object {
    const val KEY: String = "ShowcaseMode"
    fun fromValue(type: Int): ShowcaseMode {
      return when(type) {
        SHOWCASE_MODE_SLIDE -> Slide
        SHOWCASE_MODE_FRAME_WALL -> FrameWall
        SHOWCASE_MODE_FADE -> Fade
        else -> Slide
      }
    }
  }
}


fun getModeName(mode: Int): String {
  return when(mode) {
    SHOWCASE_MODE_SLIDE -> ShowcaseMode.Slide.title
    SHOWCASE_MODE_FRAME_WALL -> ShowcaseMode.FrameWall.title
    SHOWCASE_MODE_FADE -> ShowcaseMode.Fade.title
    //        SHOWCASE_SQUARE -> "Square"

    else -> {
      "Unknown"
    }
  }
}

