package com.iptv.firetv

import com.iptv.firetv.core.common.Redactor
import com.iptv.firetv.core.common.TextUtil
import com.iptv.firetv.core.database.ChannelEntity
import com.iptv.firetv.core.database.EpgChannelEntity
import com.iptv.firetv.core.epg.EpgMatcher
import com.iptv.firetv.core.model.ContentRestrictions
import com.iptv.firetv.core.network.BoundedInputStream
import com.iptv.firetv.core.network.UrlCheck
import com.iptv.firetv.core.network.UrlValidator
import com.iptv.firetv.core.network.XtreamClient
import com.iptv.firetv.core.network.XtreamConfig
import com.iptv.firetv.core.network.Downloader
import com.iptv.firetv.core.player.PlaybackErrorMapper
import com.iptv.firetv.core.player.RetryPolicy
import com.iptv.firetv.core.security.Pin
import com.iptv.firetv.core.security.PinThrottle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private fun ch(id: String, name: String, tvg: String? = null) = ChannelEntity(id, 1, name, TextUtil.normalize(name), "G", "g", null, null, tvg, "https://x/1", null, false, 0, 0)

class CoreTest {
    @Test fun normalizeKeepsIndicVowelSigns() {
        assertEquals("aajtak", TextUtil.normalize("Aaj-Tak HD!".replace("HD", "")))
        assertTrue(TextUtil.normalize("आज तक").length >= 4)
        assertEquals(TextUtil.normalize("Café"), TextUtil.normalize("cafe"))
    }

    @Test fun adultDetectionHasNoSubstringFalsePositives() {
        assertTrue(TextUtil.looksAdult("XXX Channel", null)); assertTrue(TextUtil.looksAdult("Night", "Adult 18+"))
        assertFalse(TextUtil.looksAdult("Essex News", "Local")); assertFalse(TextUtil.looksAdult("Sussex Sports", null))
    }

    @Test fun redactorRemovesCredentialsAndPaths() {
        val r = Redactor.url("http://user:secret@host.test:8080/live/u/p123/1.m3u8?token=abc")
        assertFalse(r.contains("secret")); assertFalse(r.contains("p123")); assertFalse(r.contains("abc")); assertTrue(r.startsWith("http://host.test:8080"))
        assertFalse(Redactor.text("failed for https://h.test/get.php?username=a&password=b ok").contains("password"))
    }

    @Test fun urlValidation() {
        assertTrue(UrlValidator.validate("https://a.test/p.m3u") is UrlCheck.Ok)
        assertTrue((UrlValidator.validate("http://a.test/p.m3u") as UrlCheck.Ok).insecure)
        assertTrue(UrlValidator.validate("a.test/list") .let { it is UrlCheck.Ok && it.url.scheme == "https" })
        for (bad in listOf("", "ftp://a.test/x", "file:///etc/passwd", "javascript:alert(1)", "http://", "has space.test")) assertTrue(bad, UrlValidator.validate(bad) is UrlCheck.Invalid)
    }

    @Test fun boundedStreamStopsOversizedReads() {
        try { BoundedInputStream(ByteArray(1000).inputStream(), 100).readBytes(); fail() } catch (e: com.iptv.firetv.core.common.AppException) { assertEquals("E_SIZE", e.diagnostic) }
    }

    @Test fun xtreamUrlBuilder() {
        val c = XtreamClient(Downloader())
        assertEquals("https://h.test:8080", c.normalizeBase("https://h.test:8080/player_api.php?x=1"))
        val cfg = XtreamConfig("https://h.test", "us er", "p@ss", "ts")
        assertEquals("https://h.test/live/us%20er/p%40ss/9.ts", c.streamUrl(cfg, "xtream:live:9"))
        assertEquals("https://h.test/movie/us%20er/p%40ss/5.mkv", c.streamUrl(cfg, "xtream:movie:5:mkv"))
        assertTrue(c.epgUrl(cfg).startsWith("https://h.test/xmltv.php?"))
    }

    @Test fun epgMatchingPrecedence() {
        val epg = listOf(EpgChannelEntity(1, "bbc.one", "BBC One\nBBC 1", null), EpgChannelEntity(1, "dup.a", "Dup", null), EpgChannelEntity(1, "dup.b", "Dup", null), EpgChannelEntity(1, "user.x", "Whatever", null))
        val chans = listOf(ch("1", "Anything", "bbc.one"), ch("2", "BBC One"), ch("3", "Dup"), ch("4", "Mapped"), ch("5", "Unrelated Name"))
        val off = EpgMatcher.match(chans, epg, mapOf("4" to "user.x"), nameMatching = false)
        assertEquals("bbc.one", off["1"]); assertNull(off["2"]); assertEquals("user.x", off["4"]); assertNull(off["5"])
        val on = EpgMatcher.match(chans, epg, emptyMap(), nameMatching = true)
        assertEquals("bbc.one", on["2"]); assertNull("ambiguous XMLTV names must not map", on["3"]); assertNull(on["5"])
        assertEquals("user.x", EpgMatcher.match(listOf(ch("9", "X", "bbc.one")), epg, mapOf("9" to "user.x"), true)["9"]) // user mapping beats tvg-id
    }

