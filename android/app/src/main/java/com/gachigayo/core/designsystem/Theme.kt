package com.gachigayo.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 프로토타입(docs/prototype) 색상
object GachiColors {
    val Primary = Color(0xFF7182F6)
    val Butter = Color(0xFFFFE27A)
    val Ink = Color(0xFF3A3352)
    val Muted = Color(0xFF8A85A0)
    val Background = Color(0xFFF1EEFA)
    val Surface = Color(0xFFFFFFFF)
    val Green = Color(0xFF43B07A)
}

private val LightColors = lightColorScheme(
    primary = GachiColors.Primary,
    onPrimary = Color.White,
    secondary = GachiColors.Butter,
    onSecondary = GachiColors.Ink,
    background = GachiColors.Background,
    onBackground = GachiColors.Ink,
    surface = GachiColors.Surface,
    onSurface = GachiColors.Ink,
)

@Composable
fun GachiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
