package com.iptv.firetv.core.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Shared filter for the live-channel list: source/enabled, category, text, profile restrictions and duplicate hiding.
 * A channel is hidden when a better copy (higher qualityRank, then earlier source, then earlier position) of the same channel
 * exists in the same visible set: same dupKey, or the same non-empty tvg-id. With a category selected the comparison stays inside
 * that category, so a category never loses a channel because a copy lives elsewhere. Rows without a dupKey yet are never hidden.
 */
private const val CH_FILTER = """WHERE sourceId IN (SELECT id FROM sources WHERE enabled=1)
 AND (:group IS NULL OR groupNorm=:group)
 AND (:q = '' OR nameNorm LIKE '%'||:q||'%' OR groupNorm LIKE '%'||:q||'%')
 AND (:hideAdult=0 OR adult=0) AND groupNorm NOT IN (:blocked) AND (:sourceId<0 OR sourceId=:sourceId)
 AND (:merge=0 OR NOT EXISTS (SELECT 1 FROM channels c3 WHERE c3.id<>channels.id
   AND ((channels.dupKey<>'' AND c3.dupKey=channels.dupKey) OR (LOWER(COALESCE(channels.tvgId,'')) NOT IN ('','none','null','n/a','na','0','-','unknown') AND c3.tvgId=channels.tvgId))
   AND c3.sourceId IN (SELECT id FROM sources WHERE enabled=1) AND (:sourceId<0 OR c3.sourceId=:sourceId)
   AND (:group IS NULL OR c3.groupNorm=channels.groupNorm)
   AND (:hideAdult=0 OR c3.adult=0) AND c3.groupNorm NOT IN (:blocked)
   AND (c3.qualityRank>channels.qualityRank OR (c3.qualityRank=channels.qualityRank
        AND (c3.sourceId<channels.sourceId OR (c3.sourceId=channels.sourceId AND c3.sortIndex<channels.sortIndex))))))"""
private const val CH_ORDER = " ORDER BY CASE :sort WHEN 1 THEN nameNorm END, CASE :sort WHEN 2 THEN COALESCE(number,999999) END, sourceId, sortIndex"

data class GroupCount(val name: String, val norm: String, val count: Int)
data class IdTime(val id: String, val addedAt: Long)

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY sortOrder, id") fun observeAll(): Flow<List<SourceEntity>>
    @Query("SELECT * FROM sources ORDER BY sortOrder, id") suspend fun all(): List<SourceEntity>
    @Query("SELECT * FROM sources WHERE id=:id") suspend fun byId(id: Long): SourceEntity?
    @Query("SELECT * FROM sources WHERE type=:type LIMIT 1") suspend fun firstOfType(type: String): SourceEntity?
    @Query("SELECT COALESCE(MAX(sortOrder),0)+1 FROM sources") suspend fun nextOrder(): Int
    @Insert suspend fun insert(s: SourceEntity): Long
    @Update suspend fun update(s: SourceEntity)
    @Query("DELETE FROM sources WHERE id=:id") suspend fun delete(id: Long)
}

@Dao
interface ChannelDao {
    @Upsert suspend fun upsertAll(items: List<ChannelEntity>)
    @Query("DELETE FROM channels WHERE sourceId=:sourceId AND updatedAt<:stamp") suspend fun deleteStale(sourceId: Long, stamp: Long)
    @Query("DELETE FROM channels WHERE sourceId=:sourceId") suspend fun deleteBySource(sourceId: Long)
    @Query("SELECT COUNT(*) FROM channels WHERE sourceId=:sourceId") suspend fun countBySource(sourceId: Long): Int
    @Query("SELECT MIN(groupName) AS name, groupNorm AS norm, COUNT(*) AS count FROM channels WHERE sourceId=:s GROUP BY groupNorm") suspend fun groupsOfSource(s: Long): List<GroupCount>
    @Query("SELECT * FROM channels " + CH_FILTER + CH_ORDER)
    fun paged(group: String?, q: String, hideAdult: Boolean, blocked: List<String>, sourceId: Long, sort: Int, merge: Boolean): PagingSource<Int, ChannelEntity>

