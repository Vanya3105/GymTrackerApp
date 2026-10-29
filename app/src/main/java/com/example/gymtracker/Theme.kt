package com.example.gymtracker

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ============================================================================
// COLORS
// Two palettes: warm graphite (dark) and warm paper (light), one ember accent.
// ============================================================================

@Immutable
data class GymPalette(
    val isDark: Boolean,
    val bgPrimary: Color,
    val bgCard: Color,
    val bgElevated: Color,
    val bgControl: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val accentPrimary: Color,
    val onAccentPrimary: Color,
    val accentSoft: Color,
    val accentSecondary: Color,
    val accentSecondarySoft: Color,
    val successSurface: Color,
    val successText: Color,
    val warningSurface: Color,
    val warningText: Color,
    val dangerSurface: Color,
    val dangerText: Color,
    val shadow: Color
)

val DarkPalette = GymPalette(
    isDark = true,
    bgPrimary = Color(0xFF111113),
    bgCard = Color(0xFF1B1B1E),
    bgElevated = Color(0xFF232327),
    bgControl = Color(0xFF2A2A2F),
    textPrimary = Color(0xFFF3F2F0),
    textSecondary = Color(0xFFA3A1A8),
    textTertiary = Color(0xFF6E6C74),
    borderSubtle = Color(0xFF28282D),
    borderStrong = Color(0xFF3B3B42),
    accentPrimary = Color(0xFFFF7A4D),
    onAccentPrimary = Color(0xFF1C0C05),
    accentSoft = Color(0xFF3A2219),
    accentSecondary = Color(0xFF6CB0F2),
    accentSecondarySoft = Color(0xFF1B2836),
    successSurface = Color(0xFF16271E),
    successText = Color(0xFF63D29C),
    warningSurface = Color(0xFF2D2416),
    warningText = Color(0xFFEDB95C),
    dangerSurface = Color(0xFF2F1B1B),
    dangerText = Color(0xFFFF8078),
    shadow = Color(0xFF000000)
)

val LightPalette = GymPalette(
    isDark = false,
    bgPrimary = Color(0xFFF5F3EF),
    bgCard = Color(0xFFFFFFFF),
    bgElevated = Color(0xFFFFFFFF),
    bgControl = Color(0xFFF0EDE7),
    textPrimary = Color(0xFF1C1B1A),
    textSecondary = Color(0xFF6B6863),
    textTertiary = Color(0xFFA19D96),
    borderSubtle = Color(0xFFE8E4DD),
    borderStrong = Color(0xFFD5D0C7),
    accentPrimary = Color(0xFFD9512A),
    onAccentPrimary = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFFCE8E0),
    accentSecondary = Color(0xFF2563C9),
    accentSecondarySoft = Color(0xFFE3ECFA),
    successSurface = Color(0xFFE3F2E9),
    successText = Color(0xFF1D7A4B),
    warningSurface = Color(0xFFFBF0DA),
    warningText = Color(0xFF94600F),
    dangerSurface = Color(0xFFFBE7E4),
    dangerText = Color(0xFFB4372C),
    shadow = Color(0xFF5A4A3A)
)

fun isSystemDark(context: Context): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/** Resolves "System / Light / Dark" to a palette. */
fun paletteFor(themeChoice: String, systemDark: Boolean): GymPalette = when (themeChoice) {
    THEME_LIGHT -> LightPalette
    THEME_DARK -> DarkPalette
    else -> if (systemDark) DarkPalette else LightPalette
}

fun paletteFor(context: Context, themeChoice: String): GymPalette = paletteFor(themeChoice, isSystemDark(context))

val LocalPalette = staticCompositionLocalOf { DarkPalette }

// ============================================================================
// TYPE — Inter (bundled, OFL; see licenses/Inter-OFL.txt), sentence case, few weights,
// tabular numbers for anything that counts.
// ============================================================================

/**
 * Fixed weights of Inter rather than its variable font: laying out text with a variable font
 * (each weight computed from one file at run time) measured ~40% slower, and every screen lays
 * out dozens of texts on its first frame. The files are instances of the variable font at these
 * weights, trimmed to Latin, Cyrillic, Greek and the symbols the app uses.
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** Headings and big numbers: Inter's display cut, tighter and crisper when large. */
val InterDisplay = FontFamily(
    Font(R.font.inter_display_regular, FontWeight.Normal),
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
    Font(R.font.inter_display_extrabold, FontWeight.ExtraBold)
)

object AppType {
    val display = TextStyle(fontFamily = InterDisplay, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
    val title = TextStyle(fontFamily = InterDisplay, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val headline = TextStyle(fontFamily = Inter, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val body = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.1).sp)
    val bodyStrong = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp)
    val label = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val caption = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val overline = TextStyle(fontFamily = Inter, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
    val number = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val bigNumber = TextStyle(fontFamily = InterDisplay, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, fontFeatureSettings = "tnum")
}

@Composable
fun GymTheme(palette: GymPalette, content: @Composable () -> Unit) {
    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accentPrimary,
            onPrimary = palette.onAccentPrimary,
            background = palette.bgPrimary,
            onBackground = palette.textPrimary,
            surface = palette.bgCard,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.bgControl,
            onSurfaceVariant = palette.textSecondary,
            surfaceContainerLow = palette.bgCard,
            surfaceContainer = palette.bgElevated,
            surfaceContainerHigh = palette.bgElevated,
            outline = palette.borderStrong,
            outlineVariant = palette.borderSubtle,
            error = palette.dangerText
        )
    } else {
        lightColorScheme(
            primary = palette.accentPrimary,
            onPrimary = palette.onAccentPrimary,
            background = palette.bgPrimary,
            onBackground = palette.textPrimary,
            surface = palette.bgCard,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.bgControl,
            onSurfaceVariant = palette.textSecondary,
            surfaceContainerLow = palette.bgCard,
            surfaceContainer = palette.bgElevated,
            surfaceContainerHigh = palette.bgElevated,
            outline = palette.borderStrong,
            outlineVariant = palette.borderSubtle,
            error = palette.dangerText
        )
    }
    // Every Material slot in Inter, so text fields, menus and dialogs match the rest.
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = InterDisplay),
        displayMedium = base.displayMedium.copy(fontFamily = InterDisplay),
        displaySmall = base.displaySmall.copy(fontFamily = InterDisplay),
        headlineLarge = base.headlineLarge.copy(fontFamily = InterDisplay),
        headlineMedium = base.headlineMedium.copy(fontFamily = InterDisplay),
        headlineSmall = AppType.title,
        titleLarge = AppType.title,
        titleMedium = AppType.headline,
        titleSmall = base.titleSmall.copy(fontFamily = Inter),
        bodyLarge = AppType.body,
        bodyMedium = AppType.body,
        bodySmall = base.bodySmall.copy(fontFamily = Inter),
        labelLarge = AppType.label,
        labelMedium = base.labelMedium.copy(fontFamily = Inter),
        labelSmall = base.labelSmall.copy(fontFamily = Inter)
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}
