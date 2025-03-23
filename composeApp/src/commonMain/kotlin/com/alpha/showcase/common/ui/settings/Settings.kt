package com.alpha.showcase.common.ui.settings

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val showTimeAndDate: Boolean = true,
    val fadeMode: FadeMode = FadeMode(),
    val slideMode: SlideMode = SlideMode(),
    val frameWallMode: FrameWallMode = FrameWallMode(),
    val calenderMode: CalenderMode = CalenderMode(),
    val bentoMode: BentoMode = BentoMode(),
    val cubeMode: CubeMode = CubeMode(),
    val carouselMode: CarouselMode = CarouselMode(),
    val revealMode: RevealMode = RevealMode(),
    val showcaseMode: Int = 0,
    val autoOpenLatestSource: Boolean = false,
    val recursiveDirContent: Boolean = false,
    val supportVideo: Boolean = false,
    val autoRefresh: Boolean = false,
    val zoomEffect: Boolean = false,
    val sortRule: Int = 0,
    val autoFullScreen: Boolean = false,
) {

  companion object {
    fun getDefaultInstance(): Settings {
      return Settings()
    }
  }

  @Serializable
  data class SlideMode(
    val intervalTimeUnit: Int = 0,
    val intervalTime: Int = 0,
    val showTimeProgressIndicator: Boolean = false,
    val showContentMetaInfo: Boolean = false,
    val orientation: Int = 0,
    val displayMode: Int = 0,
    val sortRule: Int = 0,
    val effect: Int = 0
  )

  @Serializable
  data class FadeMode(
    val displayMode: Int = 0,
    val intervalTimeUnit: Int = 0,
    val intervalTime: Int = 0,
    val showTimeProgressIndicator: Boolean = false,
    val showContentMetaInfo: Boolean = false,
    val sortRule: Int = 0

  )

  @Serializable
  data class FrameWallMode(
    val frameStyle: Int = 0,
    val interval: Int = 5,
    val matrixSizeRow: Int = 1,
    val matrixSizeColumn: Int = 2,
    val displayMode: Int = 0
  )

  @Serializable
  data class CalenderMode(
    val autoPlay: Boolean = true,
    val intervalTime: Int = 2,
    val intervalTimeUnit: Int = 0,
    val showContentMetaInfo: Boolean = false
  )

    @Serializable
    data class BentoMode(
        val bentoStyle: Int = 0,
        val interval: Int = 5
    )

    @Serializable
    data class CubeMode(
        val interval: Int = 5
    )

    @Serializable
    data class RevealMode(
        val interval: Int = 5
    )

    @Serializable
    data class CarouselMode(
        val interval: Int = 5
    )


}