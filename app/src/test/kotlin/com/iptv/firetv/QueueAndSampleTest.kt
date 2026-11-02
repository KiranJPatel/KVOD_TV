package com.iptv.firetv

import com.iptv.firetv.core.parser.XmltvItem
import com.iptv.firetv.core.parser.XmltvParser
import com.iptv.firetv.core.parser.XmltvStats
import com.iptv.firetv.data.SampleData
import com.iptv.firetv.feature.player.ChannelQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParserFactory
import java.time.ZoneId

class QueueAndSampleTest {
    @Test fun channelQueueWrapsAroundAndHandlesUnknownIds() {
        val q = ChannelQueue(); assertNull(q.neighbor("a", 1))
        q.set(listOf("a", "b", "c"))
        assertEquals("b", q.neighbor("a", 1)); assertEquals("a", q.neighbor("c", 1)); assertEquals("c", q.neighbor("a", -1))
        assertEquals("a", q.neighbor("zzz", 1)) // channel not in the list: start from the first
    }

    @Test fun sampleGuideIsValidXmltvCoveringNow() {
        val now = System.currentTimeMillis()
        val st = XmltvStats()
        val items = XmltvParser { XmlPullParserFactory.newInstance().newPullParser() }.parse(SampleData.xmltv(now).byteInputStream(), ZoneId.systemDefault(), st).toList()
        assertEquals(null, st.error); assertEquals(6, st.channels); assertEquals(0, st.skipped)
        val progs = items.filterIsInstance<XmltvItem.Prog>().map { it.programme }
        for (ch in listOf("sample.one", "sample.sports", "sample.music")) assertTrue("$ch has a programme on air", progs.any { it.channelId == ch && it.startMs <= now && it.endMs > now })
        assertTrue("sample data must stay clearly synthetic", progs.all { (it.desc ?: "").contains("Synthetic") })
    }
}

class ShuffleQueueTest {
    private fun queue(n: Int) = ChannelQueue().also { q -> q.set((1..n).map { "c$it" }) }

    @Test fun randomPickNeverReturnsTheCurrentChannelAndStaysInTheList() {
        val q = queue(12); val rnd = kotlin.random.Random(7)
        repeat(300) { val cur = "c${it % 12 + 1}"; val p = q.randomPick(cur, rnd)!!; assertNotEquals(cur, p); assertTrue(p in q.ids) }
    }

    @Test fun randomPickAvoidsTheLastFewChannelsWhileTheListIsLongEnough() {
        val q = queue(30); val rnd = kotlin.random.Random(3)
        val picks = ArrayList<String>(); var cur: String? = null
        repeat(8) { val p = q.randomPick(cur, rnd)!!; picks += p; cur = p }
        assertEquals("no repeats inside the recent window", picks.size, picks.toSet().size)
    }

    @Test fun smallAndEmptyListsAreHandled() {
        assertNull(ChannelQueue().randomPick(null))
        assertEquals("c1", queue(1).randomPick("c1")); assertEquals("c2", queue(2).randomPick("c1", kotlin.random.Random(1)))
    }

    @Test fun nextFollowsListOrderUnlessShuffleIsOn() {
        val q = queue(5)
        assertEquals("c2", q.next("c1", 1)); assertEquals("c5", q.next("c1", -1))
        q.setShuffle(true); assertTrue(q.shuffle.value)
        repeat(20) { assertNotEquals("c3", q.next("c3", 1)) }
        q.setShuffle(false); assertEquals("c4", q.next("c3", 1))
    }

    @Test fun settingANewListClearsRandomHistory() {
        val q = queue(10); val rnd = kotlin.random.Random(5)
        val first = q.randomPick(null, rnd)!!
        q.set(q.ids)   // same list, history cleared: the previous pick is allowed again
        val seen = (1..200).map { q.randomPick("zz", rnd) }.toSet()
        assertTrue(first in seen)
    }
}
