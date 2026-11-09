package com.xieqinglin.fitness.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Forest = Color(0xFF246B52)
val Lime = Color(0xFFE5EDB6)
private val LightColors = lightColorScheme(
    primary = Forest, onPrimary = Color.White, primaryContainer = Lime,
    onPrimaryContainer = Color(0xFF183E2E), secondary = Color(0xFF5B705D),
    secondaryContainer = Color(0xFFE7EDDF), background = Color(0xFFF8F9F5),
    surface = Color(0xFFF8F9F5), surfaceContainer = Color.White,
    surfaceVariant = Color(0xFFEEF1E9), outline = Color(0xFF748477),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5D4B7), primaryContainer = Color(0xFF284C39),
    secondaryContainer = Color(0xFF354437), background = Color(0xFF121B16), surface = Color(0xFF121B16),
    surfaceContainer = Color(0xFF1E2922), surfaceVariant = Color(0xFF2A372D),
)

@Composable fun FitnessTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(
            headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 42.sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
        ), content = content)
}
