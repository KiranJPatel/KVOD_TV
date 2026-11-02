package com.iptv.firetv.core.parser

import com.iptv.firetv.core.model.ContentKind
import com.iptv.firetv.core.model.ParseIssue
import com.iptv.firetv.core.model.ParseStats
import com.iptv.firetv.core.model.ParsedEntry
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI

/**
 * Streaming M3U/M3U8 parser. Entries are produced lazily as a [Sequence] so very large playlists never sit in memory
 * and callers can batch database writes. Malformed entries are reported through [issues] and skipped; they never
 * abort the parse. Only http, https, rtsp and udp (multicast MPEG-TS) stream schemes are accepted.
 */
class M3uParser(private val maxEntries: Int = 500_000, private val maxLineLength: Int = 16_384) {

    class Session(val stats: ParseStats = ParseStats(), val issues: MutableList<ParseIssue> = mutableListOf(), val maxIssues: Int = 200) {
        fun issue(line: Int, msg: String) { stats.invalid++; if (issues.size < maxIssues) issues.add(ParseIssue(line, msg)) }
    }

    private val allowedSchemes = setOf("http", "https", "rtsp", "udp")

    fun parse(input: InputStream, session: Session = Session(), baseUrl: String? = null): Sequence<ParsedEntry> = sequence {
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8), 64 * 1024)
        var pending: Pending? = null
        var extGroup: String? = null
        var vlcHeaders = LinkedHashMap<String, String>()
        var drmType: String? = null; var drmKey: String? = null
        var lineNo = 0
        var replacementChars = 0

        while (true) {
            var raw = reader.readLine() ?: break
            lineNo++
            session.stats.lines = lineNo
            if (lineNo == 1 && raw.startsWith("\uFEFF")) raw = raw.substring(1)
            if (raw.length > maxLineLength) { session.issue(lineNo, "Line longer than $maxLineLength characters skipped"); continue }
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.indexOf('\uFFFD') >= 0) replacementChars++

            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    session.stats.hasHeader = true
                    val attrs = parseAttributes(line.removePrefix("#EXTM3U"), lineNo, session).first
                    (attrs["url-tvg"] ?: attrs["x-tvg-url"])?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                        ?.forEach { if (it !in session.stats.epgUrls && session.stats.epgUrls.size < 20) session.stats.epgUrls.add(it) }
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    if (pending != null) session.issue(pending.line, "#EXTINF without a stream URL")
                    pending = parseExtInf(line, lineNo, session)
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> extGroup = line.substringAfter(':').trim().ifEmpty { null }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val kv = line.substringAfter(':'); val k = kv.substringBefore('=').trim().lowercase(); val v = kv.substringAfter('=', "").trim()
                    // Only well-known, harmless request headers are honoured.
                    when (k) {
                        "http-user-agent" -> vlcHeaders["User-Agent"] = v
                        "http-referrer", "http-referer" -> vlcHeaders["Referer"] = v
                        "http-origin" -> vlcHeaders["Origin"] = v
                    }
                }
                line.startsWith("#KODIPROP:", ignoreCase = true) -> {
                    val kv = line.substringAfter(':'); val k = kv.substringBefore('=').trim().lowercase(); val v = kv.substringAfter('=', "").trim()
                    when (k) {
                        "inputstream.adaptive.license_type" -> drmType = v.lowercase()
                        "inputstream.adaptive.license_key" -> drmKey = v.substringBefore('|').trim()   // Kodi appends request options after '|'; only the URL is used
                    }
                }
                line.startsWith("#") -> { /* comment or unsupported directive (e.g. #KODIPROP): ignored */ }
                else -> {
                    val p = pending
                    val resolved = resolve(line, baseUrl)
                    val scheme = schemeOf(resolved)
                    if (scheme == null || scheme !in allowedSchemes) {
                        session.issue(lineNo, "Unsupported or unsafe stream URL scheme" + (scheme?.let { " ($it)" } ?: ""))
                    } else if (session.stats.entries >= maxEntries) {
                        session.issue(lineNo, "Entry limit of $maxEntries reached; remaining entries ignored")
                        break
                    } else {
                        val name = (p?.title?.takeIf { it.isNotBlank() } ?: p?.attrs?.get("tvg-name") ?: nameFromUrl(resolved))
                        if (p == null) session.issue(lineNo, "Stream URL without #EXTINF metadata (name derived from URL)")
                        val group = (p?.attrs?.get("group-title")?.takeIf { it.isNotBlank() } ?: extGroup)?.trim()
                        val tvgId = p?.attrs?.get("tvg-id")?.takeIf { it.isNotBlank() }
                        val drm = if (drmType?.contains("widevine") == true) drmKey?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) } else null
                        if (drmType != null && drm == null) session.issue(lineNo, "Unsupported DRM setup ignored (only Widevine with an http(s) licence URL is supported)")
                        val entry = ParsedEntry(
                            name = name.trim(), url = resolved, tvgId = tvgId, tvgName = p?.attrs?.get("tvg-name"),
                            logo = p?.attrs?.get("tvg-logo")?.takeIf { it.isNotBlank() }, group = group,
                            channelNumber = (p?.attrs?.get("tvg-chno") ?: p?.attrs?.get("channel-number"))?.trim()?.toIntOrNull(),
                            language = p?.attrs?.get("tvg-language"), country = p?.attrs?.get("tvg-country"),
                            kind = EntryClassifier.classify(name, group, resolved, tvgId),
                            httpHeaders = if (vlcHeaders.isEmpty() && drm == null) emptyMap() else HashMap(vlcHeaders).also { if (drm != null) it[com.iptv.firetv.core.model.DrmHeaders.KEY] = drm },
                            extras = p?.attrs?.filterKeys { it !in KNOWN } ?: emptyMap()
                        )
                        session.stats.entries++
                        group?.let { session.stats.groups.add(it) }
                        when (entry.kind) {
                            ContentKind.LIVE -> session.stats.live++
                            ContentKind.MOVIE -> session.stats.movies++
                            ContentKind.SERIES, ContentKind.EPISODE -> session.stats.series++
                            ContentKind.UNKNOWN -> session.stats.unknown++
                        }
                        yield(entry)
                    }
                    pending = null; extGroup = null; vlcHeaders = LinkedHashMap(); drmType = null; drmKey = null
                }
            }
        }
        pending?.let { session.issue(it.line, "#EXTINF at end of file without a stream URL") }
        if (replacementChars > 0) session.issue(0, "Invalid UTF-8 sequences found on $replacementChars line(s); affected names may be garbled")
        if (!session.stats.hasHeader && session.stats.entries > 0) session.issue(1, "Missing #EXTM3U header (parsed anyway)")
    }

    private class Pending(val line: Int, val attrs: Map<String, String>, val title: String?)

    private fun parseExtInf(line: String, lineNo: Int, session: Session): Pending {
        val body = line.substring(8)
        var i = 0
        while (i < body.length && !body[i].isWhitespace() && body[i] != ',') i++ // duration token
        val (attrs, title) = parseAttributes(body.substring(i), lineNo, session)
        return Pending(lineNo, attrs, title)
    }

    /** Parses `key="value" key2=value2,Title`. Commas inside quotes do not end the attribute list. */
    internal fun parseAttributes(s: String, lineNo: Int, session: Session): Pair<Map<String, String>, String?> {
        val attrs = LinkedHashMap<String, String>()
        var i = 0
        while (i < s.length) {
            while (i < s.length && s[i].isWhitespace()) i++
            if (i >= s.length) return attrs to null
            if (s[i] == ',') return attrs to s.substring(i + 1).trim()
            val ks = i
            while (i < s.length && s[i] != '=' && s[i] != ',' && !s[i].isWhitespace()) i++
            val key = s.substring(ks, i).lowercase()
            if (i >= s.length || s[i] != '=') continue // bare token without value
            i++ // skip =
            if (i < s.length && (s[i] == '"' || s[i] == '\'')) {
                val q = s[i++]; val sb = StringBuilder(); var closed = false
                while (i < s.length) {
                    val c = s[i]
                    if (c == '\\' && i + 1 < s.length && s[i + 1] == q) { sb.append(q); i += 2; continue }
                    if (c == q) { closed = true; i++; break }
                    sb.append(c); i++
                }
                if (!closed) { // unterminated quote: salvage the title after the last comma
                    session.issue(lineNo, "Malformed attribute '$key' (unterminated quote)")
                    val lc = s.lastIndexOf(',')
                    return attrs to (if (lc >= ks) s.substring(lc + 1).trim() else null)
                }
                attrs[key] = sb.toString()
            } else {
                val vs = i
                while (i < s.length && s[i] != ',' && !s[i].isWhitespace()) i++
                attrs[key] = s.substring(vs, i)
            }
        }
        return attrs to null
    }

    private fun resolve(url: String, base: String?): String {
        if (url.contains("://")) return url
        if (base == null) return url
        return runCatching { URI(base).resolve(url).toString() }.getOrDefault(url)
    }

    private fun schemeOf(url: String): String? {
        val idx = url.indexOf("://")
        if (idx <= 0) return null
        return url.substring(0, idx).lowercase().takeIf { s -> s.all { it.isLetterOrDigit() || it == '+' || it == '.' || it == '-' } }
    }

    private fun nameFromUrl(url: String): String =
        url.substringBefore('?').trimEnd('/').substringAfterLast('/').substringBeforeLast('.').ifBlank { "Unnamed" }

    companion object {
        private val KNOWN = setOf("tvg-id", "tvg-name", "tvg-logo", "group-title", "tvg-chno", "channel-number", "tvg-language", "tvg-country")
    }
}

