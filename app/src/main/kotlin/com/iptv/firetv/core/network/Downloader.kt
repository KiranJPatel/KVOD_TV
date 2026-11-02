package com.iptv.firetv.core.network

import com.iptv.firetv.core.common.AppException
import com.iptv.firetv.core.common.DiagnosticsLog
import com.iptv.firetv.core.common.ErrorKind
import com.iptv.firetv.core.common.Redactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Request
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Caps the number of bytes that can be read; protects against oversized or endless responses. */
class BoundedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var count = 0L
    private fun check(n: Int) { if (n > 0) { count += n; if (count > limit) throw AppException(ErrorKind.TOO_LARGE, "The file is larger than the ${limit / (1024 * 1024)} MB limit.") } }
    override fun read(): Int = super.read().also { if (it >= 0) check(1) }
    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { check(it) }
}

@Singleton
class Downloader @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false) // redirects are validated by hand
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS)
        .build()

    /** Same transport settings, used by Xtream JSON calls and by Media3 data sources. */
    val httpClient: OkHttpClient get() = client

    /**
     * Streams [url] into [dest]. Validates scheme and every redirect hop (max 5, no https→http downgrade), bounds the
     * size, retries transient network failures with exponential backoff (max [retries]) and honours coroutine cancellation.
     */
    suspend fun download(
        url: String, dest: File, maxBytes: Long = 150L * 1024 * 1024, userAgent: String? = null,
        retries: Int = 2, onProgress: (Long) -> Unit = {}
    ): Long = withContext(Dispatchers.IO) {
        var last: AppException? = null
        for (attempt in 0..retries) {
            try { return@withContext downloadOnce(url, dest, maxBytes, userAgent, onProgress) }
            catch (e: AppException) {
                last = e
                if (!e.kind.retryable || attempt >= retries) throw e
            }
            kotlinx.coroutines.delay(1000L shl attempt)
        }
        throw last ?: AppException(ErrorKind.NETWORK, "Download failed.")
    }

    private suspend fun downloadOnce(url: String, dest: File, maxBytes: Long, ua: String?, onProgress: (Long) -> Unit): Long {
        // Fail early with a clear message instead of a half-written file when the device is nearly full.
        dest.parentFile?.let { it.mkdirs(); if (it.usableSpace in 1 until 20L * 1024 * 1024) throw AppException(ErrorKind.STORAGE, "Not enough free storage to download this file. Free some space and try again.") }
        var current = (UrlValidator.validate(url) as? UrlCheck.Ok)?.url
            ?: throw AppException(ErrorKind.INPUT, "This is not a valid http or https address.")
        var hops = 0
        while (true) {
            coroutineContext.ensureActive()
            val req = Request.Builder().url(current).header("User-Agent", ua?.takeIf { it.isNotBlank() } ?: "IPTV-FireTV/0.1").build()
            val call = client.newCall(req)
            try {
                call.await().use { resp ->
                    when {
                        resp.isRedirect -> {
                            val loc = resp.header("Location") ?: throw AppException(ErrorKind.NETWORK, "The server sent an invalid redirect.")
                            val next = current.resolve(loc)?.takeIf { it.scheme == "http" || it.scheme == "https" }
                                ?: throw AppException(ErrorKind.INPUT, "The server redirected to an unsupported address.")
                            if (current.scheme == "https" && next.scheme == "http") throw AppException(ErrorKind.INPUT, "Blocked a redirect from a secure to an insecure address.")
                            if (++hops > 5) throw AppException(ErrorKind.NETWORK, "Too many redirects.")
                            current = next
                        }
                        resp.code == 401 || resp.code == 403 -> throw AppException(ErrorKind.AUTH, "The server rejected the request (${resp.code}). Check the link or credentials.")
                        resp.code == 404 || resp.code == 410 -> throw AppException(ErrorKind.EXPIRED, "The playlist was not found (${resp.code}). The link may have expired.")
                        resp.code >= 500 -> throw AppException(ErrorKind.SERVER, "The server had a problem (${resp.code}). Try again later.")
                        !resp.isSuccessful -> throw AppException(ErrorKind.NETWORK, "Unexpected server response (${resp.code}).")
                        else -> {
                            val body = resp.body ?: throw AppException(ErrorKind.NETWORK, "The server returned an empty response.")
                            if (body.contentLength() > maxBytes) throw AppException(ErrorKind.TOO_LARGE, "The file is larger than the ${maxBytes / (1024 * 1024)} MB limit.")
                            var total = 0L
                            BoundedInputStream(body.byteStream(), maxBytes).use { ins ->
                                dest.parentFile?.mkdirs()
                                dest.outputStream().buffered().use { out ->
                                    val buf = ByteArray(32 * 1024)
                                    while (true) {
                                        coroutineContext.ensureActive()
                                        val n = ins.read(buf); if (n < 0) break
                                        out.write(buf, 0, n); total += n; onProgress(total)
                                    }
                                }
                            }
                            DiagnosticsLog.add("net", "Downloaded $total bytes from ${Redactor.url(current.toString())}")
                            return total
                        }
                    }
                }
            } catch (e: AppException) { throw e
            } catch (e: UnknownHostException) { throw AppException(ErrorKind.NETWORK, "Cannot reach the server. Check the address and your network.", e)
            } catch (e: SocketTimeoutException) { throw AppException(ErrorKind.NETWORK, "The server took too long to respond.", e)
            } catch (e: SSLException) { throw AppException(ErrorKind.NETWORK, "A secure connection could not be established.", e)
            } catch (e: IOException) {
                if (!coroutineContext[kotlinx.coroutines.Job]!!.isActive) throw e
                if (e.message?.contains("ENOSPC") == true || e.message?.contains("No space", true) == true) throw AppException(ErrorKind.STORAGE, "The device ran out of storage while downloading.", e)
                throw AppException(ErrorKind.NETWORK, "Network error while downloading.", e)
            } finally { if (!coroutineContext[kotlinx.coroutines.Job]!!.isActive) call.cancel() }
        }
    }

    /** Small JSON/text GET used by the Xtream client (bounded to 64 MB, same redirect/auth handling). */
    suspend fun getText(url: String, userAgent: String? = null, maxBytes: Long = 64L * 1024 * 1024): String = withContext(Dispatchers.IO) {
        val tmp = File.createTempFile("get", ".tmp")
        try { download(url, tmp, maxBytes, userAgent); tmp.readText(Charsets.UTF_8) } finally { tmp.delete() }
    }
}

/**
 * Suspends until the response headers arrive and cancels the underlying call as soon as the coroutine is cancelled, so a
 * user pressing Cancel never waits for a stalled connection to time out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { cont.resume(response) { response.close() } }
    })
}
