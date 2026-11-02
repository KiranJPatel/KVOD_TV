package com.iptv.firetv.data

import com.iptv.firetv.core.database.AppDatabase
import com.iptv.firetv.core.database.ChannelEntity
import com.iptv.firetv.core.database.FavoriteEntity
import com.iptv.firetv.core.database.PlaybackHistoryEntity
import com.iptv.firetv.core.database.RecentSearchEntity
import com.iptv.firetv.core.database.SeriesEntity
import com.iptv.firetv.core.database.VodItemEntity
import com.iptv.firetv.core.database.WatchProgressEntity
import com.iptv.firetv.core.model.ContentKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class ContinueItem(val progress: WatchProgressEntity, val route: String)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class UserDataRepository @Inject constructor(
    private val db: AppDatabase,
    private val profiles: ProfileRepository,
    private val catalog: CatalogRepository
) {
    private val activeId: Flow<Long> = profiles.activeProfile.filterNotNull().map { it.id }

    fun favoriteIds(kind: ContentKind): Flow<Set<String>> =
        activeId.flatMapLatest { db.user().favoriteIds(it, kind.name) }.map { it.toSet() }

    /** Returns the new favorite state. */
    suspend fun toggleFavorite(kind: ContentKind, itemId: String): Boolean {
        val p = profiles.activeId()
        return if (db.user().isFavorite(p, kind.name, itemId) > 0) {
            db.user().removeFavorite(p, kind.name, itemId); false
        } else {
            db.user().addFavorite(FavoriteEntity(p, kind.name, itemId, System.currentTimeMillis())); true
        }
    }

    fun favoriteChannels(): Flow<List<ChannelEntity>> = combine(activeId, profiles.restrictions) { id, r -> id to r }
        .flatMapLatest { (id, r) -> db.channels().favorites(id, r.hideAdult, r.blockedGroups.toList()) }
    fun favoriteMovies(): Flow<List<VodItemEntity>> = combine(activeId, profiles.restrictions) { id, r -> id to r }
        .flatMapLatest { (id, r) -> db.vod().favoriteMovies(id, r.hideAdult, r.blockedGroups.toList()) }
    fun favoriteSeries(): Flow<List<SeriesEntity>> = combine(activeId, profiles.restrictions) { id, r -> id to r }
        .flatMapLatest { (id, r) -> db.vod().favoriteSeries(id, r.hideAdult, r.blockedGroups.toList()) }

    suspend fun saveProgress(kind: ContentKind, itemId: String, title: String, poster: String?, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val done = positionMs >= durationMs * 0.95
        db.user().upsertProgress(
            WatchProgressEntity(profiles.activeId(), itemId, kind.name, title, poster, if (done) 0 else positionMs, durationMs, done, System.currentTimeMillis())
        )
    }

    suspend fun progress(itemId: String): WatchProgressEntity? = db.user().progress(profiles.activeId(), itemId)

    /** Restricted or removed items are dropped here, so they never appear in Continue Watching for the wrong profile. */
    fun continueWatching(limit: Int = 20): Flow<List<ContinueItem>> =
        activeId.flatMapLatest { db.user().continueRows(it, limit * 2) }.map { rows ->
            rows.mapNotNull { p ->
                when (p.kind) {
                    ContentKind.MOVIE.name -> catalog.movie(p.itemId)?.let { ContinueItem(p, "movie/${p.itemId}") }
                    ContentKind.EPISODE.name -> catalog.episode(p.itemId)?.let { ContinueItem(p, "player/episode/${p.itemId}") }
                    else -> null
                }
            }.take(limit)
        }

    suspend fun recordChannelWatched(id: String) {
        db.user().addHistory(PlaybackHistoryEntity(profileId = profiles.activeId(), itemId = id, kind = ContentKind.LIVE.name, watchedAt = System.currentTimeMillis()))
    }

    fun recentChannels(limit: Int = 20): Flow<List<ChannelEntity>> =
        activeId.flatMapLatest { db.user().recentChannelIds(it, limit) }.map { ids -> ids.mapNotNull { catalog.channel(it) } }

    fun recentSearches(): Flow<List<String>> = activeId.flatMapLatest { db.user().recentSearches(it) }
    suspend fun addSearch(q: String) {
        if (q.trim().length >= 2) db.user().addSearch(RecentSearchEntity(profiles.activeId(), q.trim(), System.currentTimeMillis()))
    }
    suspend fun clearSearches() = db.user().clearSearches(profiles.activeId())
}
