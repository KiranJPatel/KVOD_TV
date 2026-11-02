package com.iptv.firetv.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    /** AES-GCM blob (Android Keystore). URL, credentials, user-agent live only here. */
    val secretCipher: String? = null,
    /** Redacted host for display, never contains credentials. */
    val displayHost: String? = null,
    val localPath: String? = null,
    val refreshHours: Int = 24,
    val lastRefreshAt: Long? = null,
    val lastStatus: String? = null,
    val lastError: String? = null,
    val channelCount: Int = 0,
    val vodCount: Int = 0,
    val seriesCount: Int = 0,
    val programCount: Int = 0
)

/** Stable id = hash(sourceId, tvg-id or normalised name, normalised group, duplicate ordinal). Survives reorders and refreshes. */
@Entity(tableName = "channels", indices = [Index("sourceId"), Index("groupNorm"), Index("nameNorm"), Index("tvgId"), Index("dupKey")])
data class ChannelEntity(
    @PrimaryKey val id: String,
    val sourceId: Long,
    val name: String,
    val nameNorm: String,
    val groupName: String,
    val groupNorm: String,
    val logo: String?,
    val number: Int?,
    val tvgId: String?,
    /** Plain URL for M3U sources, or "xtream:live:<id>" resolved at playback time so no credentials are duplicated. */
    val streamRef: String,
    val headersJson: String?,
    val adult: Boolean,
    val sortIndex: Int,
    val updatedAt: Long,
    /** Same value for every copy of one channel (see ChannelNaming); filled at import and by a one-time backfill after the v3 migration. */
    @ColumnInfo(defaultValue = "''") val dupKey: String = "",
    /** 1..5, higher = shown first among duplicates. */
    @ColumnInfo(defaultValue = "3") val qualityRank: Int = 3
)

@Entity(tableName = "vod_items", indices = [Index("sourceId"), Index("groupNorm"), Index("titleNorm"), Index("addedAt")])
data class VodItemEntity(
    @PrimaryKey val id: String,
    val sourceId: Long,
    val title: String,
    val titleNorm: String,
    val groupName: String,
    val groupNorm: String,
    val poster: String?,
    val backdrop: String? = null,
    val plot: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val rating: String? = null,
    val durationSec: Int? = null,
    val streamRef: String,
    val headersJson: String? = null,
    val adult: Boolean,
    val addedAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "series", indices = [Index("sourceId"), Index("groupNorm"), Index("titleNorm"), Index("addedAt")])
data class SeriesEntity(
    @PrimaryKey val id: String,
    val sourceId: Long,
    val title: String,
    val titleNorm: String,
    val groupName: String,
    val groupNorm: String,
    val poster: String?,
    val plot: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val rating: String? = null,
    val externalId: String? = null,
    val adult: Boolean,
    val addedAt: Long,
    val updatedAt: Long
)

/** Season is a column of the episode (a separate Season table adds no data for M3U/Xtream sources; see FEATURES.md). */
@Entity(tableName = "episodes", indices = [Index("seriesId")])
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val seriesId: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val plot: String? = null,
    val durationSec: Int? = null,
    val poster: String? = null,
    val streamRef: String,
    val headersJson: String? = null,
    val updatedAt: Long
)

@Entity(tableName = "epg_channels", primaryKeys = ["sourceId", "epgId"])
data class EpgChannelEntity(val sourceId: Long, val epgId: String, val displayNames: String, val icon: String?)

@Entity(tableName = "programs", indices = [Index("epgChannelId", "startMs"), Index("sourceId"), Index("titleNorm")])
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val epgChannelId: String,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val titleNorm: String,
    val subTitle: String?,
    val description: String?,
    val category: String?,
    val episodeNum: String?,
    val previouslyShown: Boolean
)

/** Explicit user mapping channel → XMLTV channel id (wins over automatic matching). */
@Entity(tableName = "epg_mappings")
data class EpgMappingEntity(@PrimaryKey val channelId: String, val epgChannelId: String)

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatar: Int = 0,
    val isOwner: Boolean = false,
    val kids: Boolean = false,
    val hideAdult: Boolean = true,
    /** Normalised group names separated by "|". */
    val blockedGroups: String = "",
    val pinSalt: String? = null,
    val pinHash: String? = null,
    val audioLang: String = "",
    val subtitleLang: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites", primaryKeys = ["profileId", "kind", "itemId"])
data class FavoriteEntity(val profileId: Long, val kind: String, val itemId: String, val addedAt: Long)

@Entity(tableName = "watch_progress", primaryKeys = ["profileId", "itemId"])
data class WatchProgressEntity(
    val profileId: Long, val itemId: String, val kind: String, val title: String, val poster: String?,
    val positionMs: Long, val durationMs: Long, val completed: Boolean, val updatedAt: Long
)

@Entity(tableName = "playback_history", indices = [Index("profileId", "watchedAt")])
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long, val itemId: String, val kind: String, val watchedAt: Long
)

@Entity(tableName = "import_jobs", indices = [Index("sourceId")])
data class ImportJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long, val startedAt: Long, val finishedAt: Long?, val status: String,
    val total: Int, val invalid: Int, val duplicates: Int, val message: String?
)

@Entity(tableName = "recent_searches", primaryKeys = ["profileId", "text"])
data class RecentSearchEntity(val profileId: Long, val text: String, val at: Long)

/** User correction: treat every entry of this playlist group as the given content kind (LIVE, MOVIE or SERIES) on the next import. */
@Entity(tableName = "group_overrides", primaryKeys = ["sourceId", "groupNorm"])
data class GroupOverrideEntity(val sourceId: Long, val groupNorm: String, val groupName: String, val kind: String)

/** A user-made playlist ("Watch later", "Cricket", ...). Unlimited per profile; items can be live channels, movies or series. */
@Entity(tableName = "playlists", indices = [Index("profileId")])
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    val createdAt: Long
)

/** Membership of one item in one playlist. [kind] is LIVE, MOVIE or SERIES (same values as favourites). */
@Entity(tableName = "playlist_items", primaryKeys = ["playlistId", "kind", "itemId"], indices = [Index("playlistId")])
data class PlaylistItemEntity(val playlistId: Long, val kind: String, val itemId: String, val addedAt: Long)
