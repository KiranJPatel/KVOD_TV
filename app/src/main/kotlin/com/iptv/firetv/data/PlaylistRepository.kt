package com.iptv.firetv.data

import androidx.room.withTransaction
import com.iptv.firetv.core.database.AppDatabase
import com.iptv.firetv.core.database.ChannelEntity
import com.iptv.firetv.core.database.PlaylistEntity
import com.iptv.firetv.core.database.PlaylistItemEntity
import com.iptv.firetv.core.database.PlaylistRow
import com.iptv.firetv.core.database.SeriesEntity
import com.iptv.firetv.core.database.VodItemEntity
import com.iptv.firetv.core.model.ContentKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User playlists ("watch later" lists). Unlimited per profile, private to the profile, and every list read goes through the
 * profile's content restrictions exactly like favourites do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PlaylistRepository @Inject constructor(private val db: AppDatabase, private val profiles: ProfileRepository) {
    private val activeId: Flow<Long> = profiles.activeProfile.filterNotNull().map { it.id }

    val playlists: Flow<List<PlaylistRow>> = activeId.flatMapLatest { db.playlists().observe(it) }

    /** Returns the new playlist id, or null when the name is blank. Names are trimmed and limited to 40 characters. */
    suspend fun create(name: String): Long? {
        val n = name.trim().take(MAX_NAME)
        if (n.isEmpty()) return null
        return db.playlists().insert(PlaylistEntity(profileId = profiles.activeId(), name = n, createdAt = System.currentTimeMillis()))
    }

    suspend fun rename(id: Long, name: String) {
        val n = name.trim().take(MAX_NAME)
        if (n.isNotEmpty() && owns(id)) db.playlists().rename(id, n)
    }

    suspend fun delete(id: Long) {
        if (!owns(id)) return
        db.withTransaction { db.playlists().deleteItems(id); db.playlists().delete(id) }
    }

    suspend fun nameOf(id: Long): String? = db.playlists().byId(id)?.takeIf { it.profileId == profiles.activeId() }?.name

    /** Adds the item when it is not in the playlist, removes it otherwise. Returns true when it is in the playlist afterwards. */
    suspend fun toggle(playlistId: Long, kind: ContentKind, itemId: String): Boolean {
        if (!owns(playlistId)) return false
        return if (db.playlists().contains(playlistId, kind.name, itemId) > 0) {
            db.playlists().removeItem(playlistId, kind.name, itemId); false
        } else {
            db.playlists().addItem(PlaylistItemEntity(playlistId, kind.name, itemId, System.currentTimeMillis())); true
        }
    }

    suspend fun remove(playlistId: Long, kind: ContentKind, itemId: String) { if (owns(playlistId)) db.playlists().removeItem(playlistId, kind.name, itemId) }

    /** Ids of the active profile's playlists that contain the item (drives the check marks in the picker). */
    fun playlistIdsContaining(kind: ContentKind, itemId: String): Flow<Set<Long>> =
        activeId.flatMapLatest { db.playlists().playlistIdsContaining(it, kind.name, itemId) }.map { it.toSet() }

    fun channels(playlistId: Long): Flow<List<ChannelEntity>> = combine(profiles.restrictions, activeId) { r, _ -> r }
        .flatMapLatest { r -> db.playlists().channels(playlistId, r.hideAdult, r.blockedGroups.toList()) }
    fun movies(playlistId: Long): Flow<List<VodItemEntity>> = profiles.restrictions
        .flatMapLatest { r -> db.playlists().movies(playlistId, r.hideAdult, r.blockedGroups.toList()) }
    fun series(playlistId: Long): Flow<List<SeriesEntity>> = profiles.restrictions
        .flatMapLatest { r -> db.playlists().series(playlistId, r.hideAdult, r.blockedGroups.toList()) }

    /** Ordered channel ids of a playlist (for the player's channel queue). */
    suspend fun channelIds(playlistId: Long): List<String> = if (owns(playlistId)) channels(playlistId).first().map { it.id } else emptyList()

    private suspend fun owns(playlistId: Long): Boolean = db.playlists().byId(playlistId)?.profileId == profiles.activeId()

    companion object { const val MAX_NAME = 40 }
}
