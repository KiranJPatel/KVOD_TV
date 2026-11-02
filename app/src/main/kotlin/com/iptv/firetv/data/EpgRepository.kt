package com.iptv.firetv.data

import androidx.room.withTransaction
import com.iptv.firetv.core.common.DiagnosticsLog
import com.iptv.firetv.core.common.TextUtil
import com.iptv.firetv.core.database.AppDatabase
import com.iptv.firetv.core.database.ChannelEntity
import com.iptv.firetv.core.database.EpgChannelEntity
import com.iptv.firetv.core.database.ProgramEntity
import com.iptv.firetv.core.epg.EpgMatcher
import com.iptv.firetv.core.model.ContentRestrictions
import com.iptv.firetv.core.model.NowNext
import com.iptv.firetv.core.model.ProgramInfo
import com.iptv.firetv.core.parser.XmltvItem
import com.iptv.firetv.core.parser.XmltvParser
import com.iptv.firetv.core.parser.XmltvStats
import com.iptv.firetv.core.parser.shifted
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class ProgramHit(val program: ProgramEntity, val channel: ChannelEntity)

@Singleton
class EpgRepository @Inject constructor(private val db: AppDatabase, private val settings: SettingsRepository) {
    private val parser = XmltvParser { Xml.newPullParser() }
    @Volatile private var epgIndex: List<EpgChannelEntity>? = null

    private suspend fun index(): List<EpgChannelEntity> = epgIndex ?: db.epg().epgChannels().also { epgIndex = it }

    suspend fun zone(): ZoneId {
        val z = settings.current().epgZone
        return if (z.isBlank()) ZoneId.systemDefault() else runCatching { ZoneId.of(z) }.getOrDefault(ZoneId.systemDefault())
    }

    /** channelId -> XMLTV channel id for the given channels. */
    suspend fun idMap(channels: List<ChannelEntity>): Map<String, String> {
        val s = settings.current()
        val maps = db.epg().mappings().associate { it.channelId to it.epgChannelId }
        return EpgMatcher.match(channels, index(), maps, s.epgNameMatching)
    }

    suspend fun nowNext(channels: List<ChannelEntity>, nowMs: Long = System.currentTimeMillis()): Map<String, NowNext> {
        if (channels.isEmpty()) return emptyMap()
        val map = idMap(channels)
        if (map.isEmpty()) return emptyMap()
        val progs = loadPrograms(map.values.distinct(), nowMs, nowMs + 8 * 3_600_000L)
        val out = HashMap<String, NowNext>()
        for ((chId, epgId) in map) {
            val list = progs[epgId] ?: continue
            val now = list.firstOrNull { it.startMs <= nowMs && it.endMs > nowMs }
            val next = list.firstOrNull { it.startMs >= (now?.endMs ?: nowMs) && it !== now }
            if (now != null || next != null) out[chId] = NowNext(now?.toInfo(), next?.toInfo())
        }
        return out
    }

    /** Programmes grouped by channel id for the given window (used by the guide grid). */
    suspend fun guide(channels: List<ChannelEntity>, from: Long, to: Long): Map<String, List<ProgramEntity>> {
        val map = idMap(channels)
        val progs = loadPrograms(map.values.distinct(), from, to)
        return map.mapNotNull { (chId, epgId) -> progs[epgId]?.let { chId to it } }.toMap()
    }

    private suspend fun loadPrograms(epgIds: List<String>, from: Long, to: Long): Map<String, List<ProgramEntity>> {
        val out = HashMap<String, MutableList<ProgramEntity>>()
        for (chunk in epgIds.chunked(400)) db.epg().programsFor(chunk, from, to).forEach { out.getOrPut(it.epgChannelId) { mutableListOf() }.add(it) }
        return out
    }

    suspend fun searchPrograms(q: String, r: ContentRestrictions): List<ProgramHit> {
        val hits = db.epg().searchPrograms(q, System.currentTimeMillis(), 40)
        if (hits.isEmpty()) return emptyList()
        val ids = hits.map { it.epgChannelId }.distinct()
        val byTvg = ids.chunked(400).flatMap { db.channels().byTvgIds(it) }.filter { r.allows(it.adult, it.groupNorm) }.groupBy { it.tvgId }
        val mapped = db.epg().mappings().associate { it.epgChannelId to it.channelId }
        return hits.mapNotNull { p ->
            val ch = byTvg[p.epgChannelId]?.firstOrNull() ?: mapped[p.epgChannelId]?.let { db.channels().byId(it) }?.takeIf { r.allows(it.adult, it.groupNorm) }
            ch?.let { ProgramHit(p, it) }
        }
    }

