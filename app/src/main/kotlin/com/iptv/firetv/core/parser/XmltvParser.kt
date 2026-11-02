package com.iptv.firetv.core.parser

import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream

data class XmltvChannel(val id: String, val names: List<String>, val icon: String?)
data class XmltvProgramme(
    val channelId: String, val startMs: Long, val endMs: Long, val title: String, val subTitle: String?,
    val desc: String?, val category: String?, val episodeNum: String?, val language: String?, val previouslyShown: Boolean
)
sealed interface XmltvItem {
    data class Chan(val channel: XmltvChannel) : XmltvItem
    data class Prog(val programme: XmltvProgramme) : XmltvItem
}
/** Applies a per-source guide offset (provider guides are sometimes shifted by a fixed number of minutes). */
fun XmltvProgramme.shifted(deltaMs: Long): XmltvProgramme = if (deltaMs == 0L) this else copy(startMs = startMs + deltaMs, endMs = endMs + deltaMs)

data class XmltvStats(var channels: Int = 0, var programmes: Int = 0, var skipped: Int = 0, var error: String? = null)

/**
 * XMLTV parser built on XmlPullParser (DOCTYPE/external entities are never processed). Time values with an explicit
 * offset are honoured; values without an offset are interpreted in [defaultZone] (documented in README).
 * Plain and gzip-compressed input are both accepted.
 */
class XmltvParser(private val newParser: () -> XmlPullParser) {

    /**
     * Lazily yields channels and programmes so callers can batch database writes without holding the whole feed in
     * memory. Truncated or malformed XML ends the sequence early and is reported in [stats].error.
     */
    fun parse(
        input: InputStream, defaultZone: ZoneId, stats: XmltvStats = XmltvStats(),
        isActive: () -> Boolean = { true }
    ): Sequence<XmltvItem> = sequence {
        val parser = newParser()
        runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
        runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false) }
        try {
            parser.setInput(maybeGunzip(input), null)
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT && isActive()) {
                if (ev == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> {
                            val c = readChannel(parser)
                            if (c != null) { stats.channels++; yield(XmltvItem.Chan(c)) }
                        }
                        "programme" -> {
                            val pr = readProgramme(parser, defaultZone)
                            if (pr == null) stats.skipped++ else { stats.programmes++; yield(XmltvItem.Prog(pr)) }
                        }
                    }
                }
                ev = parser.next()
            }
        } catch (e: Exception) {
            stats.error = e.message?.take(160) ?: e.javaClass.simpleName
        }
    }

    private fun maybeGunzip(input: InputStream): InputStream {
        val b = BufferedInputStream(input, 64 * 1024)
        b.mark(2)
        val b0 = b.read(); val b1 = b.read()
        b.reset()
        return if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(b, 64 * 1024) else b
    }

    private fun readChannel(p: XmlPullParser): XmltvChannel? {
        val id = p.getAttributeValue(null, "id")?.trim().orEmpty()
        val names = ArrayList<String>(); var icon: String? = null
        while (true) {
            when (p.next()) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "display-name" -> p.nextText().trim().takeIf { it.isNotEmpty() }?.let { names.add(it) }
                    "icon" -> if (icon == null) icon = p.getAttributeValue(null, "src")
                }
                XmlPullParser.END_TAG -> if (p.name == "channel") break
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return if (id.isEmpty()) null else XmltvChannel(id, names, icon)
    }

    private fun readProgramme(p: XmlPullParser, zone: ZoneId): XmltvProgramme? {
        val channel = p.getAttributeValue(null, "channel")?.trim().orEmpty()
        val start = parseTime(p.getAttributeValue(null, "start"), zone)
        val stop = parseTime(p.getAttributeValue(null, "stop"), zone)
        var title: String? = null; var sub: String? = null; var desc: String? = null; var cat: String? = null
        var ep: String? = null; var lang: String? = null; var prev = false
        while (true) {
            when (p.next()) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "title" -> if (title == null) title = p.nextText().trim()
                    "sub-title" -> if (sub == null) sub = p.nextText().trim()
                    "desc" -> if (desc == null) desc = p.nextText().trim()
                    "category" -> if (cat == null) cat = p.nextText().trim()
                    "episode-num" -> if (ep == null) ep = p.nextText().trim()
                    "language" -> if (lang == null) lang = p.nextText().trim()
                    "previously-shown" -> prev = true
                }
                XmlPullParser.END_TAG -> if (p.name == "programme") break
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        if (channel.isEmpty() || start == null || title.isNullOrBlank()) return null
        // Some feeds omit stop; fall back to 30 minutes so the entry stays usable.
        val end = if (stop != null && stop > start) stop else start + 30 * 60_000L
        return XmltvProgramme(channel, start, end, title!!, sub?.ifEmpty { null }, desc?.ifEmpty { null }, cat?.ifEmpty { null }, ep?.ifEmpty { null }, lang?.ifEmpty { null }, prev)
    }

    companion object {
        private val timeRegex = Regex("^(\\d{4})(\\d{2})(\\d{2})(\\d{2})?(\\d{2})?(\\d{2})?\\s*(Z|UTC|GMT|[+-]\\d{2}:?\\d{2})?$", RegexOption.IGNORE_CASE)

        /** Parses `YYYYMMDDhhmmss +hhmm`. Missing time parts default to 0; missing offset uses [zone]. Returns null if invalid. */
        fun parseTime(raw: String?, zone: ZoneId): Long? {
            val m = timeRegex.find(raw?.trim().orEmpty()) ?: return null
            val g = m.groupValues
            return try {
                val ldt = LocalDateTime.of(
                    g[1].toInt(), g[2].toInt(), g[3].toInt(),
                    g[4].ifEmpty { "0" }.toInt(), g[5].ifEmpty { "0" }.toInt(), g[6].ifEmpty { "0" }.toInt()
                )
                val off = g[7]
                when {
                    off.isEmpty() -> ldt.atZone(zone).toInstant().toEpochMilli()
                    off.equals("Z", true) || off.equals("UTC", true) || off.equals("GMT", true) -> ldt.toInstant(ZoneOffset.UTC).toEpochMilli()
                    else -> {
                        val sign = if (off[0] == '-') -1 else 1
                        val digits = off.substring(1).replace(":", "")
                        val secs = sign * (digits.substring(0, 2).toInt() * 3600 + digits.substring(2).toInt() * 60)
                        ldt.toInstant(ZoneOffset.ofTotalSeconds(secs)).toEpochMilli()
                    }
                }
            } catch (e: Exception) { null }
        }
    }
}