    /** Ids in list order, used for channel up/down while playing (bounded). */
    @Query("SELECT id FROM channels " + CH_FILTER + CH_ORDER + " LIMIT 20000")
    suspend fun ids(group: String?, q: String, hideAdult: Boolean, blocked: List<String>, sourceId: Long, sort: Int, merge: Boolean): List<String>

    @Query("SELECT c.* FROM channels c INNER JOIN sources s ON s.id=c.sourceId WHERE s.name=:src AND c.nameNorm=:n AND c.groupNorm=:g")
    suspend fun find(src: String, n: String, g: String): List<ChannelEntity>
    @Query("SELECT * FROM channels WHERE id=:id") suspend fun byId(id: String): ChannelEntity?
    @Query("SELECT * FROM channels WHERE id IN (:ids)") suspend fun byIds(ids: List<String>): List<ChannelEntity>
    @Query("SELECT * FROM channels WHERE tvgId IN (:tvgIds)") suspend fun byTvgIds(tvgIds: List<String>): List<ChannelEntity>

    /**
     * Other copies of the same channel (same dupKey or same non-empty tvg-id), best first. They are the fallback streams when
     * the copy that is shown does not play. Respects enabled sources and the profile restrictions.
     */
    @Query("""SELECT * FROM channels WHERE id<>:id AND sourceId IN (SELECT id FROM sources WHERE enabled=1)
        AND ((:dupKey<>'' AND dupKey=:dupKey) OR (:tvgId<>'' AND tvgId=:tvgId))
        AND (:hideAdult=0 OR adult=0) AND groupNorm NOT IN (:blocked)
        ORDER BY qualityRank DESC, sourceId, sortIndex LIMIT :limit""")
    suspend fun alternatesOf(id: String, dupKey: String, tvgId: String, hideAdult: Boolean, blocked: List<String>, limit: Int): List<ChannelEntity>

    /** One-time backfill after the v3 migration: rows that do not have a dupKey yet. */
    @Query("SELECT * FROM channels WHERE dupKey='' LIMIT :n") suspend fun needingKeys(n: Int): List<ChannelEntity>

    @Query("""SELECT * FROM channels WHERE sourceId IN (SELECT id FROM sources WHERE enabled=1)
        AND (:group IS NULL OR groupNorm=:group)
        AND (:q = '' OR nameNorm LIKE '%'||:q||'%' OR groupNorm LIKE '%'||:q||'%')
        AND (:hideAdult=0 OR adult=0) AND groupNorm NOT IN (:blocked) AND (:sourceId<0 OR sourceId=:sourceId)
        ORDER BY CASE :sort WHEN 1 THEN nameNorm END, CASE :sort WHEN 2 THEN COALESCE(number,999999) END, sourceId, sortIndex
        LIMIT :limit""")
    fun observe(group: String?, q: String, hideAdult: Boolean, blocked: List<String>, sourceId: Long, sort: Int, limit: Int): Flow<List<ChannelEntity>>

