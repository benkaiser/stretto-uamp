package com.example.android.uamp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Palette taken from the launcher icon: a diagonal sky-blue to deep-blue gradient.
val SkyBlue = Color(0xFF47BFFF)
val DeepBlue = Color(0xFF0064AE)
val MidBlue = Color(0xFF0E86D6)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A72CC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E8FF),
    onPrimaryContainer = Color(0xFF00213F),
    secondary = Color(0xFF00809E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEEF9),
    onSecondaryContainer = Color(0xFF001F29),
    tertiary = Color(0xFF3F5BD9),
    background = Color(0xFFF3F8FE),
    onBackground = Color(0xFF0C1620),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0C1620),
    surfaceVariant = Color(0xFFE4EDF7),
    onSurfaceVariant = Color(0xFF47566A),
    surfaceContainer = Color(0xFFEAF2FB),
    surfaceContainerHigh = Color(0xFFE0EBF7),
    outline = Color(0xFF7A8AA0),
    outlineVariant = Color(0xFFCAD6E4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CC4FF),
    onPrimary = Color(0xFF00315A),
    primaryContainer = Color(0xFF0A4D8C),
    onPrimaryContainer = Color(0xFFD5E8FF),
    secondary = Color(0xFF5FD3F3),
    onSecondary = Color(0xFF003543),
    secondaryContainer = Color(0xFF0D4B5E),
    onSecondaryContainer = Color(0xFFCDEEF9),
    tertiary = Color(0xFF9DB1FF),
    background = Color(0xFF080D14),
    onBackground = Color(0xFFE3EBF4),
    surface = Color(0xFF0F1721),
    onSurface = Color(0xFFE3EBF4),
    surfaceVariant = Color(0xFF1A2635),
    onSurfaceVariant = Color(0xFFA9B8CB),
    surfaceContainer = Color(0xFF131D29),
    surfaceContainerHigh = Color(0xFF1A2635),
    outline = Color(0xFF6C7C92),
    outlineVariant = Color(0xFF2A3748),
)

private val StrettoShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp),
)

private val StrettoTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

@Composable
fun StrettoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = StrettoShapes,
        typography = StrettoTypography,
        content = content
    )
}

/** The signature diagonal gradient (matches the launcher icon). */
fun brandBrush(dark: Boolean = false): Brush {
    val start = if (dark) Color(0xFF1B9BE8) else SkyBlue
    val end = if (dark) Color(0xFF00467D) else DeepBlue
    return Brush.linearGradient(
        colors = listOf(end, MidBlue, start),
        start = Offset(0f, Float.POSITIVE_INFINITY),
        end = Offset(Float.POSITIVE_INFINITY, 0f)
    )
}
