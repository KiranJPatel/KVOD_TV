package com.iptv.firetv.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptv.firetv.R
import com.iptv.firetv.core.model.AccentColor
import com.iptv.firetv.core.model.AppTheme
import com.iptv.firetv.core.model.ThemeMode

/** Semantic colours Material 3 has no slot for. */
@Immutable
data class IptvColors(
    val elevated: Color, val focusGlow: Color, val live: Color, val success: Color, val warning: Color,
    val divider: Color, val scrim: Color, val isDark: Boolean,
    /** Fill for "on now" items (primary at ~18 %), distinct from the focus ring. */
    val nowTint: Color = Color(0x2E1688FF),
    /** High-contrast focus outline (the theme's text colour), the primary colour supplies only the soft glow. */
    val focusRing: Color = Color(0xFFF1F3F7),
    /** Second accent used for stars, highlights and badges. */
    val tertiary: Color = Color(0xFFFFC857)
)

val LocalIptvColors = staticCompositionLocalOf {
    IptvColors(Color(0xFF232A36), Color(0xFF8AB4FF), Color(0xFFFF4D4F), Color(0xFF2BC48A), Color(0xFFFFB020), Color(0xFF2D3646), Color(0xCC0F1115), true)
}

/** Layout reference width in dp; see [IptvTheme]. */
const val REFERENCE_WIDTH_DP = 1280f

/** Density that makes a display with [longSidePx] pixels on its long side exactly [REFERENCE_WIDTH_DP] dp wide; [fallback] if the size is unknown. */
fun referenceDensity(longSidePx: Int, fallback: Float): Float = if (longSidePx > 0) longSidePx / REFERENCE_WIDTH_DP else fallback

object Dimens {
    /** Overscan-safe margins (about 5% of 1920x1080). */
    val SafeH = 48.dp
    val SafeV = 27.dp
    // Control sizes, chosen against the type scale below: icon ~ button text size, control height ~ text line + padding.
    val ButtonHeight = 44.dp
    val ChipHeight = 38.dp
    val TabHeight = 40.dp
    val CircleButton = 40.dp
    val IconInline = 20.dp
    val IconCircle = 22.dp
    val RoundControl = 52.dp
    val RoundControlPrimary = 64.dp
    val RowHeight = 56.dp
    val OptionRowHeight = 48.dp
    val FieldHeight = 48.dp
    val KeyHeight = 44.dp
    val Gap = 16.dp
    val CardRadius = 18.dp
    /** Large container radius used by panels and sheets (Material 3 "extra large"). */
    val PanelRadius = 28.dp
    val SettingRowHeight = 64.dp
}

private fun onColor(c: Color) = if (c.luminance() > 0.35f) Color(0xFF0A0F18) else Color.White

/** The ten colours that define one theme in one brightness. Everything else is derived from them. */
@Immutable
class Tones(
    val bg: Color, val surface: Color, val elevated: Color, val text: Color, val secondary: Color, val divider: Color,
    val primary: Color, val primaryContainer: Color, val onPrimaryContainer: Color, val tertiary: Color
)

/** Colours of [theme] in the dark or light variant. Also used by the theme gallery in Settings to draw previews. */
fun tonesFor(theme: AppTheme, dark: Boolean): Tones = when (theme) {
        AppTheme.AURORA -> if (dark) Tones(Color(0xFF0E1022), Color(0xFF161934), Color(0xFF222650), Color(0xFFEDEEFF), Color(0xFFAEB2D6), Color(0xFF33386A), Color(0xFFA9B4FF), Color(0xFF2F3A9E), Color(0xFFDDE0FF), Color(0xFF5EE0F5))
            else Tones(Color(0xFFF6F6FF), Color(0xFFFFFFFF), Color(0xFFE8E9FB), Color(0xFF12132B), Color(0xFF4A4D74), Color(0xFFCDCFEF), Color(0xFF3F4BD6), Color(0xFFDEE0FF), Color(0xFF0A1066), Color(0xFF007A8C))
        AppTheme.SPACE_GRAY -> if (dark) Tones(Color(0xFF111214), Color(0xFF1B1C1F), Color(0xFF292B2F), Color(0xFFECEDEF), Color(0xFFB1B4BB), Color(0xFF3A3D43), Color(0xFFFFB547), Color(0xFF5C3C00), Color(0xFFFFE3B0), Color(0xFF9CC5FF))
            else Tones(Color(0xFFF4F4F5), Color(0xFFFFFFFF), Color(0xFFE8E8EA), Color(0xFF141416), Color(0xFF55565C), Color(0xFFD2D3D7), Color(0xFFA85A00), Color(0xFFFFE2B8), Color(0xFF2B1700), Color(0xFF2F5F9E))
        AppTheme.STUDIO -> if (dark) Tones(Color(0xFF0C1514), Color(0xFF132220), Color(0xFF1D3330), Color(0xFFE4F2EF), Color(0xFFA3BDB8), Color(0xFF2B4743), Color(0xFF5FE3C0), Color(0xFF094F41), Color(0xFFBFF5E8), Color(0xFFF4B860))
            else Tones(Color(0xFFF7F6F1), Color(0xFFFFFFFF), Color(0xFFE9EDE6), Color(0xFF10201D), Color(0xFF465C57), Color(0xFFCBD6D0), Color(0xFF0B7A66), Color(0xFFC6F0E4), Color(0xFF00201A), Color(0xFF9A5B00))
}