    @Query("""SELECT MIN(groupName) AS name, groupNorm AS norm, COUNT(*) AS count FROM channels
        WHERE sourceId IN (SELECT id FROM sources WHERE enabled=1) AND (:hideAdult=0 OR adult=0)
        AND groupNorm NOT IN (:blocked) AND (:sourceId<0 OR sourceId=:sourceId)
        AND NOT EXISTS (SELECT 1 FROM channels c3 WHERE c3.id<>channels.id AND c3.groupNorm=channels.groupNorm
          AND ((channels.dupKey<>'' AND c3.dupKey=channels.dupKey) OR (LOWER(COALESCE(channels.tvgId,'')) NOT IN ('','none','null','n/a','na','0','-','unknown') AND c3.tvgId=channels.tvgId))
          AND c3.sourceId IN (SELECT id FROM sources WHERE enabled=1) AND (:sourceId<0 OR c3.sourceId=:sourceId)
          AND (:hideAdult=0 OR c3.adult=0)
          AND (c3.qualityRank>channels.qualityRank OR (c3.qualityRank=channels.qualityRank
               AND (c3.sourceId<channels.sourceId OR (c3.sourceId=channels.sourceId AND c3.sortIndex<channels.sortIndex)))))
        GROUP BY groupNorm ORDER BY MIN(sortIndex)""")
    fun groups(hideAdult: Boolean, blocked: List<String>, sourceId: Long): Flow<List<GroupCount>>

    @Query("""SELECT c.* FROM channels c INNER JOIN favorites f ON f.itemId=c.id AND f.kind='LIVE'
        WHERE f.profileId=:profileId AND c.sourceId IN (SELECT id FROM sources WHERE enabled=1)
        AND (:hideAdult=0 OR c.adult=0) AND c.groupNorm NOT IN (:blocked) ORDER BY f.addedAt DESC""")
    fun favorites(profileId: Long, hideAdult: Boolean, blocked: List<String>): Flow<List<ChannelEntity>>
}

@Dao
interface VodDao {
    @Upsert suspend fun upsertMovies(items: List<VodItemEntity>)
    @Upsert suspend fun upsertSeries(items: List<SeriesEntity>)
    @Upsert suspend fun upsertEpisodes(items: List<EpisodeEntity>)
    @Query("DELETE FROM vod_items WHERE sourceId=:s AND updatedAt<:stamp") suspend fun deleteStaleMovies(s: Long, stamp: Long)
    @Query("DELETE FROM series WHERE sourceId=:s AND updatedAt<:stamp") suspend fun deleteStaleSeries(s: Long, stamp: Long)
    @Query("DELETE FROM episodes WHERE seriesId NOT IN (SELECT id FROM series)") suspend fun deleteOrphanEpisodes()
    @Query("DELETE FROM vod_items WHERE sourceId=:s") suspend fun deleteMoviesBySource(s: Long)
    @Query("DELETE FROM series WHERE sourceId=:s") suspend fun deleteSeriesBySource(s: Long)
    @Query("SELECT COUNT(*) FROM vod_items WHERE sourceId=:s") suspend fun countMovies(s: Long): Int
    @Query("SELECT COUNT(*) FROM series WHERE sourceId=:s") suspend fun countSeries(s: Long): Int

