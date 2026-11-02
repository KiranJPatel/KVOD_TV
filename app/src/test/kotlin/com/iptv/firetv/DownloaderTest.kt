package com.iptv.firetv

import com.iptv.firetv.core.common.AppException
import com.iptv.firetv.core.common.ErrorKind
import com.iptv.firetv.core.network.Downloader
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DownloaderTest {
    private lateinit var server: MockWebServer
    private val dl = Downloader()
    private lateinit var out: File

    @Before fun setUp() { server = MockWebServer().also { it.start() }; out = File.createTempFile("downloader", ".m3u") }
    @After fun tearDown() { server.shutdown(); out.delete() }

    private fun kind(block: () -> Unit): ErrorKind? = try { block(); null } catch (e: AppException) { e.kind }

    @Test fun downloadsBody() = runBlocking {
        server.enqueue(MockResponse().setBody("#EXTM3U\n"))
        assertEquals(8L, dl.download(server.url("/p.m3u").toString(), out, retries = 0)); assertEquals("#EXTM3U\n", out.readText())
    }

    @Test fun followsValidatedRedirects() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/final"))
        server.enqueue(MockResponse().setBody("ok"))
        dl.download(server.url("/a").toString(), out, retries = 0); assertEquals("ok", out.readText())
    }

    @Test fun redirectLoopsAreLimited() { repeat(8) { server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/again")) }
        assertEquals(ErrorKind.NETWORK, kind { runBlocking { dl.download(server.url("/a").toString(), out, retries = 0) } }) }

    @Test fun mapsHttpStatuses() {
        for ((code, expected) in listOf(401 to ErrorKind.AUTH, 403 to ErrorKind.AUTH, 404 to ErrorKind.EXPIRED, 410 to ErrorKind.EXPIRED, 503 to ErrorKind.SERVER)) {
            server.enqueue(MockResponse().setResponseCode(code))
            assertEquals("HTTP $code", expected, kind { runBlocking { dl.download(server.url("/x").toString(), out, retries = 0) } })
        }
    }

    @Test fun retriesTransientFailuresThenSucceeds() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503)); server.enqueue(MockResponse().setBody("fine"))
        dl.download(server.url("/x").toString(), out, retries = 1); assertEquals("fine", out.readText()); assertEquals(2, server.requestCount)
    }

    @Test fun doesNotRetryAuthErrors() { server.enqueue(MockResponse().setResponseCode(401)); server.enqueue(MockResponse().setBody("x"))
        assertEquals(ErrorKind.AUTH, kind { runBlocking { dl.download(server.url("/x").toString(), out, retries = 3) } }); assertEquals(1, server.requestCount) }

    @Test fun enforcesSizeLimit() { server.enqueue(MockResponse().setBody("x".repeat(5000)))
        assertEquals(ErrorKind.TOO_LARGE, kind { runBlocking { dl.download(server.url("/x").toString(), out, maxBytes = 1000, retries = 0) } }) }

    @Test fun rejectsNonHttpSchemes() { assertEquals(ErrorKind.INPUT, kind { runBlocking { dl.download("file:///etc/passwd", out, retries = 0) } }); assertTrue(out.length() == 0L) }
}
