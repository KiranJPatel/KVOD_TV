package com.iptv.firetv.core.network

import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Tiny one-shot web page so a phone can type a playlist address for the TV (QR-assisted entry; TVs rarely have cameras).
 *
 * Safety: binds to one private LAN address only (never 0.0.0.0), the page lives under an unguessable random token path,
 * accepts a single small form POST, validates the URL with [UrlValidator], and shuts down after the first valid submission,
 * after [ttlMs], or after 20 requests to wrong paths. Nothing is logged. The phone-to-TV hop is plain HTTP on the home
 * network, which the dialog states explicitly.
 */
class LanEntryServer(private val address: InetAddress, private val ttlMs: Long = 5 * 60_000L, private val clock: () -> Long = System::currentTimeMillis) {
    data class Submission(val url: String, val name: String)

    val token: String = run {
        val chars = "abcdefghijkmnpqrstuvwxyz23456789"; val r = SecureRandom()
        (1..10).map { chars[r.nextInt(chars.length)] }.joinToString("")
    }
    @Volatile private var server: ServerSocket? = null
    @Volatile private var badRequests = 0

    /** Starts listening and returns the address to show in the QR code. */
    fun start(onSubmit: (Submission) -> Unit): String {
        val ss = ServerSocket(0, 4, address)
        server = ss
        val deadline = clock() + ttlMs
        thread(isDaemon = true, name = "lan-entry") {
            try {
                ss.soTimeout = 1000
                while (!ss.isClosed && clock() < deadline && badRequests < 20) {
                    val s = try { ss.accept() } catch (e: SocketTimeoutException) { continue }
                    s.use { runCatching { handle(it, onSubmit) } }
                }
            } catch (_: Exception) { /* closed */ } finally { stop() }
        }
        return "http://${address.hostAddress}:${ss.localPort}/$token"
    }

    fun stop() { runCatching { server?.close() }; server = null }

    private fun handle(s: Socket, onSubmit: (Submission) -> Unit) {
        s.soTimeout = 5000
        val ins = BufferedInputStream(s.getInputStream(), 8192); val out = s.getOutputStream()
        val head = readHead(ins) ?: return respond(out, 400, page("Bad request", ""))
        val lines = head.split("\r\n")
        val req = lines[0].split(" ")
        val method = req.getOrElse(0) { "" }; val path = req.getOrElse(1) { "" }.substringBefore('?')
        if (path != "/$token") { badRequests++; return respond(out, 404, page("Not found", "")) }
        when (method) {
            "GET" -> respond(out, 200, form(null))
            "POST" -> {
                val len = lines.drop(1).firstOrNull { it.startsWith("content-length:", true) }?.substringAfter(':')?.trim()?.toIntOrNull() ?: -1
                if (len < 0 || len > 4096) return respond(out, 413, page("Too large", "<p>That request is too large.</p>"))
                val body = ByteArray(len).also { b -> var n = 0; while (n < len) { val r = ins.read(b, n, len - n); if (r < 0) break; n += r } }
                val fields = String(body, Charsets.UTF_8).split('&').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { URLDecoder.decode(it[0], "UTF-8") to URLDecoder.decode(it[1], "UTF-8") } }.toMap()
                when (val c = UrlValidator.validate(fields["url"].orEmpty())) {
                    is UrlCheck.Invalid -> respond(out, 400, form(c.reason))
                    is UrlCheck.Ok -> {
                        onSubmit(Submission(c.url.toString(), fields["name"].orEmpty().trim().take(60)))
                        respond(out, 200, page("Sent", "<p>Done. Look at your TV to continue.</p>"))
                        stop()
                    }
                }
            }
            else -> respond(out, 405, page("Not allowed", ""))
        }
    }

    private fun readHead(ins: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 8192) {
            val b = ins.read(); if (b < 0) return null
            sb.append(b.toChar())
            if (sb.length >= 4 && sb.endsWith("\r\n\r\n")) return sb.substring(0, sb.length - 4)
        }
        return null
    }

    private fun respond(out: OutputStream, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val text = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; 413 -> "Payload Too Large"; else -> "Error" }
        out.write("HTTP/1.1 $code $text\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        out.write(bytes); out.flush()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun page(title: String, inner: String) = "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>${esc(title)}</title>" +
        "<style>body{font-family:sans-serif;margin:24px;background:#050B14;color:#F5F8FC}input{width:100%;box-sizing:border-box;font-size:18px;padding:12px;margin:6px 0 16px;border-radius:8px;border:1px solid #24344A}" +
        "button{font-size:18px;padding:12px 24px;border:0;border-radius:24px;background:#1688FF;color:#fff}.e{color:#FF8A8A}</style></head><body><h2>${esc(title)}</h2>$inner</body></html>"
    private fun form(error: String?) = page("Send a playlist address to your TV",
        (if (error != null) "<p class=\"e\">${esc(error)}</p>" else "") +
        "<form method=\"post\"><label>Name (optional)</label><input name=\"name\" maxlength=\"60\"><label>Playlist address</label><input name=\"url\" type=\"url\" inputmode=\"url\" required placeholder=\"https://\u2026\"><button type=\"submit\">Send to TV</button></form>" +
        "<p style=\"color:#A7B4C6\">Only use content you are authorised to watch.</p>")

    companion object {
        /** The device's private IPv4 LAN address (Wi-Fi or Ethernet), or null when offline. */
        fun lanAddress(): InetAddress? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        }.getOrNull()
    }
}