    @Query("SELECT id, addedAt FROM vod_items WHERE id IN (:ids)") suspend fun movieTimes(ids: List<String>): List<IdTime>
    @Query("SELECT id, addedAt FROM series WHERE id IN (:ids)") suspend fun seriesTimes(ids: List<String>): List<IdTime>
    @Query("SELECT MIN(groupName) AS name, groupNorm AS norm, COUNT(*) AS count FROM vod_items WHERE sourceId=:s GROUP BY groupNorm") suspend fun movieGroupsOfSource(s: Long): List<GroupCount>
    @Query("SELECT MIN(groupName) AS name, groupNorm AS norm, COUNT(*) AS count FROM series WHERE sourceId=:s GROUP BY groupNorm") suspend fun seriesGroupsOfSource(s: Long): List<GroupCount>
    @Query("SELECT * FROM vod_items WHERE id IN (:ids)") suspend fun moviesByIds(ids: List<String>): List<VodItemEntity>
    @Query("SELECT * FROM series WHERE id IN (:ids)") suspend fun seriesByIds(ids: List<String>): List<SeriesEntity>
    @Query("SELECT * FROM episodes WHERE id IN (:ids)") suspend fun episodesByIds(ids: List<String>): List<EpisodeEntity>
    @Query("SELECT v.* FROM vod_items v INNER JOIN sources s ON s.id=v.sourceId WHERE s.name=:src AND v.titleNorm=:n AND v.groupNorm=:g") suspend fun findMovies(src: String, n: String, g: String): List<VodItemEntity>
    @Query("SELECT v.* FROM series v INNER JOIN sources s ON s.id=v.sourceId WHERE s.name=:src AND v.titleNorm=:n AND v.groupNorm=:g") suspend fun findSeries(src: String, n: String, g: String): List<SeriesEntity>
    @Query("SELECT * FROM episodes WHERE seriesId=:seriesId AND season=:season AND episode=:ep LIMIT 1") suspend fun episodeAt(seriesId: String, season: Int, ep: Int): EpisodeEntity?
    @Query("SELECT * FROM vod_items WHERE id=:id") suspend fun movieById(id: String): VodItemEntity?
    @Query("SELECT * FROM series WHERE id=:id") suspend fun seriesById(id: String): SeriesEntity?
    @Query("SELECT * FROM episodes WHERE id=:id") suspend fun episodeById(id: String): EpisodeEntity?
    @Query("SELECT * FROM episodes WHERE seriesId=:seriesId ORDER BY season, episode") fun observeEpisodes(seriesId: String): Flow<List<EpisodeEntity>>
    @Query("SELECT COUNT(*) FROM episodes WHERE seriesId=:seriesId") suspend fun episodeCount(seriesId: String): Int
    @Query("""SELECT * FROM episodes WHERE seriesId=:seriesId AND (season>:season OR (season=:season AND episode>:ep))
        ORDER BY season, episode LIMIT 1""")
    suspend fun nextEpisode(seriesId: String, season: Int, ep: Int): EpisodeEntity?

    @Query("""SELECT * FROM vod_items WHERE sourceId IN (SELECT id FROM sources WHERE enabled=1)
        AND (:group IS NULL OR groupNorm=:group) AND (:q = '' OR titleNorm LIKE '%'||:q||'%' OR groupNorm LIKE '%'||:q||'%')
        AND (:hideAdult=0 OR adult=0) AND groupNorm NOT IN (:blocked) AND (:sourceId<0 OR sourceId=:sourceId)
        ORDER BY CASE :sort WHEN 1 THEN titleNorm END, CASE :sort WHEN 0 THEN addedAt END DESC LIMIT :limit""")
    fun observeMovies(group: String?, q: String, hideAdult: Boolean, blocked: List<String>, sourceId: Long, sort: Int, limit: Int): Flow<List<VodItemEntity>>

    @Query("""SELECT * FROM series WHERE sourceId IN (SELECT id FROM sources WHERE enabled=1)
        AND (:group IS NULL OR groupNorm=:group) AND (:q = '' OR titleNorm LIKE '%'||:q||'%' OR groupNorm LIKE '%'||:q||'%')
        AND (:hideAdult=0 OR adult=0) AND groupNorm NOT IN (:blocked) AND (:sourceId<0 OR sourceId=:sourceId)
        ORDER BY CASE :sort WHEN 1 THEN titleNorm END, CASE :sort WHEN 0 THEN addedAt END DESC LIMIT :limit""")
    fun observeSeries(group: String?, q: String, hideAdult: Boolean, blocked: List<String>, sourceId: Long, sort: Int, limit: Int): Flow<List<SeriesEntity>>

    @Query("""SELECT * FROM vod_items WHERE sourceId IN (SELECT id FROM sources WHERE enabled=1)
        AND (:group IS NULL OR groupNorm=:group) AND (:q = '' OR titleNorm LIKE '%'||:q||'%' OR groupNorm LIKE '%'||:q||'%')
        AND (:hideAdult=0 OR adult=0) AND groupNorm NOT IN (:blocked) AND (:sourceId<0 OR sourceId=:sourceId)
        ORDER BY CASE :sort WHEN 1 THEN titleNorm END, CASE :sort WHEN 0 THEN addedAt END DESC""")
    fun pagedMovies(group: String?, q: String, hideAdult: Boolean, blocked: List<String>, sourceId: Long, sort: Int): PagingSource<Int, VodItemEntity>

