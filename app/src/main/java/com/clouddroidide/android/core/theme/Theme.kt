package com.clouddroidide.android.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object StudioColors {
    val Background = Color(0xFF0E1116)
    val Surface = Color(0xFF161B22)
    val SurfaceElevated = Color(0xFF1F242D)
    val Divider = Color(0xFF2B313C)
    val TextPrimary = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF8B949E)
    val AccentBlue = Color(0xFF58A6FF)
    val AccentGreen = Color(0xFF3FB950)
    val AccentAmber = Color(0xFFD29922)
    val AccentRed = Color(0xFFF85149)
}

private val StudioColorScheme = darkColorScheme(
    primary = StudioColors.AccentBlue,
    onPrimary = StudioColors.Background,
    primaryContainer = Color(0xFF1A2A3E),
    onPrimaryContainer = StudioColors.TextPrimary,
    secondary = StudioColors.AccentGreen,
    onSecondary = StudioColors.Background,
    secondaryContainer = Color(0xFF172B1E),
    onSecondaryContainer = StudioColors.TextPrimary,
    tertiary = StudioColors.AccentAmber,
    onTertiary = StudioColors.Background,
    background = StudioColors.Background,
    onBackground = StudioColors.TextPrimary,
    surface = StudioColors.Surface,
    onSurface = StudioColors.TextPrimary,
    surfaceVariant = StudioColors.SurfaceElevated,
    onSurfaceVariant = StudioColors.TextSecondary,
    outline = StudioColors.Divider,
    outlineVariant = StudioColors.Divider,
    error = StudioColors.AccentRed,
    onError = StudioColors.Background,
    errorContainer = Color(0xFF3A181A),
    onErrorContainer = StudioColors.AccentRed
)

private val StudioTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = StudioColors.TextPrimary,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = StudioColors.TextPrimary
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = StudioColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = StudioColors.TextPrimary
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = StudioColors.TextSecondary
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = StudioColors.TextPrimary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = StudioColors.TextSecondary
    )
)

@Composable
fun CloudDroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudioColorScheme,
        typography = StudioTypography,
        content = content
    )
}