    suspend fun setMapping(channelId: String, epgId: String?) {
        if (epgId == null) db.epg().clearMapping(channelId) else db.epg().setMapping(com.iptv.firetv.core.database.EpgMappingEntity(channelId, epgId))
    }

    /** Imports an XMLTV file (plain or gzip) for [sourceId], keeping a window of yesterday..+8 days. Replaces that source's old data atomically. */
    suspend fun importXmltv(sourceId: Long, file: File, offsetMin: Int = 0, onProgress: (Int) -> Unit = {}): XmltvStats = withContext(Dispatchers.IO) {
        val zone = zone()
        val now = System.currentTimeMillis()
        val from = now - 24 * 3_600_000L
        val to = now + 8 * 24 * 3_600_000L
        val stats = XmltvStats()
        db.withTransaction {
            db.epg().deleteProgramsBySource(sourceId)
            db.epg().deleteEpgChannelsBySource(sourceId)
            val progBatch = ArrayList<ProgramEntity>(2000)
            val chBatch = ArrayList<EpgChannelEntity>(500)
            var kept = 0
            file.inputStream().buffered().use { ins ->
                for (item in parser.parse(ins, zone, stats) { true }) {
                    coroutineContext.ensureActive()
                    when (item) {
                        is XmltvItem.Chan -> chBatch.add(EpgChannelEntity(sourceId, item.channel.id, item.channel.names.joinToString("\n"), item.channel.icon))
                        is XmltvItem.Prog -> {
                            val p = item.programme.shifted(offsetMin * 60_000L)
                            if (p.endMs > from && p.startMs < to) {
                                progBatch.add(ProgramEntity(sourceId = sourceId, epgChannelId = p.channelId, startMs = p.startMs, endMs = p.endMs, title = p.title,
                                    titleNorm = TextUtil.normalize(p.title), subTitle = p.subTitle, description = p.desc, category = p.category,
                                    episodeNum = p.episodeNum, previouslyShown = p.previouslyShown))
                                kept++
                            }
                        }
                    }
                    if (progBatch.size >= 2000) { db.epg().insertPrograms(progBatch); progBatch.clear(); onProgress(kept) }
                    if (chBatch.size >= 500) { db.epg().upsertEpgChannels(chBatch); chBatch.clear() }
                }
            }
            if (progBatch.isNotEmpty()) db.epg().insertPrograms(progBatch)
            if (kept == 0 && stats.programmes == 0) throw com.iptv.firetv.core.common.AppException(com.iptv.firetv.core.common.ErrorKind.PLAYLIST, "No programmes were found in this XMLTV file" + (stats.error?.let { " (" + it + ")" } ?: "."))
            if (chBatch.isNotEmpty()) db.epg().upsertEpgChannels(chBatch)
            db.epg().pruneBefore(from)
            onProgress(kept)
        }
        epgIndex = null
        DiagnosticsLog.add("epg", "XMLTV import: ${stats.channels} channels, ${stats.programmes} programmes, ${stats.skipped} skipped" + (stats.error?.let { ", error: $it" } ?: ""))
        stats
    }

    /** XMLTV channels whose id or display name contains the (normalised) query, for the manual mapping dialog. */
    suspend fun searchEpgChannels(query: String, limit: Int = 50): List<EpgChannelEntity> {
        val q = TextUtil.normalize(query)
        val all = index()
        return (if (q.isEmpty()) all else all.filter { e -> TextUtil.normalize(e.epgId).contains(q) || e.displayNames.split("\n").any { TextUtil.normalize(it).contains(q) } }).take(limit)
    }
    suspend fun mappingOf(channelId: String): String? = db.epg().mappings().firstOrNull { it.channelId == channelId }?.epgChannelId
    suspend fun lastProgramEnd(): Long? = db.epg().lastProgramEnd()
    suspend fun prune() { db.epg().pruneBefore(System.currentTimeMillis() - 24 * 3_600_000L) }
    suspend fun clearIndexCache() { epgIndex = null }
}

private fun ProgramEntity.toInfo() = ProgramInfo(title, startMs, endMs, description)
