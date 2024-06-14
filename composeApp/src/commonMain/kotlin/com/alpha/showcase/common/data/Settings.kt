package com.alpha.showcase.common.data

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
  //
  val showTimeAndDate: Boolean = true,
  val fadeMode: FadeMode = FadeMode(),
  val slideMode: SlideMode = SlideMode(),
  val frameWallMode: FrameWallMode = FrameWallMode(),

  val showcaseMode: Int = 0,
  val autoOpenLatestSource: Boolean = false,
  val recursiveDirContent: Boolean = false,
  val supportVideo: Boolean = false,
  val autoRefresh: Boolean = false,
  val zoomEffect: Boolean = false,
  val sortRule: Int = 0,
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
    val sortRule: Int = 0
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
    val matrixSizeRows: Int = 1,
    val matrixSizeColumns: Int = 2
  )
}