    @Test fun parentalRestrictions() {
        val r = ContentRestrictions(hideAdult = true, blockedGroups = setOf("horror"))
        assertFalse(r.allows(true, "news")); assertFalse(r.allows(false, "horror")); assertTrue(r.allows(false, "news"))
        assertTrue(ContentRestrictions().allows(true, "x"))
    }

    @Test fun pinHashingAndThrottle() {
        val h = Pin.hash("1234", "salt"); assertTrue(Pin.matches("1234", "salt", h)); assertFalse(Pin.matches("1235", "salt", h)); assertFalse(Pin.matches("1234", null, h))
        assertTrue(Pin.isValidFormat("123456")); assertFalse(Pin.isValidFormat("12a4")); assertFalse(Pin.isValidFormat("123"))
        var now = 0L; val t = PinThrottle { now }
        repeat(5) { t.onFailure() }; assertTrue(t.remainingLockSeconds() > 0); now += 31_000; assertEquals(0, t.remainingLockSeconds())
    }

    @Test fun retryPolicyIsBoundedExponential() {
        val p = RetryPolicy(3); assertEquals(1000L, p.delayFor(0)); assertEquals(2000L, p.delayFor(1)); assertEquals(4000L, p.delayFor(2)); assertNull(p.delayFor(3))
        assertEquals(8000L, RetryPolicy(10).delayFor(6))
    }

    @Test fun playbackErrorMapping() {
        assertFalse(PlaybackErrorMapper.map(2004, 401).recoverable); assertFalse(PlaybackErrorMapper.map(2004, 404).recoverable)
        assertTrue(PlaybackErrorMapper.map(2004, 503).recoverable); assertTrue(PlaybackErrorMapper.map(2001, null).recoverable)
        assertEquals("P_FORMAT", PlaybackErrorMapper.map(3003, null).code); assertEquals("P_DECODE", PlaybackErrorMapper.map(4001, null).code); assertEquals("P_DRM", PlaybackErrorMapper.map(6001, null).code)
        assertFalse(PlaybackErrorMapper.map(3003, null).diagnostic.contains("http"))
    }
}

class RetryControllerTest {
    private val transient = com.iptv.firetv.core.player.PlaybackErrorMapper.map(2001, null)
    private val auth = com.iptv.firetv.core.player.PlaybackErrorMapper.map(2004, 401)

    @Test fun transientErrorsRetryWithBackoffThenGiveUp() {
        val c = com.iptv.firetv.core.player.RetryController(RetryPolicy(3))
        val d = (1..4).map { c.onError(transient) }
        assertEquals(com.iptv.firetv.core.player.RetryDecision.Retry(1000, 1), d[0])
        assertEquals(com.iptv.firetv.core.player.RetryDecision.Retry(2000, 2), d[1])
        assertEquals(com.iptv.firetv.core.player.RetryDecision.Retry(4000, 3), d[2])
        assertEquals(com.iptv.firetv.core.player.RetryDecision.GiveUp, d[3])   // never an infinite loop
    }

    @Test fun permanentErrorsNeverRetry() {
        val c = com.iptv.firetv.core.player.RetryController(RetryPolicy(3))
        assertEquals(com.iptv.firetv.core.player.RetryDecision.GiveUp, c.onError(auth)); assertEquals(0, c.attempts)
    }

    @Test fun successfulStartRestoresTheRetryBudget() {
        val c = com.iptv.firetv.core.player.RetryController(RetryPolicy(2))
        c.onError(transient); c.onError(transient); assertEquals(com.iptv.firetv.core.player.RetryDecision.GiveUp, c.onError(transient))
        c.reset(); assertEquals(com.iptv.firetv.core.player.RetryDecision.Retry(1000, 1), c.onError(transient))
    }

    @Test fun zeroRetriesMeansImmediateFailure() { assertEquals(com.iptv.firetv.core.player.RetryDecision.GiveUp, com.iptv.firetv.core.player.RetryController(RetryPolicy(0)).onError(transient)) }
}

class DiagnosticsLogTest {
    @Test fun logIsRedactedBoundedAndPersistedAcrossAttach() {
        val f = java.io.File.createTempFile("diag", ".log")
        com.iptv.firetv.core.common.DiagnosticsLog.clear(); com.iptv.firetv.core.common.DiagnosticsLog.attach(f)
        com.iptv.firetv.core.common.DiagnosticsLog.add("net", "failed https://user:pw@host.test/get.php?username=a&password=secret")
        val onDisk = f.readText()
        assertTrue(onDisk.contains("host.test")); assertFalse(onDisk.contains("secret")); assertFalse(onDisk.contains("pw@"))
        repeat(400) { com.iptv.firetv.core.common.DiagnosticsLog.add("x", "line $it") }
        assertEquals(300, com.iptv.firetv.core.common.DiagnosticsLog.snapshot().size)
        assertTrue(com.iptv.firetv.core.common.DiagnosticsLog.snapshot().first().contains("line 399"))   // newest first
        assertTrue(f.readLines().size >= 300)
        com.iptv.firetv.core.common.DiagnosticsLog.clear(); assertFalse(f.exists())
    }
}
