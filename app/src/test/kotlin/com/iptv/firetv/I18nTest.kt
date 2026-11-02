package com.iptv.firetv

import com.iptv.firetv.core.common.I18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class I18nTest {
    @Test fun parsesEscapesCommentsAndIgnoresBadLines() {
        val m = I18n.parse("# comment\nA\\=B=x\\ny\nplain=value\n\nnoseparator\nempty=\nback\\\\slash=ok\n")
        assertEquals("x\ny", m["A=B"]); assertEquals("value", m["plain"]); assertEquals("ok", m["back\\slash"])
        assertEquals(3, m.size)   // empty translations and separator-less lines are ignored
    }

    @Test fun escapeAndParseRoundTrip() {
        val key = "Line1\nA=B \\ end"
        assertEquals(mapOf(key to "t"), I18n.parse(I18n.escape(key) + "=t"))
    }

    @Test fun effectiveLanguageResolution() {
        assertEquals("hi", I18n.resolve("hi", "en")); assertEquals("hi", I18n.resolve("", "hi")); assertEquals("en", I18n.resolve("", "de")); assertEquals("en", I18n.resolve("xx", "fr"))
    }

    @Test fun missingTranslationsFallBackToEnglish() { assertEquals("Anything untranslated", I18n.tr("Anything untranslated")) }

    @Test fun shippedHindiFileIsValidAndHasNoStaleKeys() {
        val hi = I18n.parse(File("src/main/assets/i18n/hi.properties").readText(Charsets.UTF_8))
        val catalog = I18n.parse(File("../docs/i18n/catalog_en.properties").readText(Charsets.UTF_8).lines().joinToString("\n") { if (it.startsWith("#") || it.isBlank()) it else it + "x" }).keys
        assertTrue("Hindi file should cover most of the UI", hi.size > 180)
        val stale = hi.keys.filter { it !in catalog }
        assertTrue("keys not in catalog: $stale", stale.isEmpty())
        // placeholders and technical tokens must survive translation
        assertTrue(hi.getValue("Server address (e.g. https://host:port)").contains("https://host:port"))
    }
}
