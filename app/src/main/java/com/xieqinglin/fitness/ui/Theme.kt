package com.xieqinglin.fitness.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

val Forest = Color(0xFF3159D9)
val Lime = Color(0xFFEAF0FF)
private val LightColors = lightColorScheme(
    primary = Forest, onPrimary = Color.White, primaryContainer = Lime,
    onPrimaryContainer = Color(0xFF243C84), secondary = Color(0xFF267B72),
    onSecondary = Color.White, secondaryContainer = Color(0xFFDDF3ED),
    tertiary = Color(0xFFD68437), onTertiary = Color.White,
    background = Color(0xFFF3F5F9), onBackground = Color(0xFF18243E),
    surface = Color(0xFFF3F5F9), onSurface = Color(0xFF18243E), surfaceContainer = Color.White,
    surfaceVariant = Color(0xFFEDF0F6), onSurfaceVariant = Color(0xFF64718A),
    outline = Color(0xFF8B96AA), outlineVariant = Color(0xFFE1E6F0),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C5FF), primaryContainer = Color(0xFF23376B), onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFF8CDACB), secondaryContainer = Color(0xFF21443F), tertiary = Color(0xFFF3BA7B),
    background = Color(0xFF101623), surface = Color(0xFF101623), onSurface = Color(0xFFE4E9F4),
    surfaceContainer = Color(0xFF1B2334), surfaceVariant = Color(0xFF252F43),
    onSurfaceVariant = Color(0xFFAAB6CB), outlineVariant = Color(0xFF344056),
)

@Composable fun FitnessTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(
            headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 42.sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
        ), shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)), content = content)
}