    @Query("""SELECT * FROM series WHERE sourceId IN (SELECT id FROM sources WHERE enabled=1)
        AND (:group IS NULL OR groupNorm=:group) AND (:q = '' OR titleNorm LIKE '%'||:q||'%' OR groupNorm LIKE '%'||:q||'%')
        AND (:hideAdult=0 OR adult=0) AND groupNorm NOT IN (:blocked) AND (:sourceId<0 OR sourceId=:sourceId)
        ORDER BY CASE :sort WHEN 1 THEN titleNorm END, CASE :sort WHEN 0 THEN addedAt END DESC""")
    fun pagedSeries(group: String?, q: String, hideAdult: Boolean, blocked: List<String>, sourceId: Long, sort: Int): PagingSource<Int, SeriesEntity>

    @Query("""SELECT MIN(groupName) AS name, groupNorm AS norm, COUNT(*) AS count FROM vod_items
        WHERE sourceId IN (SELECT id FROM sources WHERE enabled=1) AND (:hideAdult=0 OR adult=0) AND groupNorm NOT IN (:blocked)
        GROUP BY groupNorm ORDER BY MIN(groupName)""")
    fun movieGroups(hideAdult: Boolean, blocked: List<String>): Flow<List<GroupCount>>

    @Query("""SELECT MIN(groupName) AS name, groupNorm AS norm, COUNT(*) AS count FROM series
        WHERE sourceId IN (SELECT id FROM sources WHERE enabled=1) AND (:hideAdult=0 OR adult=0) AND groupNorm NOT IN (:blocked)
        GROUP BY groupNorm ORDER BY MIN(groupName)""")
    fun seriesGroups(hideAdult: Boolean, blocked: List<String>): Flow<List<GroupCount>>

    @Query("""SELECT v.* FROM vod_items v INNER JOIN favorites f ON f.itemId=v.id AND f.kind='MOVIE'
        WHERE f.profileId=:p AND (:hideAdult=0 OR v.adult=0) AND v.groupNorm NOT IN (:blocked) ORDER BY f.addedAt DESC""")
    fun favoriteMovies(p: Long, hideAdult: Boolean, blocked: List<String>): Flow<List<VodItemEntity>>

    @Query("""SELECT s.* FROM series s INNER JOIN favorites f ON f.itemId=s.id AND f.kind='SERIES'
        WHERE f.profileId=:p AND (:hideAdult=0 OR s.adult=0) AND s.groupNorm NOT IN (:blocked) ORDER BY f.addedAt DESC""")
    fun favoriteSeries(p: Long, hideAdult: Boolean, blocked: List<String>): Flow<List<SeriesEntity>>

    @Query("""SELECT * FROM vod_items WHERE sourceId IN (SELECT id FROM sources WHERE enabled=1) AND (:hideAdult=0 OR adult=0)
        AND groupNorm NOT IN (:blocked) AND titleNorm LIKE '%'||:q||'%' LIMIT :limit""")
    suspend fun searchMovies(q: String, hideAdult: Boolean, blocked: List<String>, limit: Int): List<VodItemEntity>

    @Query("""SELECT * FROM series WHERE sourceId IN (SELECT id FROM sources WHERE enabled=1) AND (:hideAdult=0 OR adult=0)
        AND groupNorm NOT IN (:blocked) AND titleNorm LIKE '%'||:q||'%' LIMIT :limit""")
    suspend fun searchSeries(q: String, hideAdult: Boolean, blocked: List<String>, limit: Int): List<SeriesEntity>

