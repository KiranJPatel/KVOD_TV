package com.iptv.firetv.core.common

/**
 * Identity of a live channel across duplicate entries (HD/FHD/SD copies, backup feeds, the same channel listed by several
 * sources). Only quality and codec tags are removed; anything that makes a channel genuinely different (a language, a
 * region such as "East"/"West", a number) stays in the key, so "Star Sports 1" and "Star Sports 1 Hindi" never merge.
 */
object ChannelNaming {
    private const val EDGE_BEFORE = "(?<![a-z0-9])"
    private const val EDGE_AFTER = "(?![a-z0-9])"

    private val noiseTags = Regex(
        "(?i)$EDGE_BEFORE(?:uhd|4k|2160[pi]?|fhd|full\\s*hd|1080[pi]?|hd|720[pi]?|sd|576[pi]?|480[pi]?|hevc|h\\.?26[45]|avc|\\d{2}\\s*fps|backup)$EDGE_AFTER"
    )
    private val uhdTag = Regex("(?i)$EDGE_BEFORE(?:uhd|4k|2160[pi]?)$EDGE_AFTER")
    private val fhdTag = Regex("(?i)$EDGE_BEFORE(?:fhd|full\\s*hd|1080[pi]?)$EDGE_AFTER")
    private val hdTag = Regex("(?i)$EDGE_BEFORE(?:hd|720[pi]?)$EDGE_AFTER")
    private val sdTag = Regex("(?i)$EDGE_BEFORE(?:sd|576[pi]?|480[pi]?)$EDGE_AFTER")

    /** Never empty, so rows are never left in the "not yet computed" state (empty string). */
    fun dupKey(name: String, id: String): String {
        val cleaned = TextUtil.normalize(noiseTags.replace(name, " "))
        return cleaned.ifEmpty { TextUtil.normalize(name).ifEmpty { "#$id" } }
    }

    /**
     * Which copy is shown first (higher wins). Tuned for a 1080p panel: Full HD, then HD, then untagged, then 4K (heavy for a
     * 1080p TV and a weak stick), then SD. Ties are broken by source order and playlist position.
     */
    fun qualityRank(name: String): Int = when {
        uhdTag.containsMatchIn(name) -> 2
        fhdTag.containsMatchIn(name) -> 5
        hdTag.containsMatchIn(name) -> 4
        sdTag.containsMatchIn(name) -> 1
        else -> 3
    }

    private val genericTvgIds = setOf("", "none", "null", "n/a", "na", "0", "-", "unknown")

    /** The tvg-id when it identifies a channel; empty when it is missing or a placeholder some playlists use. */
    fun usableTvgId(tvgId: String?): String = tvgId?.trim()?.takeUnless { it.lowercase() in genericTvgIds }.orEmpty()
}
