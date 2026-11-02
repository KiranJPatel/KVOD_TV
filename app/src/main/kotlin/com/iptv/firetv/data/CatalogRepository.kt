package com.iptv.firetv.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.iptv.firetv.core.common.ChannelNaming
import com.iptv.firetv.core.common.TextUtil
import com.iptv.firetv.core.database.AppDatabase
import com.iptv.firetv.core.database.ChannelEntity
import com.iptv.firetv.core.database.EpisodeEntity
import com.iptv.firetv.core.database.GroupCount
import com.iptv.firetv.core.database.SeriesEntity
import com.iptv.firetv.core.database.VodItemEntity
import com.iptv.firetv.core.model.ContentRestrictions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class SearchResults(
    val channels: List<ChannelEntity> = emptyList(),
    val movies: List<VodItemEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val programs: List<ProgramHit> = emptyList()
) {
    val isEmpty get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty() && programs.isEmpty()
}

/**
 * Every read goes through the active profile restrictions. Lookups by id return null for restricted items, so
 * restricted content is unreachable via search, direct navigation or Continue Watching.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CatalogRepository @Inject constructor(
    private val db: AppDatabase,
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
    private val epg: EpgRepository
) {
    private fun norm(s: String) = TextUtil.normalize(s)

    fun channelGroups(): Flow<List<GroupCount>> =
        combine(profiles.restrictions, settings.settings) { r, s -> r to s.activeSourceId }
            .flatMapLatest { (r, src) -> db.channels().groups(r.hideAdult, r.blockedGroups.toList(), src) }

    fun channels(groupNorm: String?, query: String, limit: Int = 5000): Flow<List<ChannelEntity>> =
        combine(profiles.restrictions, settings.settings) { r, s -> r to s }.flatMapLatest { (r, s) ->
            db.channels().observe(groupNorm, norm(query), r.hideAdult, r.blockedGroups.toList(), s.activeSourceId, s.channelSort, limit)
                .map { list -> hideDuplicates(list) }
        }

    /** Duplicate copies are never listed: of each group of copies only the best one stays, list order is kept. */
    private fun hideDuplicates(list: List<ChannelEntity>): List<ChannelEntity> {
        fun key(c: ChannelEntity) = c.dupKey.ifEmpty { c.id }
        val best = HashMap<String, ChannelEntity>()
        for (c in list) { val cur = best[key(c)]; if (cur == null || c.qualityRank > cur.qualityRank) best[key(c)] = c }
        return list.filter { best[key(it)]?.id == it.id }
    }

    /**
     * Other copies of [c] (same channel from another source or in another quality), best first, already limited by the profile
     * restrictions. The player uses them as fallback streams; they are never shown in lists.
     */
    suspend fun alternatesOf(c: ChannelEntity, limit: Int = 4): List<ChannelEntity> {
        val r = restrictions()
        return db.channels().alternatesOf(c.id, c.dupKey, ChannelNaming.usableTvgId(c.tvgId), r.hideAdult, r.blockedGroups.toList(), limit)
    }

    /** Several channels in one query, in the order of [ids]; restricted ones are dropped. */
    suspend fun channelsByIds(ids: List<String>): List<ChannelEntity> {
        if (ids.isEmpty()) return emptyList()
        val r = restrictions()
        val byId = ids.chunked(400).flatMap { db.channels().byIds(it) }.associateBy { it.id }
        return ids.mapNotNull { byId[it] }.filter { r.allows(it.adult, it.groupNorm) }
    }

    private fun pager() = PagingConfig(pageSize = 60, prefetchDistance = 30, enablePlaceholders = false)

    /** Paged live channels: only the rows near the viewport are loaded, so very large playlists stay responsive. */
    fun pagedChannels(groupNorm: String?, query: String): Flow<PagingData<ChannelEntity>> =
        combine(profiles.restrictions, settings.settings) { r, s -> r to s }.flatMapLatest { (r, s) ->
            Pager(pager()) { db.channels().paged(groupNorm, norm(query), r.hideAdult, r.blockedGroups.toList(), s.activeSourceId, s.channelSort, true) }.flow
        }

    suspend fun channelIds(groupNorm: String?, query: String): List<String> {
        val r = restrictions(); val s = settings.current()
        return db.channels().ids(groupNorm, norm(query), r.hideAdult, r.blockedGroups.toList(), s.activeSourceId, s.channelSort, true)
    }

    fun pagedMovies(groupNorm: String?, query: String, sort: Int): Flow<PagingData<VodItemEntity>> =
        combine(profiles.restrictions, settings.settings) { r, s -> r to s.activeSourceId }.flatMapLatest { (r, src) ->
            Pager(pager()) { db.vod().pagedMovies(groupNorm, norm(query), r.hideAdult, r.blockedGroups.toList(), src, sort) }.flow
        }

    fun pagedSeries(groupNorm: String?, query: String, sort: Int): Flow<PagingData<SeriesEntity>> =
        combine(profiles.restrictions, settings.settings) { r, s -> r to s.activeSourceId }.flatMapLatest { (r, src) ->
            Pager(pager()) { db.vod().pagedSeries(groupNorm, norm(query), r.hideAdult, r.blockedGroups.toList(), src, sort) }.flow
        }

    suspend fun restrictions(): ContentRestrictions = profiles.restrictions.first()

    suspend fun channel(id: String): ChannelEntity? = db.channels().byId(id)?.takeIf { restrictions().allows(it.adult, it.groupNorm) }
    suspend fun movie(id: String): VodItemEntity? = db.vod().movieById(id)?.takeIf { restrictions().allows(it.adult, it.groupNorm) }
    suspend fun series(id: String): SeriesEntity? = db.vod().seriesById(id)?.takeIf { restrictions().allows(it.adult, it.groupNorm) }
    suspend fun episode(id: String): Pair<EpisodeEntity, SeriesEntity>? {
        val e = db.vod().episodeById(id) ?: return null
        val s = series(e.seriesId) ?: return null
        return e to s
    }

    suspend fun nextEpisode(e: EpisodeEntity): EpisodeEntity? = db.vod().nextEpisode(e.seriesId, e.season, e.episode)
    fun episodes(seriesId: String): Flow<List<EpisodeEntity>> = db.vod().observeEpisodes(seriesId)
    fun movieGroups(): Flow<List<GroupCount>> = profiles.restrictions.flatMapLatest { db.vod().movieGroups(it.hideAdult, it.blockedGroups.toList()) }
    fun seriesGroups(): Flow<List<GroupCount>> = profiles.restrictions.flatMapLatest { db.vod().seriesGroups(it.hideAdult, it.blockedGroups.toList()) }

    /** sort: 0 = recently added, 1 = name. */
    fun movies(groupNorm: String?, query: String, sort: Int, limit: Int = 3000): Flow<List<VodItemEntity>> =
        combine(profiles.restrictions, settings.settings) { r, s -> r to s.activeSourceId }.flatMapLatest { (r, src) ->
            db.vod().observeMovies(groupNorm, norm(query), r.hideAdult, r.blockedGroups.toList(), src, sort, limit)
        }

    fun series(groupNorm: String?, query: String, sort: Int, limit: Int = 3000): Flow<List<SeriesEntity>> =
        combine(profiles.restrictions, settings.settings) { r, s -> r to s.activeSourceId }.flatMapLatest { (r, src) ->
            db.vod().observeSeries(groupNorm, norm(query), r.hideAdult, r.blockedGroups.toList(), src, sort, limit)
        }

    suspend fun search(query: String, sourceFilter: Long, types: Set<String>): SearchResults {
        val q = norm(query)
        if (q.length < 2) return SearchResults()
        val r = restrictions()
        val blocked = r.blockedGroups.toList()
        val ch = if ("LIVE" in types) hideDuplicates(db.vod().searchChannels(q, r.hideAdult, blocked, 60).filter { sourceFilter < 0 || it.sourceId == sourceFilter }) else emptyList()
        val mv = if ("MOVIE" in types) db.vod().searchMovies(q, r.hideAdult, blocked, 60).filter { sourceFilter < 0 || it.sourceId == sourceFilter } else emptyList()
        val se = if ("SERIES" in types) db.vod().searchSeries(q, r.hideAdult, blocked, 60).filter { sourceFilter < 0 || it.sourceId == sourceFilter } else emptyList()
        val pr = if ("EPG" in types) epg.searchPrograms(q, r) else emptyList()
        return SearchResults(ch, mv, se, pr)
    }
}
