package com.iptv.firetv.core.model

enum class SourceType(val label: String) {
    M3U_URL("M3U playlist URL"),
    M3U_FILE("M3U playlist file"),
    XTREAM("Xtream-compatible API"),
    XMLTV_URL("XMLTV guide URL"),
    XMLTV_FILE("XMLTV guide file"),
    LOCAL_MEDIA("Local media")
}

enum class ContentKind { LIVE, MOVIE, SERIES, EPISODE, UNKNOWN }

/**
 * Three colour themes, each available in a dark and a light variant (see [ThemeMode]).
 * Names stored by older builds (six earlier themes and the four before those) are mapped by [fromStored].
 */
enum class AppTheme(val label: String, val tagline: String) {
    AURORA("Aurora", "Cool indigo \u2022 Outfit"),
    SPACE_GRAY("Space Gray", "Neutral graphite \u2022 Instrument Sans"),
    STUDIO("Studio", "Calm teal \u2022 Lora");

    companion object {
        /** Maps a stored name (any name used by an earlier build) to a theme and, for names that implied a brightness, a mode. */
        fun fromStored(name: String?): Pair<AppTheme, ThemeMode?> = when (name) {
            null, "" -> AURORA to null
            "MIDNIGHT", "EMBER", "ORCHID" -> AURORA to null
            "FOREST", "OCEAN" -> STUDIO to null
            "GRAPHITE" -> SPACE_GRAY to null
            "MATERIAL_DARK", "MATERIAL_GRAY" -> SPACE_GRAY to ThemeMode.DARK
            "LIGHT" -> AURORA to ThemeMode.LIGHT
            else -> (AppTheme.entries.firstOrNull { it.name == name } ?: AURORA) to null
        }
    }
}

enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("Follow system") }

/** [THEME] keeps the colours of the chosen theme; the others override its primary colour. */
enum class AccentColor(val argb: Long, val label: String) {
    THEME(0x00000000, "Theme colour"),
    BLUE(0xFF1688FF, "Electric blue"),
    TEAL(0xFF14B8A6, "Teal"),
    AMBER(0xFFF5A524, "Amber"),
    VIOLET(0xFF8B7BFF, "Violet"),
    ROSE(0xFFF0658A, "Rose")
}

/**
 * Video quality policy. Heights carry a small tolerance (1088 for "1080p") because some encoders emit 1920x1088.
 * [lockHighest] disables adaptive down-switching: the best variant at or below the cap is chosen and kept.
 */
enum class QualityMode(val label: String, val maxHeight: Int, val lockHighest: Boolean) {
    LOCK_1080("Best up to 1080p (locked)", 1088, true),
    AUTO_1080("Auto (adaptive, up to 1080p)", 1088, false),
    P720("720p", 736, true),
    P480("480p / SD", 576, true),
    /** No height cap: adaptive up to the best variant this device's decoders report as supported (4K on a 4K stick). */
    BEST("Best available (adaptive, up to what the device decodes)", Int.MAX_VALUE, false)
}

/**
 * [startMs]/[rebufferMs]: how much data is needed before playback (re)starts. [startTimeoutMs]: how long a channel may stay
 * without a picture before a duplicate (backup) stream is tried; [stallTimeoutMs]: same for a stall in the middle of playback.
 */
enum class BufferProfile(val label: String, val minMs: Int, val maxMs: Int, val startMs: Int, val rebufferMs: Int, val startTimeoutMs: Long, val stallTimeoutMs: Long) {
    LOW("Fast start", 8_000, 25_000, 750, 1_500, 7_000, 8_000),
    BALANCED("Balanced", 15_000, 40_000, 1_000, 2_500, 9_000, 10_000),
    HIGH("Stable (large buffer)", 30_000, 80_000, 2_000, 4_000, 12_000, 14_000)
}

/** One playlist entry as produced by the M3U parser (before it is mapped to a database row). */
data class ParsedEntry(
    val name: String,
    val url: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val logo: String? = null,
    val group: String? = null,
    val channelNumber: Int? = null,
    val language: String? = null,
    val country: String? = null,
    val kind: ContentKind = ContentKind.UNKNOWN,
    val httpHeaders: Map<String, String> = emptyMap(),
    val extras: Map<String, String> = emptyMap()
)

data class ParseIssue(val line: Int, val message: String)

data class ParseStats(
    var lines: Int = 0,
    var entries: Int = 0,
    var live: Int = 0,
    var movies: Int = 0,
    var series: Int = 0,
    var unknown: Int = 0,
    var invalid: Int = 0,
    var hasHeader: Boolean = false,
    val groups: MutableSet<String> = linkedSetOf(),
    val epgUrls: MutableList<String> = mutableListOf()
)

/** What the active profile is allowed to see. Enforced in repositories and SQL, not just in the UI. */
data class ContentRestrictions(
    val hideAdult: Boolean = false,
    val blockedGroups: Set<String> = emptySet() // normalised group names
) {
    fun allows(adult: Boolean, groupNorm: String): Boolean = !(hideAdult && adult) && groupNorm !in blockedGroups
}

data class NowNext(val now: ProgramInfo?, val next: ProgramInfo?)
data class ProgramInfo(val title: String, val startMs: Long, val endMs: Long, val description: String? = null) {
    fun progress(nowMs: Long): Float =
        if (endMs <= startMs) 0f else ((nowMs - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f)
}

/**
 * Widevine licence address supplied by the user's own playlist (`#KODIPROP` license_key). It travels with the item's
 * stored request headers under a reserved pseudo-header name and is split off again before any HTTP request is made.
 * Only officially supported Media3 DRM is used; there is no key extraction or bypass.
 */
object DrmHeaders {
    const val KEY = "drm.license"
    fun split(headers: Map<String, String>): Pair<Map<String, String>, String?> {
        val lic = headers[KEY]?.trim()?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
        return headers.filterKeys { it != KEY } to lic
    }
}
