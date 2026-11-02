package com.iptv.firetv

import com.iptv.firetv.core.ui.REFERENCE_WIDTH_DP
import com.iptv.firetv.core.ui.referenceDensity
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutGridTest {
    @Test fun everyOutputResolutionBecomesTheSameDpWidth() {
        for (px in listOf(1280, 1920, 3840)) assertEquals(REFERENCE_WIDTH_DP, px / referenceDensity(px, 1f), 0.01f)
    }

    @Test fun unknownDisplaySizeKeepsTheSystemDensity() { assertEquals(2f, referenceDensity(0, 2f), 0f) }

    @Test fun fullHdGivesTheClassicTvDpiOfOnePointFive() { assertEquals(1.5f, referenceDensity(1920, 1f), 0.001f) }
}
