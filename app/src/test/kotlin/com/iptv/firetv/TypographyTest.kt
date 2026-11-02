package com.iptv.firetv

import androidx.compose.ui.text.font.FontWeight
import com.iptv.firetv.core.model.AppTheme
import com.iptv.firetv.core.ui.REFERENCE_WIDTH_DP
import com.iptv.firetv.core.ui.TypeRoles
import com.iptv.firetv.core.ui.tvTypography
import org.junit.Assert.assertEquals
import org.junit.Test

class TypographyTest {
    private val k = REFERENCE_WIDTH_DP / 960f

    @Test fun rolesFollowTheSpecificationTable() {
        fun check(style: androidx.compose.ui.text.TextStyle, size: Float, line: Float, weight: FontWeight, track: Float = 0f) {
            assertEquals(size * k, style.fontSize.value, 0.01f)
            assertEquals(line * k, style.lineHeight.value, 0.01f)
            assertEquals(weight, style.fontWeight)
            assertEquals(track * k, style.letterSpacing.value, 0.01f)
        }
        check(TypeRoles.Hero, 32f, 38f, FontWeight.Bold, -0.4f)
        check(TypeRoles.ScreenTitle, 26f, 32f, FontWeight.Bold, -0.3f)
        check(TypeRoles.SectionHeading, 18f, 23f, FontWeight.Bold)
        check(TypeRoles.ContentTitle, 15f, 19f, FontWeight.SemiBold)
        check(TypeRoles.Body, 14f, 20f, FontWeight.Normal)
        check(TypeRoles.Metadata, 12f, 16f, FontWeight.Normal)
        check(TypeRoles.Caption, 10f, 13f, FontWeight.Normal)
        check(TypeRoles.Button, 15f, 19f, FontWeight.Bold)
    }

    @Test fun everyThemeUsesTheSameSizesAndWeights() {
        // Themes differ in their font families only; sizes, line heights, tracking and weights are identical so layouts never change with the theme.
        fun sig(t: androidx.compose.material3.Typography) = listOf(t.displayLarge, t.headlineMedium, t.titleLarge, t.titleSmall, t.bodyLarge, t.bodySmall, t.labelLarge, t.labelMedium, t.labelSmall)
            .map { listOf(it.fontSize, it.lineHeight, it.letterSpacing, it.fontWeight) }
        val base = sig(tvTypography(AppTheme.AURORA))
        AppTheme.entries.forEach { assertEquals(base, sig(tvTypography(it))) }
    }

    @Test fun themesUseDistinctFontFamiliesWhereDesigned() {
        assertEquals(3, AppTheme.entries.size)
        val families = AppTheme.entries.map { tvTypography(it).titleLarge.fontFamily }.toSet()
        assert(families.size == 3) { "each theme has its own display font, got ${families.size}" }
    }

    @Test fun bodyTextIsNeverSmallerThanMetadataAndTitlesAreNeverSmallerThanBody() {
        val t = tvTypography(AppTheme.AURORA)
        assert(t.bodyLarge.fontSize.value >= t.bodySmall.fontSize.value)
        assert(t.titleSmall.fontSize.value >= t.bodyLarge.fontSize.value)
        assert(t.headlineSmall.fontSize.value >= t.titleMedium.fontSize.value)
        assert(t.displayLarge.fontSize.value >= t.headlineLarge.fontSize.value)
    }
}
