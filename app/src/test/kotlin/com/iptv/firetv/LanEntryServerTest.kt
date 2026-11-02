package com.iptv.firetv

import com.iptv.firetv.core.network.LanEntryServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

class LanEntryServerTest {
    private val server = LanEntryServer(InetAddress.getLoopbackAddress())
    private val got = AtomicReference<LanEntryServer.Submission?>(null)
    @After fun tearDown() = server.stop()

    private fun call(url: String, method: String = "GET", body: String? = null): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 3000; c.readTimeout = 3000
        if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); c.outputStream.use { it.write(body.toByteArray()) } }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        return code to text
    }

    @Test fun servesTheFormOnlyUnderTheSecretToken() {
        val url = server.start { got.set(it) }
        assertTrue(url.endsWith("/" + server.token)); assertTrue(server.token.length >= 10)
        val (code, html) = call(url); assertEquals(200, code); assertTrue(html.contains("name=\"url\""))
        assertEquals(404, call(url.substringBeforeLast('/') + "/wrong").first)
        assertNull(got.get())
    }

    @Test fun validSubmissionIsDeliveredOnceAndTheServerShutsDown() {
        val url = server.start { got.set(it) }
        val (code, _) = call(url, "POST", "name=Home+list&url=https%3A%2F%2Fexample.test%2Fp.m3u")
        assertEquals(200, code)
        assertEquals("https://example.test/p.m3u", got.get()!!.url); assertEquals("Home list", got.get()!!.name)
        Thread.sleep(300)
        assertTrue("server must be closed after a successful submission", runCatching { call(url) }.isFailure)
    }

    @Test fun invalidAndUnsafeAddressesAreRejectedWithoutCallback() {
        val url = server.start { got.set(it) }
        for (bad in listOf("file%3A%2F%2F%2Fetc%2Fpasswd", "javascript%3Aalert(1)", "", "ftp%3A%2F%2Fx.test")) {
            val (code, html) = call(url, "POST", "url=$bad"); assertEquals(bad, 400, code); assertTrue(html.contains("class=\"e\""))
        }
        assertNull(got.get())
    }

    @Test fun oversizedBodiesAreRefused() {
        val url = server.start { got.set(it) }
        assertEquals(413, call(url, "POST", "url=" + "a".repeat(5000)).first); assertNull(got.get())
    }

    @Test fun echoedErrorsAreHtmlEscaped() {
        val url = server.start { got.set(it) }
        val (_, html) = call(url, "POST", "url=%3Cscript%3E")
        assertFalse(html.contains("<script>"))
    }
}

class SubtitleFilesTest {
    @org.junit.Test fun onlyRenderableFormatsAndContentUrisAreAccepted() {
        val f = com.iptv.firetv.core.player.SubtitleFiles
        org.junit.Assert.assertEquals("application/x-subrip", f.mimeFor("Movie.EN.srt")); org.junit.Assert.assertEquals("text/vtt", f.mimeFor("a.vtt"))
        org.junit.Assert.assertEquals("text/x-ssa", f.mimeFor("a.ass")); org.junit.Assert.assertEquals("text/x-ssa", f.mimeFor("a.ssa"))
        org.junit.Assert.assertNull(f.mimeFor("virus.exe")); org.junit.Assert.assertNull(f.mimeFor("noextension"))
        org.junit.Assert.assertTrue(f.isSafeUri("content://docs/1")); org.junit.Assert.assertFalse(f.isSafeUri("https://evil.test/a.srt")); org.junit.Assert.assertFalse(f.isSafeUri("file:///sdcard/a.srt"))
    }
}
