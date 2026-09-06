package com.agentkosticka.playbox.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Shared ARGB token used by Compose and bitmap widget renderers. */
const val NOTHING_RED_ARGB: Int = -2680543
val NothingRed = Color(NOTHING_RED_ARGB)
val Ink = Color(0xFF080808)
val Panel = Color(0xFF151515)
val Muted = Color(0xFF9A9A9A)

private val PlayboxColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = NothingRed,
    onSecondary = Color.White,
    background = Ink,
    onBackground = Color.White,
    surface = Panel,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF232323),
    onSurfaceVariant = Color(0xFFD0D0D0),
    error = Color(0xFFFF6464),
)

@Composable
fun PlayboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PlayboxColors, content = content)
}
