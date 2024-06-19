package com.alpha.showcase.common.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import showcaseapp.composeapp.generated.resources.Res
import com.alpha.showcase.common.ui.view.CheckItem
import com.alpha.showcase.common.ui.view.SwitchItem
import com.alpha.showcase.common.ui.view.TextTitleMedium
import com.alpha.showcase.common.utils.SYSTEM_DEFAULT
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.display_style_calender
import showcaseapp.composeapp.generated.resources.display_style_carousel
import showcaseapp.composeapp.generated.resources.display_style_cube
import showcaseapp.composeapp.generated.resources.display_style_fade
import showcaseapp.composeapp.generated.resources.display_style_frame_wall
import showcaseapp.composeapp.generated.resources.display_style_reveal
import showcaseapp.composeapp.generated.resources.display_style_slide
import showcaseapp.composeapp.generated.resources.showcase_style
import showcaseapp.composeapp.generated.resources.sort_rule

const val SHOWCASE_MODE_SLIDE = 0
const val SHOWCASE_MODE_FRAME_WALL = 1
const val SHOWCASE_MODE_FADE = 2
const val SHOWCASE_SCROLL_FADE = 3
const val SHOWCASE_SQUARE = 4
const val SHOWCASE_MODE_CALENDER = 5

const val SHOWCASE_MODE_CUBE = 6
const val SHOWCASE_MODE_REVEAL = 7
const val SHOWCASE_MODE_CAROUSEL = 8

@Composable
fun ShowcaseSettings(
    settings: Settings = Settings.getDefaultInstance(),
    generalPreference: GeneralPreference = GeneralPreference(SYSTEM_DEFAULT, 0),
    onSettingChanged: (Settings) -> Unit,
    onGeneralSettingChanged: (GeneralPreference) -> Unit,
) {

  val styleList = listOf(
    ShowcaseMode.Slide.toPairWithResString(),
    ShowcaseMode.FrameWall.toPairWithResString(),
    ShowcaseMode.Fade.toPairWithResString(),
    ShowcaseMode.Calender.toPairWithResString(),
//        ShowcaseMode.Cube.toPairWithResString(),
//        ShowcaseMode.Reveal.toPairWithResString(),
//        ShowcaseMode.Carousel.toPairWithResString()
  )

  Column {
    Spacer(modifier = Modifier.height(10.dp))
    TextTitleMedium(text = stringResource(Res.string.showcase_style))

    CheckItem(
      Icons.Outlined.Style,
      ShowcaseMode.fromValue(settings.showcaseMode).toPairWithResString(),
      stringResource(Res.string.showcase_style),
      styleList,
      onCheck = {
        onSettingChanged(settings.copy(showcaseMode = it.first))
      }
    )


    when(settings.showcaseMode) {
      SHOWCASE_MODE_SLIDE ->
        SlideModeView(settings.slideMode) { key, value ->
          var slideMode =  when(key) {
            DisplayMode.key -> {
              settings.slideMode.copy(
                displayMode = value as Int
              )
            }

            Orientation.key -> {
              settings.slideMode.copy(
                orientation = value as Int
              )
            }

            AutoPlayDuration.key -> {
              settings.slideMode.copy(
                intervalTime = value as Int
              )
            }

            IntervalTimeUnit.key -> {
              settings.slideMode.copy(
                intervalTimeUnit = value as Int
              )
            }

            ShowTimeProgressIndicator.key -> {
              settings.slideMode.copy(
                showTimeProgressIndicator = value as Boolean
              )
            }

            ShowContentMetaInfo.key -> {
              settings.slideMode.copy(
                showContentMetaInfo = value as Boolean
              )
            }

            SortRule.key -> {
              settings.slideMode.copy(
                sortRule = value as Int
              )
            }

            else -> {
              settings.slideMode
            }
          }
          onSettingChanged(settings.copy(
            slideMode = slideMode
          ))
        }

      SHOWCASE_MODE_FRAME_WALL ->
        FrameWallModeView(settings.frameWallMode) {key, value ->
          val frameWallMode = when(key) {
            FrameWallMode.key -> {
              settings.frameWallMode.copy(
                frameStyle = value as Int
              )
            }

            MatrixSize.Row -> {
              settings.frameWallMode.copy(
                matrixSizeRow = value as Int
              )
            }

            MatrixSize.Column -> {
              settings.frameWallMode.copy(
                matrixSizeColumn = value as Int
              )
            }

            Interval.key -> {
              settings.frameWallMode.copy(
                interval = value as Int
              )
            }

            DisplayMode.key -> {
              settings.frameWallMode.copy(
                displayMode = value as Int
              )
            }

            else -> {
              settings.frameWallMode
            }
          }
          
          onSettingChanged(settings.copy(
            frameWallMode = frameWallMode
          ))

        }

      SHOWCASE_MODE_FADE ->
        FadeModeView(settings.fadeMode) {key, value ->

          val fadeMode = when(key) {
            DisplayMode.key -> {
              settings.fadeMode.copy(
                displayMode = value as Int
              )
            }

            AutoPlayDuration.key -> {
              settings.fadeMode.copy(
                intervalTime = value as Int
              )
            }

            IntervalTimeUnit.key -> {
              settings.fadeMode.copy(
                intervalTimeUnit = value as Int
              )
            }

            ShowTimeProgressIndicator.key -> {
              settings.fadeMode.copy(
                showTimeProgressIndicator = value as Boolean
              )
            }

            ShowContentMetaInfo.key -> {
              settings.fadeMode.copy(
                showContentMetaInfo = value as Boolean
              )
            }

            SortRule.key -> {
              settings.fadeMode.copy(
                sortRule = value as Int
              )
            }

            else -> {
              settings.fadeMode
            }
          }
          
          onSettingChanged(settings.copy(
            fadeMode = fadeMode
          ))
        }


      SHOWCASE_MODE_CALENDER -> CalenderView(settings.calenderMode) { key, value ->

        val calenderModeBuilder = when (key) {

          AutoPlay.key -> {
            settings.calenderMode.copy(autoPlay = value as Boolean)
          }

          AutoPlayDuration.key -> {
            settings.calenderMode.copy(intervalTime = value as Int)
          }

          IntervalTimeUnit.key -> {
            settings.calenderMode.copy(intervalTimeUnit = value as Int)
          }

          ShowContentMetaInfo.key -> {
            settings.calenderMode.copy(showContentMetaInfo = value as Boolean)
          }

          else -> {
            settings.calenderMode
          }
        }
        onSettingChanged(settings.copy(calenderMode = calenderModeBuilder))

      }
      

      else -> {

      }
    }


    CheckItem(
      Icons.AutoMirrored.Outlined.Sort,
      SortRule.fromValue(settings.sortRule).toPairWithResString(),
      stringResource(Res.string.sort_rule),
      listOf(
        SortRule.Random.toPairWithResString(),
        SortRule.NameAsc.toPairWithResString(),
        SortRule.NameDesc.toPairWithResString(),
        SortRule.DateAsc.toPairWithResString(),
        SortRule.DateDesc.toPairWithResString()
      ),
      onCheck = {
        onSettingChanged(settings.copy(sortRule = it.first))
      }
    )

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
          onGeneralSettingChanged(generalPreference.copy(language = value as Int))
        }

        GeneralPreferenceKey.AnonymousUsage -> {
            onGeneralSettingChanged(generalPreference.copy(anonymousUsage = value as Boolean))
        }

        GeneralPreferenceKey.DarkMode -> {
            onGeneralSettingChanged(generalPreference.copy(darkMode = value as Int))
        }

        GeneralPreferenceKey.CacheSize -> {
            onGeneralSettingChanged(generalPreference.copy(cacheSize = value as Int))
        }
      }
    }

    SourcePreferenceView(settings) { key, value ->
      val settingsBuilder = when (key) {
        SourcePreferenceItem.RecursiveDir -> {
          settings.copy(recursiveDirContent = value as Boolean)
        }
        SourcePreferenceItem.AutoRefresh -> {
          settings.copy(autoRefresh = value as Boolean)
        }
        SourcePreferenceItem.AutoOpenLatestSource -> {
          settings.copy(autoOpenLatestSource = value as Boolean)
        }
        SourcePreferenceItem.SupportVideo -> {
          settings.copy(supportVideo = value as Boolean)
        }
        else -> {
          settings
        }
      }
      onSettingChanged(settingsBuilder)
    }

    AboutView()

  }
}


