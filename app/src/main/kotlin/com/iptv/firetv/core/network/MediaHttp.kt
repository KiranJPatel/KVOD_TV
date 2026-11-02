package com.iptv.firetv.core.network

import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * HTTP client for media (live streams, segments, VOD). It differs from the playlist/API client on purpose:
 *  - it follows redirects (many IPTV providers answer the stream URL with a 302 to an edge server; with redirects off, such
 *    channels failed with "the server refused the stream (302)"),
 *  - but never follows a redirect from https down to http,
 *  - it caches DNS answers so channel switching does not pay a lookup every time,
 *  - shorter connect timeout, so a dead provider host fails over quickly instead of after 15 seconds.
 */
object MediaHttp {
    fun client(base: OkHttpClient): OkHttpClient = base.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .addNetworkInterceptor(Interceptor { chain ->
            val first = chain.call().request().url
            val now = chain.request().url
            if (first.isHttps && !now.isHttps) throw IOException("Blocked a redirect from a secure to an insecure address.")
            chain.proceed(chain.request())
        })
        .dns(CachingDns())
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)   // live streams never "finish"
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(6, 5, TimeUnit.MINUTES))
        .build()
}

/** Remembers successful lookups for [ttlMs]; failures are never cached. Bounded to 128 hosts. */
class CachingDns(private val ttlMs: Long = 5 * 60_000L, private val delegate: Dns = Dns.SYSTEM, private val clock: () -> Long = System::currentTimeMillis) : Dns {
    private class Entry(val at: Long, val addresses: List<InetAddress>)
    private val cache = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > 128
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val now = clock()
        synchronized(cache) { cache[hostname]?.takeIf { now - it.at < ttlMs }?.let { return it.addresses } }
        val fresh = delegate.lookup(hostname)           // resolved outside the lock: a slow host never blocks other lookups
        synchronized(cache) { cache[hostname] = Entry(now, fresh) }
        return fresh
    }
}
