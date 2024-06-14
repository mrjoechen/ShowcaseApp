package com.alpha.showcase.common.ui.theme


import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


private val materialDarkColorScheme = darkColorScheme(
  primary = Blue80,
  onPrimary = Blue20,
  primaryContainer = Blue30,
  onPrimaryContainer = Blue90,
  inversePrimary = Blue40,
  secondary = DarkBlue80,
  onSecondary = DarkBlue20,
  secondaryContainer = DarkBlue30,
  onSecondaryContainer = DarkBlue90,
  tertiary = Yellow80,
  onTertiary = Yellow20,
  tertiaryContainer = Yellow30,
  onTertiaryContainer = Yellow90,
  error = Red80,
  onError = Red20,
  errorContainer = Red30,
  onErrorContainer = Red90,
  background = Grey10,
  onBackground = Grey90,
  surface = Grey10,
  onSurface = Grey80,
  inverseSurface = Grey90,
  inverseOnSurface = Grey20,
  surfaceVariant = BlueGrey30,
  onSurfaceVariant = BlueGrey80,
  outline = BlueGrey60
)

private val materialLightColorScheme = lightColorScheme(
  primary = Blue40,
  onPrimary = Color.White,
  primaryContainer = Blue90,
  onPrimaryContainer = Blue10,
  inversePrimary = Blue80,
  secondary = DarkBlue40,
  onSecondary = Color.White,
  secondaryContainer = DarkBlue90,
  onSecondaryContainer = DarkBlue10,
  tertiary = Yellow40,
  onTertiary = Color.White,
  tertiaryContainer = Yellow90,
  onTertiaryContainer = Yellow10,
  error = Red40,
  onError = Color.White,
  errorContainer = Red90,
  onErrorContainer = Red10,
  background = Grey99,
  onBackground = Grey10,
  surface = Grey99,
  onSurface = Grey10,
  inverseSurface = Grey20,
  inverseOnSurface = Grey95,
  surfaceVariant = BlueGrey90,
  onSurfaceVariant = BlueGrey30,
  outline = BlueGrey50
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit
) {

  val colorScheme = when {
//    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
//      val context = LocalContext.current
//      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
//    }
    darkTheme -> materialDarkColorScheme
    else -> materialLightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = materialTypography,
    shapes = shapes,
    content = content
  )
}