/** Metadata-first classification. File extensions are only a hint and never decide on their own when better signals exist. */
object EntryClassifier {
    private val vodExt = setOf("mkv", "mp4", "avi", "mov", "wmv", "flv", "m4v", "webm", "mpg", "mpeg")
    private val episodeRegex = Regex("(?i)[Ss](\\d{1,2})\\s*[Ee](\\d{1,3})")
    private val seriesWords = Regex("(?i)\\b(series|tv shows?|serien|seriale|web ?series)\\b")
    private val movieWords = Regex("(?i)\\b(vod|movies?|films?|cinema)\\b")

    fun classify(name: String, group: String?, url: String, tvgId: String?): ContentKind {
        val path = url.substringBefore('?').lowercase()
        when {
            "/live/" in path -> return ContentKind.LIVE
            "/series/" in path -> return ContentKind.EPISODE
            "/movie/" in path -> return ContentKind.MOVIE
        }
        val ext = path.substringAfterLast('.', "").takeIf { it.length in 2..5 && !path.substringAfterLast('/').startsWith(".") }
        val isFile = ext != null && ext in vodExt
        val hasEpisode = episodeRegex.containsMatchIn(name)
        val g = group.orEmpty()
        if (isFile) return if (hasEpisode || seriesWords.containsMatchIn(g)) ContentKind.EPISODE else ContentKind.MOVIE
        if (hasEpisode && tvgId == null) return ContentKind.EPISODE
        if (tvgId == null && ext != "m3u8" && ext != "ts") {
            if (seriesWords.containsMatchIn(g)) return ContentKind.EPISODE
            if (movieWords.containsMatchIn(g) && ext != null) return ContentKind.MOVIE
        }
        if (ext == "m3u8" || ext == "ts" || ext == null || tvgId != null || url.startsWith("rtsp", true)) return ContentKind.LIVE
        return ContentKind.UNKNOWN
    }

    fun episodeInfo(name: String): Triple<String, Int, Int>? {
        val m = episodeRegex.find(name) ?: return null
        val title = name.substring(0, m.range.first).trim().trimEnd('-', '.', '_', ':', ' ')
        if (title.isBlank()) return null
        return Triple(title, m.groupValues[1].toInt(), m.groupValues[2].toInt())
    }
}