/** Display font (titles, headings) and text font (body, labels) of a theme. All bundled fonts are under the SIL Open Font License. */
@Immutable
class ThemeFonts(val display: FontFamily, val text: FontFamily)

object AppFonts {
    val Outfit = FontFamily(Font(R.font.outfit_regular, FontWeight.Normal), Font(R.font.outfit_bold, FontWeight.Bold))
    val WorkSans = FontFamily(Font(R.font.worksans_regular, FontWeight.Normal), Font(R.font.worksans_bold, FontWeight.Bold))
    val InstrumentSans = FontFamily(Font(R.font.instrumentsans_regular, FontWeight.Normal), Font(R.font.instrumentsans_bold, FontWeight.Bold))
    val Lora = FontFamily(Font(R.font.lora_regular, FontWeight.Normal), Font(R.font.lora_bold, FontWeight.Bold))

    fun forTheme(theme: AppTheme): ThemeFonts = when (theme) {
        AppTheme.AURORA -> ThemeFonts(Outfit, InstrumentSans)
        AppTheme.SPACE_GRAY -> ThemeFonts(InstrumentSans, WorkSans)
        AppTheme.STUDIO -> ThemeFonts(Lora, WorkSans)
    }
}

/**
 * Three themes (see [AppTheme]), each in a dark and a light variant ([ThemeMode]). All sizes respect a 16 sp-equivalent minimum
 * for 10-foot viewing and are multiplied by the user's text-size setting. An [AccentColor] other than THEME overrides the primary colour.
 */
@Composable
fun IptvTheme(theme: AppTheme, mode: ThemeMode, accent: AccentColor, textScale: Float, uiScale: Float = 1f, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    val t = tonesFor(theme, dark)
    val a = Color(accent.argb)
    val overridden = accent != AccentColor.THEME
    val primary = when {
        !overridden -> t.primary
        dark -> a
        else -> lerp(a, Color.Black, 0.28f)
    }
    val container = if (!overridden) t.primaryContainer else if (dark) lerp(primary, t.bg, 0.65f) else lerp(primary, Color.White, 0.82f)
    val bg = t.bg; val text = t.text
    val scheme: ColorScheme = if (dark) darkColorScheme(
        primary = primary, onPrimary = onColor(primary), primaryContainer = container, onPrimaryContainer = t.onPrimaryContainer,
        secondary = t.tertiary, onSecondary = onColor(t.tertiary), secondaryContainer = t.elevated, onSecondaryContainer = text,
        tertiary = t.tertiary, onTertiary = onColor(t.tertiary),
        background = bg, onBackground = text, surface = t.surface, onSurface = text,
        surfaceVariant = t.elevated, onSurfaceVariant = t.secondary, surfaceTint = primary,
        surfaceContainerLowest = bg, surfaceContainerLow = t.surface, surfaceContainer = t.surface, surfaceContainerHigh = t.elevated, surfaceContainerHighest = t.elevated,
        outline = t.divider, outlineVariant = t.divider,
        error = Color(0xFFFF6B6B), onError = Color(0xFF410002), errorContainer = Color(0xFF5C1A1A), onErrorContainer = Color(0xFFFFDAD6)
    ) else lightColorScheme(
        primary = primary, onPrimary = onColor(primary), primaryContainer = container, onPrimaryContainer = t.onPrimaryContainer,
        secondary = t.tertiary, onSecondary = onColor(t.tertiary), secondaryContainer = t.elevated, onSecondaryContainer = text,
        tertiary = t.tertiary, onTertiary = onColor(t.tertiary),
        background = bg, onBackground = text, surface = t.surface, onSurface = text,
        surfaceVariant = t.elevated, onSurfaceVariant = t.secondary, surfaceTint = primary,
        surfaceContainerLowest = Color.White, surfaceContainerLow = t.surface, surfaceContainer = t.surface, surfaceContainerHigh = t.elevated, surfaceContainerHighest = t.elevated,
        outline = t.divider, outlineVariant = t.divider,
        error = Color(0xFFB42318), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002)
    )
    val extras = IptvColors(
        elevated = t.elevated, focusGlow = primary,
        live = if (dark) Color(0xFFFF4D4F) else Color(0xFFD92D20),
        success = if (dark) Color(0xFF2BC48A) else Color(0xFF12805C),
        warning = if (dark) Color(0xFFFFB020) else Color(0xFFB54708),
        divider = t.divider, scrim = bg.copy(alpha = 0.82f), isDark = dark, nowTint = primary.copy(alpha = 0.18f),
        focusRing = text, tertiary = t.tertiary
    )
    val d = LocalDensity.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // The layout is designed for a 1280 x 720 dp screen. Fire TV devices report different densities depending on model,
    // OS version and the chosen output resolution (720p / 1080p / 4K), so derive the density from the real display size
    // instead: the same layout then fits on every TV and nothing falls off the screen.
    val refDensity = remember(ctx) {
        val dm = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") runCatching { (ctx.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getRealMetrics(dm) }
        val longSide = maxOf(dm.widthPixels, dm.heightPixels)
        referenceDensity(longSide, d.density)
    }
    CompositionLocalProvider(
        LocalIptvColors provides extras,
        LocalDensity provides Density(refDensity * uiScale.coerceIn(0.8f, 1.25f), d.fontScale * textScale)
    ) { MaterialTheme(colorScheme = scheme, typography = tvTypography(theme), content = content) }
}

