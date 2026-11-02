package com.iptv.firetv

import com.iptv.firetv.core.model.QualityMode
import com.iptv.firetv.core.player.QualityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityPolicyTest {
    @Test fun lockedModePicksBestVariantUpToTheCap() {
        assertEquals(1080, QualityPolicy.pick(listOf(2160, 1080, 720, 480), QualityMode.LOCK_1080))
        assertEquals(1080, QualityPolicy.pick(listOf(1080, 720), QualityMode.AUTO_1080))
    }

    @Test fun fourKIsNeverChosenWhenA1080pVariantExists() {
        assertEquals(1080, QualityPolicy.pick(listOf(2160, 1440, 1080), QualityMode.LOCK_1080))
    }

    @Test fun encodersThatEmit1088LinesStillCountAs1080p() {
        assertEquals(1088, QualityPolicy.pick(listOf(1088, 720), QualityMode.LOCK_1080))
    }

    @Test fun streamsThatOnlyOffer720pStayAt720p() {
        assertEquals(720, QualityPolicy.pick(listOf(720, 576, 360), QualityMode.LOCK_1080))
    }

    @Test fun manualModesCapLower() {
        assertEquals(720, QualityPolicy.pick(listOf(1080, 720, 480), QualityMode.P720))
        assertEquals(480, QualityPolicy.pick(listOf(1080, 720, 480), QualityMode.P480))
    }

    @Test fun noVariantWithinCapReturnsNull() { assertNull(QualityPolicy.pick(listOf(2160), QualityMode.LOCK_1080)) }

    @Test fun lockIsTheDefaultPolicy() {
        assertTrue(QualityMode.LOCK_1080.lockHighest); assertEquals(1088, QualityMode.LOCK_1080.maxHeight)
        assertTrue(!QualityMode.AUTO_1080.lockHighest)
    }
}

class StallDetectorTest {
    @Test fun threeStallsInsideTheWindowTrigger() {
        val d = com.iptv.firetv.core.player.StallDetector(windowMs = 90_000, threshold = 3)
        assertTrue(!d.onStall(0)); assertTrue(!d.onStall(30_000)); assertTrue(d.onStall(60_000))
    }

    @Test fun oldStallsExpire() {
        val d = com.iptv.firetv.core.player.StallDetector(windowMs = 90_000, threshold = 3)
        d.onStall(0); d.onStall(10_000)
        assertTrue(!d.onStall(200_000))
    }

    @Test fun resetClearsTheCount() {
        val d = com.iptv.firetv.core.player.StallDetector()
        d.onStall(0); d.onStall(1_000); d.reset()
        assertTrue(!d.onStall(2_000))
    }
}
