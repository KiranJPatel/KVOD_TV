package com.iptv.firetv

import com.iptv.firetv.core.model.AppTheme
import com.iptv.firetv.core.model.ThemeMode
import com.iptv.firetv.core.player.SubBackground
import com.iptv.firetv.core.player.SubColor
import com.iptv.firetv.core.player.SubEdge
import com.iptv.firetv.core.player.SubPosition
import com.iptv.firetv.core.player.SubSize
import com.iptv.firetv.core.player.SubtitlePreset
import com.iptv.firetv.core.player.SubtitleStyle
import com.iptv.firetv.core.ui.tonesFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleStyleTest {
    @Test fun defaultIsTheCinemaPreset() {
        val s = SubtitleStyle()
        assertEquals(SubtitlePreset.CINEMA.style, s)
        assertEquals(0xFFFFFFFF.toInt(), s.color.argb); assertEquals(0, s.backgroundArgb); assertEquals(SubEdge.OUTLINE, s.effectiveEdge)
    }

    @Test fun classicPresetIsTheOldWhiteOnBlackBox() {
        val s = SubtitlePreset.CLASSIC.style
        assertEquals(0xFF000000.toInt(), s.backgroundArgb); assertEquals(SubEdge.NONE, s.effectiveEdge)
    }

    @Test fun presetsAreDistinctAndMatchingRoundTrips() {
        assertEquals(6, SubtitlePreset.entries.size)
        assertEquals(6, SubtitlePreset.entries.map { it.style }.toSet().size)
        SubtitlePreset.entries.forEach { assertEquals(it, SubtitlePreset.matching(it.style)) }
        assertNull("a customised style is not a preset", SubtitlePreset.matching(SubtitlePreset.CINEMA.style.copy(size = SubSize.LARGE)))
    }

    @Test fun nextPresetCyclesAndStartsFromTheFirstWhenCustom() {
        var p = SubtitlePreset.CLASSIC
        repeat(SubtitlePreset.entries.size) { p = SubtitlePreset.next(p.style) }
        assertEquals(SubtitlePreset.CLASSIC, p)
        assertEquals(SubtitlePreset.entries.first(), SubtitlePreset.next(SubtitleStyle(color = SubColor.PINK, size = SubSize.XLARGE)))
    }

    @Test fun largePrintIsExtraLargeAndRaised() {
        val s = SubtitlePreset.LARGE_PRINT.style
        assertEquals(SubSize.XLARGE, s.size); assertEquals(SubPosition.RAISED, s.position)
    }

    @Test fun transparentBackgroundHasNoBoxAndGetsAnOutlineAutomatically() {
        val s = SubtitleStyle(background = SubBackground.TRANSPARENT)
        assertEquals(0, s.backgroundArgb); assertEquals(SubEdge.OUTLINE, s.effectiveEdge)
        assertEquals(SubEdge.OUTLINE, SubtitleStyle(background = SubBackground.SEMI).effectiveEdge)
        assertEquals("an explicit edge choice wins over automatic", SubEdge.SHADOW, SubtitleStyle(background = SubBackground.TRANSPARENT, edge = SubEdge.SHADOW).effectiveEdge)
    }

    @Test fun semiTransparentBoxIsPartlyOpaque() {
        val a = (SubtitleStyle(background = SubBackground.SEMI).backgroundArgb ushr 24) and 0xFF
        assertTrue(a in 1..254)
    }

    @Test fun blackTextGetsAWhiteOutlineSoItStaysReadable() {
        assertEquals(0xFFFFFFFF.toInt(), SubtitleStyle(color = SubColor.BLACK).edgeArgb)
        assertEquals(0xFF000000.toInt(), SubtitleStyle(color = SubColor.YELLOW).edgeArgb)
    }

    @Test fun sizesGrowAndMediumMatchesThePlayerDefault() {
        val f = SubSize.entries.map { it.fraction }
        assertEquals(f.sorted(), f); assertEquals(0.0533f, SubSize.MEDIUM.fraction, 0.0001f)
    }

    @Test fun positionsMoveTheTextUp() {
        val p = SubPosition.entries.map { it.bottomPadding }
        assertEquals(p.sorted(), p); assertEquals(0.08f, SubPosition.BOTTOM.bottomPadding, 0.0001f)
    }

    @Test fun parseFallsBackToTheDefaultForUnknownNames() {
        assertEquals(SubColor.YELLOW, SubtitleStyle.parse("YELLOW", SubColor.WHITE))
        assertEquals(SubColor.WHITE, SubtitleStyle.parse("nonsense", SubColor.WHITE)); assertEquals(SubColor.WHITE, SubtitleStyle.parse(null, SubColor.WHITE))
    }
}

class ThemeModelTest {
    @Test fun namesFromEarlierBuildsMapToANewThemeAndMode() {
        assertEquals(AppTheme.AURORA to null, AppTheme.fromStored("MIDNIGHT"))
        assertEquals(AppTheme.AURORA to ThemeMode.LIGHT, AppTheme.fromStored("LIGHT"))
        assertEquals(AppTheme.SPACE_GRAY to ThemeMode.DARK, AppTheme.fromStored("MATERIAL_DARK")); assertEquals(AppTheme.SPACE_GRAY to ThemeMode.DARK, AppTheme.fromStored("MATERIAL_GRAY"))
        assertEquals(AppTheme.SPACE_GRAY to null, AppTheme.fromStored("GRAPHITE")); assertEquals(AppTheme.STUDIO to null, AppTheme.fromStored("FOREST")); assertEquals(AppTheme.STUDIO to null, AppTheme.fromStored("OCEAN"))
        assertEquals(AppTheme.AURORA to null, AppTheme.fromStored("EMBER")); assertEquals(AppTheme.AURORA to null, AppTheme.fromStored("ORCHID"))
        assertEquals(AppTheme.STUDIO to null, AppTheme.fromStored("STUDIO"))
        assertEquals(AppTheme.AURORA to null, AppTheme.fromStored("garbage")); assertEquals(AppTheme.AURORA to null, AppTheme.fromStored(null))
    }

    @Test fun everyThemeHasADistinctDarkAndLightVariant() {
        assertEquals(3, AppTheme.entries.size)
        AppTheme.entries.forEach { t ->
            val d = tonesFor(t, true); val l = tonesFor(t, false)
            assertNotEquals("$t dark and light backgrounds differ", d.bg, l.bg)
            assertTrue("$t dark background is darker than its light background", d.bg.red + d.bg.green + d.bg.blue < l.bg.red + l.bg.green + l.bg.blue)
        }
        assertEquals("every theme has its own primary colour", 3, AppTheme.entries.map { tonesFor(it, true).primary }.toSet().size)
    }

    @Test fun textIsReadableOnBackgroundAndSurfaceInEveryVariant() {
        fun lum(c: androidx.compose.ui.graphics.Color): Double {
            fun f(v: Float) = if (v <= 0.03928f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
            return 0.2126 * f(c.red) + 0.7152 * f(c.green) + 0.0722 * f(c.blue)
        }
        fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }
        AppTheme.entries.forEach { t -> listOf(true, false).forEach { dark ->
            val c = tonesFor(t, dark)
            assertTrue("$t dark=$dark text/background", contrast(c.text, c.bg) >= 7.0)
            assertTrue("$t dark=$dark text/surface", contrast(c.text, c.surface) >= 7.0)
            assertTrue("$t dark=$dark secondary/background", contrast(c.secondary, c.bg) >= 4.5)
            assertTrue("$t dark=$dark onContainer/container", contrast(c.onPrimaryContainer, c.primaryContainer) >= 7.0)
        } }
    }
}
