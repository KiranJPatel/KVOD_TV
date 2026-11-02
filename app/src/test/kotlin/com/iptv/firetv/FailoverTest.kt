package com.iptv.firetv

import com.iptv.firetv.core.model.BufferProfile
import com.iptv.firetv.core.network.CachingDns
import com.iptv.firetv.core.player.FailoverController
import com.iptv.firetv.core.player.StreamAlt
import com.iptv.firetv.core.player.StreamHealth
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class FailoverTest {
    @Test fun walksThroughCopiesThenStops() {
        val f = FailoverController(); f.begin(3)
        assertTrue(f.hasNext); assertEquals(1, f.advance()); assertEquals(2, f.advance())
        assertFalse(f.hasNext)
        f.rewind(); assertEquals(0, f.index); assertTrue(f.hasNext)
    }

    @Test fun singleStreamHasNoFallback() {
        val f = FailoverController(); f.begin(1)
        assertFalse(f.hasNext); assertEquals(1, f.total)
    }

    @Test fun alternatesArrivingLaterExtendTheList() {
        val f = FailoverController(); f.begin(1); f.extend(3)
        assertEquals(3, f.total); assertTrue(f.hasNext)
        f.extend(2); assertEquals(3, f.total)   // never shrinks
    }

    @Test fun recentlyFailedCopiesMoveToTheBackAndRecover() {
        var now = 0L
        val h = StreamHealth(clock = { now }, badForMs = 1_000)
        val a = StreamAlt("http://a/1"); val b = StreamAlt("http://b/1"); val c = StreamAlt("http://c/1")
        assertEquals(listOf(a, b, c), h.order(listOf(a, b, c)))
        h.markBad(a.url)
        assertEquals(listOf(b, c, a), h.order(listOf(a, b, c)))
        now = 1_001
        assertEquals(listOf(a, b, c), h.order(listOf(a, b, c)))
    }

    @Test fun bufferProfilesAreValidForLoadControl() {
        // DefaultLoadControl throws if playback thresholds exceed the minimum buffer or min exceeds max.
        for (p in BufferProfile.entries) {
            assertTrue(p.name, p.startMs <= p.minMs); assertTrue(p.name, p.rebufferMs <= p.minMs); assertTrue(p.name, p.minMs <= p.maxMs)
            assertTrue(p.name, p.startTimeoutMs > p.startMs)
        }
    }

    @Test fun dnsCacheAnswersFromMemoryUntilExpiry() {
        var now = 0L; var lookups = 0
        val upstream = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> { lookups++; return listOf(InetAddress.getByAddress(hostname, byteArrayOf(10, 0, 0, 1))) }
        }
        val dns = CachingDns(ttlMs = 1_000, delegate = upstream, clock = { now })
        dns.lookup("provider.test"); dns.lookup("provider.test")
        assertEquals(1, lookups)
        now = 1_500; dns.lookup("provider.test")
        assertEquals(2, lookups)
    }

    @Test fun dnsFailuresAreNotCached() {
        var lookups = 0
        val upstream = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> { lookups++; throw UnknownHostException("nope") }
        }
        val dns = CachingDns(delegate = upstream)
        repeat(2) { try { dns.lookup("dead.test") } catch (_: UnknownHostException) {} }
        assertEquals(2, lookups)
    }
}
