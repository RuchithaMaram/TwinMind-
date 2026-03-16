package com.twinmind.recorder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TealDark        = Color(0xFF1A5C78)
val TealMid         = Color(0xFF1E6E8E)
val TealLight       = Color(0xFF2589AD)
val TealUltraLight  = Color(0xFFE8F4F9)
val TealBorder      = Color(0xFFB8DDE9)

val BgWhite         = Color(0xFFFFFFFF)
val BgSurface       = Color(0xFFF5F7F9)
val BgCard          = Color(0xFFF0F4F7)
val BgShareBanner   = Color(0xFFEAF4F8)

val TextDark        = Color(0xFF1A1A1A)
val TextMid         = Color(0xFF4A5568)
val TextLight       = Color(0xFF8A9BB0)
val TextTeal        = TealDark

val OrangeAccent    = Color(0xFFE8820C)
val RedRecord       = Color(0xFF222222)
val GreenCheck      = Color(0xFF34A853)
val DividerColor    = Color(0xFFE5EAF0)

val ActionBlue      = TealDark
val ChipBg          = Color(0xFFEDF3F7)

private val LightColorScheme = lightColorScheme(
    primary          = TealDark,
    onPrimary        = Color.White,
    primaryContainer = TealUltraLight,
    secondary        = TealMid,
    onSecondary      = Color.White,
    background       = BgWhite,
    surface          = BgWhite,
    surfaceVariant   = BgSurface,
    onBackground     = TextDark,
    onSurface        = TextDark,
    onSurfaceVariant = TextMid,
    outline          = DividerColor,
    error            = Color(0xFFD32F2F),
)

@Composable
fun TwinMindTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = TwinMindTypography,
        content     = content
    )
}
