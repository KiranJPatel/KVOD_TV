package com.iptv.firetv.core.epg

import com.iptv.firetv.core.common.TextUtil
import com.iptv.firetv.core.database.ChannelEntity
import com.iptv.firetv.core.database.EpgChannelEntity

/**
 * Maps playlist channels to XMLTV channel ids. Order of precedence:
 *  1. explicit user mapping,
 *  2. exact XMLTV channel id equal to the channel tvg-id,
 *  3. (optional) exact normalised-name equality, only when it is unambiguous on both sides.
 * There is deliberately no fuzzy or "similar name" matching.
 */
object EpgMatcher {
    fun match(
        channels: List<ChannelEntity>,
        epgChannels: List<EpgChannelEntity>,
        userMappings: Map<String, String>,
        nameMatching: Boolean
    ): Map<String, String> {
        if (epgChannels.isEmpty() && userMappings.isEmpty()) return emptyMap()
        val ids = HashSet<String>(epgChannels.size * 2).apply { epgChannels.forEach { add(it.epgId) } }
        val idsLower = HashMap<String, String>().apply { epgChannels.forEach { putIfAbsent(it.epgId.lowercase(), it.epgId) } }

        val epgByName: Map<String, List<String>> by lazy {
            val m = HashMap<String, MutableList<String>>()
            for (e in epgChannels) for (n in e.displayNames.split("\n")) {
                val k = TextUtil.normalize(n); if (k.isNotEmpty()) m.getOrPut(k) { mutableListOf() }.let { if (e.epgId !in it) it.add(e.epgId) }
            }
            m
        }
        val channelNameCount: Map<String, Int> by lazy { channels.groupingBy { it.nameNorm }.eachCount() }

        val out = HashMap<String, String>()
        for (c in channels) {
            val userEpg = userMappings[c.id]
            if (userEpg != null) {
                out[c.id] = userEpg
                continue
            }
            val tvg = c.tvgId
            if (!tvg.isNullOrBlank()) {
                if (tvg in ids) { out[c.id] = tvg; continue }
                val lowerEpg = idsLower[tvg.lowercase()]
                if (lowerEpg != null) {
                    out[c.id] = lowerEpg
                    continue
                }
            }
            if (nameMatching && c.nameNorm.isNotEmpty()) {
                val cands = epgByName[c.nameNorm]
                if (cands != null && cands.size == 1 && channelNameCount[c.nameNorm] == 1) out[c.id] = cands[0]
            }
        }
        return out
    }
}