sealed class ShowcaseMode(type: Int, title: String, resString: StringResource) :
  Select<Int>(type, title, resString) {
  data object Slide : ShowcaseMode(SHOWCASE_MODE_SLIDE, "Slide", Res.string.display_style_slide)
  data object FrameWall :
    ShowcaseMode(SHOWCASE_MODE_FRAME_WALL, "Frame wall", Res.string.display_style_frame_wall)

  data object Fade : ShowcaseMode(SHOWCASE_MODE_FADE, "Fade", Res.string.display_style_fade)
  data object Calender :
    ShowcaseMode(SHOWCASE_MODE_CALENDER, "Calender", Res.string.display_style_calender)

  data object Cube :
    ShowcaseMode(SHOWCASE_MODE_CUBE, "Cube", Res.string.display_style_cube)

  data object Reveal :
    ShowcaseMode(SHOWCASE_MODE_REVEAL, "Reveal", Res.string.display_style_reveal)

  data object Carousel :
    ShowcaseMode(SHOWCASE_MODE_CAROUSEL, "Carousel", Res.string.display_style_carousel)
  companion object {
    const val key: String = "ShowcaseMode"
    fun fromValue(type: Int): ShowcaseMode {
      return when (type) {
        SHOWCASE_MODE_SLIDE -> Slide
        SHOWCASE_MODE_FRAME_WALL -> FrameWall
        SHOWCASE_MODE_FADE -> Fade
        SHOWCASE_MODE_CALENDER -> Calender
//                SHOWCASE_MODE_CUBE -> Cube
//                SHOWCASE_MODE_REVEAL -> Reveal
//                SHOWCASE_MODE_CAROUSEL -> Carousel
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

