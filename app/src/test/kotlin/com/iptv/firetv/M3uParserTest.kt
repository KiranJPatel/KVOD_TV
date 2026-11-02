package com.iptv.firetv

import com.iptv.firetv.core.model.ContentKind
import com.iptv.firetv.core.parser.EntryClassifier
import com.iptv.firetv.core.parser.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    private fun parse(text: String, base: String? = null): Pair<List<com.iptv.firetv.core.model.ParsedEntry>, M3uParser.Session> {
        val s = M3uParser.Session()
        return M3uParser().parse(text.byteInputStream(Charsets.UTF_8), s, base).toList() to s
    }

    @Test fun emptyPlaylist() { val (e, s) = parse(""); assertTrue(e.isEmpty()); assertEquals(0, s.stats.entries) }

    @Test fun parsesAttributesAndCommasInsideQuotes() {
        val (e, s) = parse("#EXTM3U url-tvg=\"https://x.test/g.xml\"\n#EXTINF:-1 tvg-id=\"a.b\" tvg-name=\"A B\" tvg-logo=\"https://x.test/l.png\" tvg-chno=\"12\" group-title=\"News, Business\",Channel, One\nhttps://x.test/1.m3u8\n")
        assertEquals(1, e.size)
        with(e[0]) { assertEquals("Channel, One", name); assertEquals("a.b", tvgId); assertEquals("News, Business", group); assertEquals(12, channelNumber); assertEquals(ContentKind.LIVE, kind) }
        assertEquals(listOf("https://x.test/g.xml"), s.stats.epgUrls)
    }

    @Test fun handlesBomAndMixedLineEndings() {
        val (e, _) = parse("\uFEFF#EXTM3U\r\n#EXTINF:-1,One\r\nhttps://x.test/1.m3u8\r#EXTINF:-1,Two\nhttps://x.test/2.m3u8\n")
        assertEquals(listOf("One", "Two"), e.map { it.name })
    }

    @Test fun reportsMissingUrlWithoutDroppingValidEntries() {
        val (e, s) = parse("#EXTM3U\n#EXTINF:-1,No url\n#EXTINF:-1,Has url\nhttps://x.test/a.m3u8\n")
        assertEquals(1, e.size); assertEquals("Has url", e[0].name)
        assertTrue(s.issues.any { it.message.contains("without a stream URL") })
    }

    @Test fun rejectsUnsafeSchemes() {
        val (e, s) = parse("#EXTM3U\n#EXTINF:-1,Bad\nfile:///sdcard/a.m3u8\n#EXTINF:-1,Js\njavascript://alert(1)\n#EXTINF:-1,Good\nrtsp://x.test/live\n")
        assertEquals(listOf("Good"), e.map { it.name }); assertEquals(2, s.stats.invalid)
    }

    @Test fun unterminatedQuoteIsReportedAndTitleSalvaged() {
        val (e, s) = parse("#EXTM3U\n#EXTINF:-1 tvg-id=\"broken,Salvaged Title\nhttps://x.test/a.m3u8\n")
        assertEquals(1, e.size); assertEquals("Salvaged Title", e[0].name); assertTrue(s.issues.isNotEmpty())
    }

    @Test fun unicodeNamesAndRelativeUrls() {
        val (e, _) = parse("#EXTM3U\n#EXTINF:-1 group-title=\"हिन्दी\",आज तक\nstreams/aajtak.m3u8\n", base = "https://x.test/lists/p.m3u8")
        assertEquals("आज तक", e[0].name); assertEquals("हिन्दी", e[0].group); assertEquals("https://x.test/lists/streams/aajtak.m3u8", e[0].url)
    }

    @Test fun vlcOptionsAndExtGrp() {
        val (e, _) = parse("#EXTM3U\n#EXTGRP:Sports\n#EXTINF:-1,S\n#EXTVLCOPT:http-user-agent=TestUA\n#EXTVLCOPT:http-evil=1\nhttps://x.test/s.m3u8\n")
        assertEquals("Sports", e[0].group); assertEquals(mapOf("User-Agent" to "TestUA"), e[0].httpHeaders)
    }

    @Test fun missingHeaderIsWarnedButParsed() { val (e, s) = parse("#EXTINF:-1,A\nhttps://x.test/a.m3u8\n"); assertEquals(1, e.size); assertTrue(s.issues.any { it.message.contains("#EXTM3U") }) }

    @Test fun largePlaylistStreamsLazily() {
        val sb = StringBuilder("#EXTM3U\n"); repeat(60_000) { sb.append("#EXTINF:-1 tvg-id=\"c$it\" group-title=\"G${it % 50}\",Channel $it\nhttps://x.test/$it.m3u8\n") }
        val s = M3uParser.Session(); val first = M3uParser().parse(sb.toString().byteInputStream(), s).first()
        assertEquals("Channel 0", first.name); assertTrue("lazy: parser must not read everything for first()", s.stats.entries < 5)
        val s2 = M3uParser.Session(); assertEquals(60_000, M3uParser().parse(sb.toString().byteInputStream(), s2).count()); assertEquals(50, s2.stats.groups.size)
    }

    @Test fun entryLimitStopsParsing() {
        val s = M3uParser.Session(); val n = M3uParser(maxEntries = 3).parse("#EXTM3U\n${(1..10).joinToString("") { "#EXTINF:-1,C$it\nhttps://x.test/$it.m3u8\n" }}".byteInputStream(), s).count()
        assertEquals(3, n)
    }

    @Test fun classification() {
        assertEquals(ContentKind.LIVE, EntryClassifier.classify("CNN", "News", "https://h/live/u/p/1.m3u8", null))
        assertEquals(ContentKind.MOVIE, EntryClassifier.classify("Film", "Movies", "https://h/movie/u/p/1.mkv", null))
        assertEquals(ContentKind.EPISODE, EntryClassifier.classify("Show S01E02", "Series", "https://h/a/b.mp4", null))
        assertEquals(ContentKind.LIVE, EntryClassifier.classify("Movie Channel", "Movies", "https://h/a.m3u8", "movies.tv")) // live movie channel stays live
        assertEquals(ContentKind.MOVIE, EntryClassifier.classify("Some Film (2020)", "Cinema", "https://h/a.mp4", null))
        assertNull(EntryClassifier.episodeInfo("No episode marker"))
        assertEquals(Triple("Show", 2, 5), EntryClassifier.episodeInfo("Show - S02E05"))
    }

    @Test fun widevineLicenceUrlFromKodiPropIsCapturedAndOtherDrmIsIgnoredWithAWarning() {
        val (e, s) = parse("#EXTM3U\n#EXTINF:-1,Protected\n#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha\n#KODIPROP:inputstream.adaptive.license_key=https://lic.test/w?x=1|Content-Type=application/octet-stream|R{SSM}|\nhttps://x.test/a.mpd\n" +
            "#EXTINF:-1,Other\n#KODIPROP:inputstream.adaptive.license_type=clearkey\n#KODIPROP:inputstream.adaptive.license_key=abc:def\nhttps://x.test/b.mpd\n")
        assertEquals("https://lic.test/w?x=1", e[0].httpHeaders[com.iptv.firetv.core.model.DrmHeaders.KEY])
        assertNull(e[1].httpHeaders[com.iptv.firetv.core.model.DrmHeaders.KEY]); assertTrue(s.issues.any { it.message.contains("Unsupported DRM") })
    }

    @Test fun drmPseudoHeaderIsSplitOffBeforeAnyRequestHeaders() {
        val (plain, lic) = com.iptv.firetv.core.model.DrmHeaders.split(mapOf("User-Agent" to "UA", com.iptv.firetv.core.model.DrmHeaders.KEY to "https://lic.test/w"))
        assertEquals(mapOf("User-Agent" to "UA"), plain); assertEquals("https://lic.test/w", lic)
        assertNull(com.iptv.firetv.core.model.DrmHeaders.split(mapOf(com.iptv.firetv.core.model.DrmHeaders.KEY to "javascript:alert(1)")).second)
    }
}
