package com.gachigayo.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 대표 색: 로드 틸 (컬러 시안 A)
object GachiColors {
    val Primary = Color(0xFF087F70)       // 대표 색. 흰 글씨 대비 4.9:1
    val PrimaryLight = Color(0xFF3CC4B0)  // 야간 모드 경로선·강조
    val Accent = Color(0xFFFFC845)        // 질문·알림 말풍선 (글씨는 Ink)
    val Ink = Color(0xFF1B2430)
    val Muted = Color(0xFF5B6673)
    val Line = Color(0xFFE2E6EA)
    val Soft = Color(0xFFF1F3F5)
    val Background = Color(0xFFF7F8F9)
    val Surface = Color(0xFFFFFFFF)

    // 함께 가는 차 (내 차는 Primary)
    val Car2 = Color(0xFFE8772E)
    val Car3 = Color(0xFF5468E8)
}

private val LightColors = lightColorScheme(
    primary = GachiColors.Primary,
    onPrimary = Color.White,
    secondary = GachiColors.Accent,
    onSecondary = GachiColors.Ink,
    background = GachiColors.Background,
    onBackground = GachiColors.Ink,
    surface = GachiColors.Surface,
    onSurface = GachiColors.Ink,
    outline = GachiColors.Line,
)

private val DarkColors = darkColorScheme(
    primary = GachiColors.PrimaryLight,
    onPrimary = GachiColors.Ink,
    secondary = GachiColors.Accent,
    onSecondary = GachiColors.Ink,
    background = Color(0xFF161C23),
    onBackground = Color(0xFFEEF2F5),
    surface = Color(0xFF1F262F),
    onSurface = Color(0xFFEEF2F5),
    outline = Color(0xFF36404C),
)

@Composable
fun GachiTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
