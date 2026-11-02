package com.iptv.firetv.core.player

/** One playable copy of a stream: its own address, headers and (optionally) Widevine licence server. */
data class StreamAlt(val url: String, val headers: Map<String, String> = emptyMap(), val drmLicenseUrl: String? = null, val label: String = "")

/**
 * Which copy of a channel is playing: index 0 is the one shown in the list, the rest are the duplicate entries used as
 * fallbacks. Pure state (no Android types), so the switching rules are unit-testable.
 */
class FailoverController {
    var index = 0; private set
    var total = 1; private set

    fun begin(count: Int) { total = count.coerceAtLeast(1); index = 0 }
    /** Alternates can arrive after playback started (they are looked up in the background). */
    fun extend(count: Int) { if (count > total) total = count }
    val hasNext: Boolean get() = index + 1 < total
    fun advance(): Int { check(hasNext) { "no further stream" }; return ++index }
    fun rewind() { index = 0 }
    fun jumpTo(i: Int) { index = i.coerceIn(0, total - 1) }
}

/**
 * Short-lived memory (this session only, nothing stored) of streams that just failed, so the next tune-in to the same channel
 * tries a copy that worked first instead of waiting for a dead one to time out again. Keys are hashes, never the URL itself.
 */
class StreamHealth(private val clock: () -> Long = System::currentTimeMillis, private val badForMs: Long = 10 * 60_000L, private val maxEntries: Int = 400) {
    private val badUntil = LinkedHashMap<Int, Long>()

    @Synchronized fun markBad(url: String) {
        val now = clock()
        badUntil.remove(url.hashCode())
        badUntil[url.hashCode()] = now + badForMs
        if (badUntil.size > maxEntries) badUntil.entries.removeAll { it.value <= now }
        while (badUntil.size > maxEntries) badUntil.remove(badUntil.keys.first())
    }

    @Synchronized fun isBad(url: String): Boolean = badUntil[url.hashCode()]?.let { it > clock() } == true

    /** Stable: copies that recently failed move to the back, everything else keeps its order. */
    fun order(candidates: List<StreamAlt>): List<StreamAlt> = candidates.sortedBy { if (isBad(it.url)) 1 else 0 }
}
