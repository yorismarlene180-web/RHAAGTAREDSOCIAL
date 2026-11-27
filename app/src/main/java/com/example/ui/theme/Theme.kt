package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = BrandMagenta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1226),
    onPrimaryContainer = Color(0xFFFFD8E4),
    secondary = BrandBlue,
    onSecondary = Color.White,
    tertiary = BrandAmber,
    onTertiary = Color(0xFF1F1600),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = BrandMagenta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4EE),
    onPrimaryContainer = Color(0xFF4A0722),
    secondary = BrandBlue,
    onSecondary = Color.White,
    tertiary = BrandAmber,
    onTertiary = Color(0xFF2B1C00),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline
)

val MomentiaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

val StoryRingBrush = Brush.linearGradient(
    colors = listOf(BrandAmber, BrandCoral, BrandMagenta, BrandPurple)
)

val BrandGradientBrush = Brush.horizontalGradient(
    colors = listOf(BrandPurple, BrandMagenta, BrandCoral, BrandAmber)
)

@Composable
fun MomentiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = MomentiaShapes,
        content = content
    )
}
