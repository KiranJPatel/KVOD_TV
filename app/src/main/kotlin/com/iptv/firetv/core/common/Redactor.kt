package com.iptv.firetv.core.common

import android.util.Log
import java.util.ArrayDeque

/** Removes anything that could carry credentials (userinfo, path, query, fragment) from URLs before they are shown or logged. */
object Redactor {
    private val urlRegex = Regex("[a-zA-Z][a-zA-Z0-9+.-]*://[^\\s\"<>]+")
    private val hostRegex = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://(?:[^/@?#]*@)?([^/?#]+)")

    fun url(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val m = hostRegex.find(raw.trim()) ?: return "<redacted>"
        return "${m.groupValues[1]}://${m.groupValues[2]}/\u2026"
    }

    fun text(s: String): String = urlRegex.replace(s) { url(it.value) }
}

/**
 * Redacted diagnostics log: an in-memory ring buffer (newest first in [snapshot]) mirrored to a bounded app-private file so
 * playback and import problems survive a restart. Lines are redacted before they are stored; the file never holds secrets.
 */
object DiagnosticsLog {
    private val lines = ArrayDeque<String>()
    private const val MAX = 300
    private const val MAX_FILE_BYTES = 256 * 1024L
    private var file: java.io.File? = null

    @Synchronized fun attach(f: java.io.File) {
        file = f
        runCatching { if (f.exists()) f.readLines().takeLast(MAX).forEach { lines.addLast(it) } }
    }

    @Synchronized fun add(tag: String, message: String) {
        val line = "${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())} [$tag] ${Redactor.text(message)}".replace('\n', ' ')
        if (lines.size >= MAX) lines.removeFirst()
        lines.addLast(line)
        runCatching { Log.i("KVODTV/$tag", line) }
        file?.let { f -> runCatching {
            if (f.length() > MAX_FILE_BYTES) f.writeText(lines.joinToString("\n", postfix = "\n")) else f.appendText(line + "\n")
        } }
    }

    @Synchronized fun snapshot(): List<String> = lines.toList().asReversed()
    @Synchronized fun clear() { lines.clear(); file?.let { runCatching { it.delete() } } }
}

enum class ErrorKind(val code: String, val retryable: Boolean) {
    INPUT("E_INPUT", false), NETWORK("E_NET", true), AUTH("E_AUTH", false), EXPIRED("E_GONE", false),
    SERVER("E_SRV", true), TOO_LARGE("E_SIZE", false), PLAYLIST("E_PLAYLIST", false), STORAGE("E_STORAGE", false)
}

/** Every failure surfaced to the UI carries a user-facing message and a short diagnostic code; never secrets. */
class AppException(val kind: ErrorKind, val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause) {
    val diagnostic: String get() = kind.code
}
