package com.iptv.firetv

import com.iptv.firetv.core.common.ChannelNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelNamingTest {
    private fun key(n: String) = ChannelNaming.dupKey(n, "id")

    @Test fun qualityTagsDoNotChangeTheKey() {
        val k = key("Star Sports 1")
        assertEquals(k, key("Star Sports 1 HD"))
        assertEquals(k, key("Star Sports 1 FHD"))
        assertEquals(k, key("Star Sports 1 [SD]"))
        assertEquals(k, key("Star Sports 1 (1080p) HEVC"))
        assertEquals(k, key("STAR SPORTS 1 4K"))
        assertEquals(k, key("Star Sports 1 - Backup"))
    }

    @Test fun differentChannelsStayDifferent() {
        assertNotEquals(key("Star Sports 1"), key("Star Sports 1 Hindi HD"))
        assertNotEquals(key("Star Sports 1"), key("Star Sports 2"))
        assertNotEquals(key("Zee TV East"), key("Zee TV West"))
        assertNotEquals(key("UK: BBC One"), key("US: BBC One"))
    }

    @Test fun tagsInsideWordsAreNotStripped() {
        // "hd" inside "Shd..." or "sd" inside "Msd" must stay: only whole-word tags are noise.
        assertNotEquals(key("Msd News"), key("M News"))
        assertNotEquals(key("Ahdnews"), key("Anews"))
    }

    @Test fun keyIsNeverEmpty() {
        assertTrue(key("HD").isNotEmpty())
        assertTrue(key("***").isNotEmpty())
        assertEquals("#id", key("***"))
    }

    @Test fun indicNamesKeepTheirVowelSigns() {
        assertNotEquals(key("आज तक"), key("आज तकि"))
        assertEquals(key("आज तक"), key("आज तक HD"))
    }

    @Test fun rankPrefersFullHdThenHdThenUntaggedThen4kThenSd() {
        val order = listOf("X FHD", "X HD", "X", "X 4K", "X SD").map { ChannelNaming.qualityRank(it) }
        assertEquals(order.sortedDescending(), order)
        assertEquals(5, order.first()); assertEquals(1, order.last())
    }

    @Test fun placeholderTvgIdsAreNotUsedForMatching() {
        assertEquals("", ChannelNaming.usableTvgId(null))
        assertEquals("", ChannelNaming.usableTvgId("  "))
        assertEquals("", ChannelNaming.usableTvgId("None"))
        assertEquals("", ChannelNaming.usableTvgId("N/A"))
        assertEquals("bbc1.uk", ChannelNaming.usableTvgId(" bbc1.uk "))
    }
}