/** Fallback font for places that have no theme (tests, previews). The themed fonts are applied by [tvTypography]. */
val AppFontFamily: FontFamily = FontFamily.SansSerif

/**
 * The typography specification is written in sp for a 960 dp wide screen. This app lays out on a 1280 dp wide grid (see
 * [REFERENCE_WIDTH_DP]), so every size, line height and letter spacing is multiplied by this factor to keep the same
 * proportions of the screen. The 10-foot viewing distance is the reason none of the sizes are reduced further.
 */
private const val TV_TYPE_SCALE = REFERENCE_WIDTH_DP / 960f

private fun role(size: Float, weight: FontWeight, line: Float, track: Float = 0f) = TextStyle(
    fontFamily = AppFontFamily, fontSize = (size * TV_TYPE_SCALE).sp, fontWeight = weight,
    lineHeight = (line * TV_TYPE_SCALE).sp, letterSpacing = (track * TV_TYPE_SCALE).sp
)

/** Specification roles (size / weight / line height / tracking, before scaling). Identical in every theme. */
object TypeRoles {
    val Hero = role(32f, FontWeight.Bold, 38f, -0.4f)
    val ScreenTitle = role(26f, FontWeight.Bold, 32f, -0.3f)
    val SectionHeading = role(18f, FontWeight.Bold, 23f)
    val ContentTitle = role(15f, FontWeight.SemiBold, 19f)
    val Body = role(14f, FontWeight.Normal, 20f)
    val Metadata = role(12f, FontWeight.Normal, 16f)
    /** Chips and top tabs: the "navigation label" role, kept at metadata size and Medium weight for legibility at 10 feet. */
    val NavLabel = role(12f, FontWeight.Medium, 16f)
    val Caption = role(10f, FontWeight.Normal, 13f)
    val Button = role(15f, FontWeight.Bold, 19f)
}

/** Sizes and weights are the same in every theme; only the font families differ (display font for titles, text font for body and labels). */
fun tvTypography(theme: AppTheme): Typography {
    val f = AppFonts.forTheme(theme)
    fun display(s: TextStyle) = s.copy(fontFamily = f.display)
    fun text(s: TextStyle) = s.copy(fontFamily = f.text)
    return Typography(
        displayLarge = display(TypeRoles.Hero), displayMedium = display(TypeRoles.Hero), displaySmall = display(TypeRoles.ScreenTitle),
        headlineLarge = display(TypeRoles.ScreenTitle), headlineMedium = display(TypeRoles.ScreenTitle), headlineSmall = display(TypeRoles.SectionHeading),
        titleLarge = display(TypeRoles.SectionHeading), titleMedium = display(TypeRoles.ContentTitle), titleSmall = display(TypeRoles.ContentTitle),
        bodyLarge = text(TypeRoles.Body), bodyMedium = text(TypeRoles.Body), bodySmall = text(TypeRoles.Metadata),
        labelLarge = text(TypeRoles.Button), labelMedium = text(TypeRoles.NavLabel), labelSmall = text(TypeRoles.Caption)
    )
}
