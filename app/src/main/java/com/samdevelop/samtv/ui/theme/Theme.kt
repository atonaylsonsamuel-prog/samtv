package com.samdevelop.samtv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Paleta oficial SAM TV. */
object SamColors {
    val Background = Color(0xFF070A14)
    val Surface = Color(0xFF10162A)
    val SurfaceHigh = Color(0xFF182039)
    val Purple = Color(0xFF741BFF)
    val PurpleAccent = Color(0xFFA020F0)
    val Blue = Color(0xFF1677FF)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB7BED4)
    val Success = Color(0xFF3FBF7F)
    val Error = Color(0xFFE5534B)
    val Outline = Color(0xFF2A3354)

    val BrandGradient = Brush.linearGradient(listOf(Purple, PurpleAccent, Blue))
}

private val colorScheme = darkColorScheme(
    primary = SamColors.Purple,
    onPrimary = SamColors.TextPrimary,
    primaryContainer = Color(0xFF2B1260),
    onPrimaryContainer = SamColors.TextPrimary,
    secondary = SamColors.Blue,
    onSecondary = SamColors.TextPrimary,
    secondaryContainer = Color(0xFF0E2D5E),
    onSecondaryContainer = SamColors.TextPrimary,
    tertiary = SamColors.PurpleAccent,
    background = SamColors.Background,
    onBackground = SamColors.TextPrimary,
    surface = SamColors.Background,
    onSurface = SamColors.TextPrimary,
    surfaceVariant = SamColors.Surface,
    onSurfaceVariant = SamColors.TextSecondary,
    surfaceContainerLowest = SamColors.Background,
    surfaceContainerLow = SamColors.Surface,
    surfaceContainer = SamColors.Surface,
    surfaceContainerHigh = SamColors.SurfaceHigh,
    surfaceContainerHighest = SamColors.SurfaceHigh,
    outline = SamColors.Outline,
    outlineVariant = SamColors.Outline,
    error = SamColors.Error,
    onError = SamColors.TextPrimary,
)

private val typography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val BrandTitleStyle = TextStyle(fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.sp)

@Composable
fun SamTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = shapes, content = content)
}
