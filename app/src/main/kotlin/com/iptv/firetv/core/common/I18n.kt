package com.iptv.firetv.core.common

import android.content.Context

/**
 * Lightweight UI translation layer. Visible UI strings are written in English in the code and passed through [tr];
 * translations live in plain asset files `assets/i18n/<code>.properties` (one `English text=Translation` per line,
 * `\n`, `\=` and `\\` escaped). A missing translation always falls back to the English text, so partial files are safe
 * and a new language needs no code change — add a file and an entry in [languages].
 */
object I18n {
    @Volatile private var map: Map<String, String> = emptyMap()

    /** (display name, code). The empty code means "follow the system language". */
    val languages: List<Pair<String, String>> = listOf("System default" to "", "English" to "en", "\u0939\u093F\u0928\u094D\u0926\u0940 (Hindi)" to "hi")
    private val supported = setOf("en", "hi")

    fun tr(s: String): String = map[s] ?: s

    /** Picks the effective language: an explicit choice wins, otherwise the system language if supported, else English. */
    fun resolve(setting: String, systemLanguage: String): String = when {
        setting in supported -> setting
        systemLanguage in supported -> systemLanguage
        else -> "en"
    }

    fun load(ctx: Context, code: String) {
        map = if (code == "en" || code !in supported) emptyMap()
        else runCatching { ctx.assets.open("i18n/$code.properties").use { parse(it.readBytes().toString(Charsets.UTF_8)) } }.getOrDefault(emptyMap())
    }

    /** Parses `key=value` lines; `#` starts a comment; `\n`, `\=` and `\\` are the only escapes. Lines without a separator are ignored. */
    fun parse(text: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (raw in text.lines()) {
            val line = raw.trimEnd('\r')
            if (line.isBlank() || line.startsWith("#")) continue
            var i = 0; var sep = -1
            while (i < line.length) { when (line[i]) { '\\' -> i++; '=' -> { sep = i; break } }; i++ }
            if (sep <= 0) continue
            val k = unescape(line.substring(0, sep)); val v = unescape(line.substring(sep + 1))
            if (v.isNotEmpty()) out[k] = v
        }
        return out
    }

    fun escape(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("=", "\\=")
    private fun unescape(s: String): String {
        val sb = StringBuilder(); var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) { sb.append(when (val n = s[i + 1]) { 'n' -> '\n'; else -> n }); i += 2 } else { sb.append(c); i++ }
        }
        return sb.toString()
    }
}

/** Translate a UI string (falls back to the English text). */
fun tr(s: String): String = I18n.tr(s)