    @Query("""SELECT * FROM channels WHERE sourceId IN (SELECT id FROM sources WHERE enabled=1) AND (:hideAdult=0 OR adult=0)
        AND groupNorm NOT IN (:blocked) AND (nameNorm LIKE '%'||:q||'%' OR groupNorm LIKE '%'||:q||'%') LIMIT :limit""")
    suspend fun searchChannels(q: String, hideAdult: Boolean, blocked: List<String>, limit: Int): List<ChannelEntity>
}

@Dao
interface EpgDao {
    @Insert suspend fun insertPrograms(items: List<ProgramEntity>)
    @Upsert suspend fun upsertEpgChannels(items: List<EpgChannelEntity>)
    @Query("DELETE FROM programs WHERE sourceId=:s") suspend fun deleteProgramsBySource(s: Long)
    @Query("DELETE FROM epg_channels WHERE sourceId=:s") suspend fun deleteEpgChannelsBySource(s: Long)
    @Query("DELETE FROM programs WHERE endMs<:before") suspend fun pruneBefore(before: Long)
    @Query("SELECT COUNT(*) FROM programs WHERE sourceId=:s") suspend fun programCount(s: Long): Int
    @Query("SELECT * FROM epg_channels") suspend fun epgChannels(): List<EpgChannelEntity>
    @Query("SELECT MAX(endMs) FROM programs") suspend fun lastProgramEnd(): Long?

    @Query("SELECT * FROM programs WHERE epgChannelId IN (:ids) AND endMs>:from AND startMs<:to ORDER BY epgChannelId, startMs")
    suspend fun programsFor(ids: List<String>, from: Long, to: Long): List<ProgramEntity>

    @Query("SELECT * FROM programs WHERE titleNorm LIKE '%'||:q||'%' AND endMs>:now ORDER BY startMs LIMIT :limit")
    suspend fun searchPrograms(q: String, now: Long, limit: Int): List<ProgramEntity>

    @Query("SELECT * FROM epg_mappings") fun observeMappings(): Flow<List<EpgMappingEntity>>
    @Query("SELECT * FROM epg_mappings") suspend fun mappings(): List<EpgMappingEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setMapping(m: EpgMappingEntity)
    @Query("DELETE FROM epg_mappings WHERE channelId=:channelId") suspend fun clearMapping(channelId: String)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM profiles ORDER BY id") fun observeProfiles(): Flow<List<ProfileEntity>>
    @Query("SELECT * FROM profiles WHERE id=:id") suspend fun profile(id: Long): ProfileEntity?
    @Query("SELECT COUNT(*) FROM profiles") suspend fun profileCount(): Int
    @Insert suspend fun insertProfile(p: ProfileEntity): Long
    @Update suspend fun updateProfile(p: ProfileEntity)
    @Query("DELETE FROM profiles WHERE id=:id") suspend fun deleteProfile(id: Long)
    @Query("DELETE FROM favorites WHERE profileId=:id") suspend fun deleteFavoritesOf(id: Long)
    @Query("DELETE FROM watch_progress WHERE profileId=:id") suspend fun deleteProgressOf(id: Long)
    @Query("DELETE FROM playback_history WHERE profileId=:id") suspend fun deleteHistoryOf(id: Long)
    @Query("DELETE FROM recent_searches WHERE profileId=:id") suspend fun deleteSearchesOf(id: Long)

