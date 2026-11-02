package com.iptv.firetv

import com.iptv.firetv.core.parser.XmltvItem
import com.iptv.firetv.core.parser.XmltvParser
import com.iptv.firetv.core.parser.XmltvStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.util.zip.GZIPOutputStream

class XmltvParserTest {
    private val parser = XmltvParser { XmlPullParserFactory.newInstance().newPullParser() }
    private val ist = ZoneId.of("Asia/Kolkata")

    private val xml = """<?xml version="1.0" encoding="UTF-8"?>
<tv><channel id="a.b"><display-name>Alpha</display-name><display-name>Alpha HD</display-name><icon src="https://x.test/a.png"/></channel>
<programme start="20260101120000 +0530" stop="20260101130000 +0530" channel="a.b"><title lang="en">News &amp; Views</title><sub-title>Ep</sub-title><desc>Desc</desc><category>News</category><episode-num>S1E2</episode-num><previously-shown/></programme>
<programme start="20260101130000" stop="20260101140000" channel="a.b"><title>No offset</title></programme>
<programme start="bad" stop="x" channel="a.b"><title>Broken</title></programme></tv>"""

    private fun run(s: String, zone: ZoneId = ist): Triple<List<XmltvItem.Chan>, List<XmltvItem.Prog>, XmltvStats> {
        val st = XmltvStats(); val items = parser.parse(s.byteInputStream(), zone, st).toList()
        return Triple(items.filterIsInstance<XmltvItem.Chan>(), items.filterIsInstance<XmltvItem.Prog>(), st)
    }

    @Test fun parsesChannelsAndProgrammes() {
        val (c, p, st) = run(xml)
        assertEquals(listOf("Alpha", "Alpha HD"), c[0].channel.names); assertEquals("https://x.test/a.png", c[0].channel.icon)
        assertEquals(2, p.size); assertEquals(1, st.skipped)
        with(p[0].programme) { assertEquals("News & Views", title); assertTrue(previouslyShown); assertEquals("S1E2", episodeNum); assertEquals(3_600_000L, endMs - startMs) }
    }

    @Test fun explicitOffsetIsHonouredAndMissingOffsetUsesFallbackZone() {
        val (_, p, _) = run(xml)
        val expectedExplicit = java.time.OffsetDateTime.parse("2026-01-01T12:00:00+05:30").toInstant().toEpochMilli()
        assertEquals(expectedExplicit, p[0].programme.startMs)
        assertEquals(expectedExplicit + 3_600_000L, p[1].programme.startMs)           // 13:00 IST == 12:00+05:30 + 1h
        val (_, pUtc, _) = run(xml, ZoneId.of("UTC"))
        assertEquals(java.time.Instant.parse("2026-01-01T13:00:00Z").toEpochMilli(), pUtc[1].programme.startMs)
    }

    @Test fun timeFormats() {
        assertNull(XmltvParser.parseTime("garbage", ist)); assertNull(XmltvParser.parseTime(null, ist))
        assertEquals(java.time.Instant.parse("2026-03-04T00:00:00Z").toEpochMilli(), XmltvParser.parseTime("20260304", ZoneId.of("UTC")))
        assertEquals(java.time.Instant.parse("2026-03-04T10:30:00Z").toEpochMilli(), XmltvParser.parseTime("202603041030 Z", ist))
        assertEquals(java.time.Instant.parse("2026-03-04T05:00:00Z").toEpochMilli(), XmltvParser.parseTime("20260304100000 +0500", ist))
        assertNotNull(XmltvParser.parseTime("20260304100000 -03:30", ist))
        assertNull(XmltvParser.parseTime("20261301000000", ist)) // month 13
    }

    @Test fun gzipInputIsDetected() {
        val bos = ByteArrayOutputStream(); GZIPOutputStream(bos).use { it.write(xml.toByteArray()) }
        val st = XmltvStats(); val n = parser.parse(bos.toByteArray().inputStream(), ist, st).count()
        assertEquals(3, n)
    }

    @Test fun truncatedXmlKeepsEarlierItemsAndReportsError() {
        val (c, p, st) = run(xml.substringBefore("<programme start=\"20260101130000\"") + "<programme start=\"2026")
        assertEquals(1, c.size); assertEquals(1, p.size); assertNotNull(st.error)
    }

    @Test fun doctypeEntitiesAreNotResolved() {
        val evil = "<?xml version=\"1.0\"?><!DOCTYPE tv [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><tv><channel id=\"x\"><display-name>&xxe;</display-name></channel></tv>"
        val (c, _, _) = run(evil)
        assertTrue(c.none { it.channel.names.any { n -> n.contains("root:") } })
    }
}
