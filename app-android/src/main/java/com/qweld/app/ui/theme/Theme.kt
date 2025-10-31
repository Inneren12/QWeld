package com.qweld.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF2741C3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E4FF),
    onPrimaryContainer = Color(0xFF0E1C5A),
    secondary = Color(0xFF00658B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC4E8FF),
    onSecondaryContainer = Color(0xFF001F2E),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE1E3F1),
    onSurfaceVariant = Color(0xFF1C1F2B),
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFB7C2FF),
    onPrimary = Color(0xFF00115A),
    primaryContainer = Color(0xFF0F2894),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFF7ED0F4),
    onSecondary = Color(0xFF003547),
    secondaryContainer = Color(0xFF004D66),
    onSecondaryContainer = Color(0xFFC4E8FF),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    surfaceVariant = Color(0xFF3F4256),
    onSurfaceVariant = Color(0xFFDEE1F0),
  )

@Composable
fun QWeldTheme(
  useDarkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colors = if (useDarkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(
    colorScheme = colors,
    typography = MaterialTheme.typography,
    shapes = MaterialTheme.shapes,
    content = content,
  )
}