    @Query("SELECT itemId FROM favorites WHERE profileId=:p AND kind=:kind") fun favoriteIds(p: Long, kind: String): Flow<List<String>>
    @Query("SELECT COUNT(*) FROM favorites WHERE profileId=:p AND kind=:kind AND itemId=:id") suspend fun isFavorite(p: Long, kind: String, id: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFavorite(f: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE profileId=:p AND kind=:kind AND itemId=:id") suspend fun removeFavorite(p: Long, kind: String, id: String)
    @Query("DELETE FROM favorites WHERE itemId IN (SELECT id FROM channels WHERE sourceId=:s) OR itemId IN (SELECT id FROM vod_items WHERE sourceId=:s) OR itemId IN (SELECT id FROM series WHERE sourceId=:s)")
    suspend fun deleteFavoritesOfSource(s: Long)
    @Query("DELETE FROM watch_progress WHERE itemId IN (SELECT id FROM vod_items WHERE sourceId=:s) OR itemId IN (SELECT e.id FROM episodes e INNER JOIN series se ON se.id=e.seriesId WHERE se.sourceId=:s)")
    suspend fun deleteProgressOfSource(s: Long)

    @Upsert suspend fun upsertProgress(p: WatchProgressEntity)
    @Query("SELECT * FROM watch_progress WHERE profileId=:p AND itemId=:id") suspend fun progress(p: Long, id: String): WatchProgressEntity?
    @Query("SELECT * FROM watch_progress WHERE profileId=:p AND completed=0 AND positionMs>10000 ORDER BY updatedAt DESC LIMIT :limit")
    fun continueRows(p: Long, limit: Int): Flow<List<WatchProgressEntity>>
    @Query("SELECT * FROM watch_progress WHERE profileId=:p ORDER BY updatedAt DESC LIMIT :limit")
    fun recentVodRows(p: Long, limit: Int): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM favorites WHERE profileId=:p ORDER BY addedAt LIMIT 5000") suspend fun favoritesOf(p: Long): List<FavoriteEntity>
    @Query("SELECT * FROM watch_progress WHERE profileId=:p ORDER BY updatedAt DESC LIMIT 2000") suspend fun progressOf(p: Long): List<WatchProgressEntity>
    @Query("SELECT * FROM playback_history WHERE profileId=:p ORDER BY watchedAt DESC LIMIT 300") suspend fun historyOf(p: Long): List<PlaybackHistoryEntity>
    @Insert suspend fun addHistory(h: PlaybackHistoryEntity)
    @Query("SELECT itemId FROM playback_history WHERE profileId=:p AND kind='LIVE' GROUP BY itemId ORDER BY MAX(watchedAt) DESC LIMIT :limit")
    fun recentChannelIds(p: Long, limit: Int): Flow<List<String>>
    @Query("DELETE FROM playback_history WHERE watchedAt<:before") suspend fun pruneHistory(before: Long)

    @Query("SELECT text FROM recent_searches WHERE profileId=:p ORDER BY at DESC LIMIT 8") fun recentSearches(p: Long): Flow<List<String>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addSearch(r: RecentSearchEntity)
    @Query("DELETE FROM recent_searches WHERE profileId=:p") suspend fun clearSearches(p: Long)
}

@Dao
interface ImportDao {
    @Insert suspend fun insert(j: ImportJobEntity): Long
    @Update suspend fun update(j: ImportJobEntity)
    @Query("SELECT * FROM import_jobs WHERE sourceId=:s ORDER BY startedAt DESC LIMIT 10") suspend fun recent(s: Long): List<ImportJobEntity>
    @Query("DELETE FROM import_jobs WHERE sourceId=:s") suspend fun deleteBySource(s: Long)
    @Query("SELECT * FROM group_overrides WHERE sourceId=:s") suspend fun overrides(s: Long): List<GroupOverrideEntity>
    @Upsert suspend fun setOverride(o: GroupOverrideEntity)
    @Query("DELETE FROM group_overrides WHERE sourceId=:s AND groupNorm=:g") suspend fun clearOverride(s: Long, g: String)
    @Query("DELETE FROM group_overrides WHERE sourceId=:s") suspend fun deleteOverridesBySource(s: Long)
}

/** A playlist with the number of items it holds (counted in SQL, not by loading the items). */
data class PlaylistRow(val id: Long, val name: String, val createdAt: Long, val itemCount: Int)

@Dao
interface PlaylistDao {
    @Query("""SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt,
        (SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId=p.id) AS itemCount
        FROM playlists p WHERE p.profileId=:profileId ORDER BY p.createdAt, p.id""")
    fun observe(profileId: Long): Flow<List<PlaylistRow>>

    @Query("SELECT * FROM playlists WHERE id=:id") suspend fun byId(id: Long): PlaylistEntity?
    @Insert suspend fun insert(p: PlaylistEntity): Long
    @Query("UPDATE playlists SET name=:name WHERE id=:id") suspend fun rename(id: Long, name: String)
    @Query("DELETE FROM playlists WHERE id=:id") suspend fun delete(id: Long)
    @Query("DELETE FROM playlist_items WHERE playlistId=:id") suspend fun deleteItems(id: Long)
    @Query("DELETE FROM playlist_items WHERE playlistId IN (SELECT id FROM playlists WHERE profileId=:profileId)") suspend fun deleteItemsOfProfile(profileId: Long)
    @Query("DELETE FROM playlists WHERE profileId=:profileId") suspend fun deleteOfProfile(profileId: Long)
    /** Items of a source that is being removed together with its favourites. */
    @Query("DELETE FROM playlist_items WHERE itemId IN (SELECT id FROM channels WHERE sourceId=:s) OR itemId IN (SELECT id FROM vod_items WHERE sourceId=:s) OR itemId IN (SELECT id FROM series WHERE sourceId=:s)")
    suspend fun deleteItemsOfSource(s: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addItem(i: PlaylistItemEntity)
    @Query("DELETE FROM playlist_items WHERE playlistId=:playlistId AND kind=:kind AND itemId=:itemId") suspend fun removeItem(playlistId: Long, kind: String, itemId: String)
    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId=:playlistId AND kind=:kind AND itemId=:itemId") suspend fun contains(playlistId: Long, kind: String, itemId: String): Int

    @Query("""SELECT i.playlistId FROM playlist_items i INNER JOIN playlists p ON p.id=i.playlistId
        WHERE p.profileId=:profileId AND i.kind=:kind AND i.itemId=:itemId""")
    fun playlistIdsContaining(profileId: Long, kind: String, itemId: String): Flow<List<Long>>

    @Query("SELECT * FROM playlist_items WHERE playlistId IN (SELECT id FROM playlists WHERE profileId=:profileId) ORDER BY addedAt LIMIT 20000")
    suspend fun itemsOfProfile(profileId: Long): List<PlaylistItemEntity>

    @Query("""SELECT c.* FROM channels c INNER JOIN playlist_items i ON i.itemId=c.id AND i.kind='LIVE'
        WHERE i.playlistId=:playlistId AND c.sourceId IN (SELECT id FROM sources WHERE enabled=1)
        AND (:hideAdult=0 OR c.adult=0) AND c.groupNorm NOT IN (:blocked) ORDER BY i.addedAt""")
    fun channels(playlistId: Long, hideAdult: Boolean, blocked: List<String>): Flow<List<ChannelEntity>>

    @Query("""SELECT v.* FROM vod_items v INNER JOIN playlist_items i ON i.itemId=v.id AND i.kind='MOVIE'
        WHERE i.playlistId=:playlistId AND v.sourceId IN (SELECT id FROM sources WHERE enabled=1)
        AND (:hideAdult=0 OR v.adult=0) AND v.groupNorm NOT IN (:blocked) ORDER BY i.addedAt""")
    fun movies(playlistId: Long, hideAdult: Boolean, blocked: List<String>): Flow<List<VodItemEntity>>

    @Query("""SELECT s.* FROM series s INNER JOIN playlist_items i ON i.itemId=s.id AND i.kind='SERIES'
        WHERE i.playlistId=:playlistId AND s.sourceId IN (SELECT id FROM sources WHERE enabled=1)
        AND (:hideAdult=0 OR s.adult=0) AND s.groupNorm NOT IN (:blocked) ORDER BY i.addedAt""")
    fun series(playlistId: Long, hideAdult: Boolean, blocked: List<String>): Flow<List<SeriesEntity>>
}
