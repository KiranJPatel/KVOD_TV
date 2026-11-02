package com.iptv.firetv.data

import android.content.Context
import android.net.Uri
import com.iptv.firetv.core.common.AppException
import com.iptv.firetv.core.common.ErrorKind
import com.iptv.firetv.core.database.AppDatabase
import com.iptv.firetv.core.common.TextUtil
import com.iptv.firetv.core.database.FavoriteEntity
import com.iptv.firetv.core.database.PlaybackHistoryEntity
import com.iptv.firetv.core.database.ProfileEntity
import com.iptv.firetv.core.database.WatchProgressEntity
import com.iptv.firetv.core.database.SourceEntity
import com.iptv.firetv.core.model.AccentColor
import com.iptv.firetv.core.model.AppTheme
import com.iptv.firetv.core.model.ThemeMode
import com.iptv.firetv.core.player.SubBackground
import com.iptv.firetv.core.player.SubColor
import com.iptv.firetv.core.player.SubEdge
import com.iptv.firetv.core.player.SubFont
import com.iptv.firetv.core.player.SubPosition
import com.iptv.firetv.core.player.SubSize
import com.iptv.firetv.core.player.SubtitleStyle
import com.iptv.firetv.core.database.PlaylistEntity
import com.iptv.firetv.core.database.PlaylistItemEntity
import com.iptv.firetv.core.model.BufferProfile
import com.iptv.firetv.core.model.QualityMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup format v1 (JSON). It contains preferences, profile settings (no PINs) and the *definition* of each source
 * (name, type, refresh interval) — never addresses, usernames, passwords or tokens. Restored sources are created
 * with a "needs setup" status until the user re-enters their access details.
 * Version 2 also carries favourites, Continue Watching positions and recently watched channels per profile. Item ids are
 * derived per installation, so they are stored as title references and re-attached when the matching source has been
 * imported (unmatched ones wait in a private pending file and are retried after every import).
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT, val version: Int = 2, val createdAt: Long = 0,
    val settings: BackupSettings = BackupSettings(), val profiles: List<BackupProfile> = emptyList(), val sources: List<BackupSource> = emptyList(),
    /** v2: favourites, resume positions and recently watched channels per profile, referenced by title (ids are per installation). */
    val userData: List<BackupUserData> = emptyList()
) { companion object { const val FORMAT = "iptv-firetv-backup" } }

/** Portable reference to a catalogue item: source name + normalised title/group (+ season/episode). */
@Serializable
data class ItemRef(val kind: String, val source: String, val name: String, val group: String = "", val tvgId: String? = null, val season: Int? = null, val episode: Int? = null)

@Serializable data class ProgressRef(val item: ItemRef, val title: String, val positionMs: Long, val durationMs: Long, val completed: Boolean, val updatedAt: Long)
@Serializable data class RecentRef(val item: ItemRef, val at: Long)

/** A user playlist with its items as portable references (re-attached like favourites). */
@Serializable data class BackupPlaylist(val name: String, val items: List<ItemRef> = emptyList())

@Serializable
data class BackupUserData(
    val profile: String, val favorites: List<ItemRef> = emptyList(), val progress: List<ProgressRef> = emptyList(), val recentChannels: List<RecentRef> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList()
) {
    val isEmpty get() = favorites.isEmpty() && progress.isEmpty() && recentChannels.isEmpty() && playlists.isEmpty()
    companion object { const val FORMAT = "iptv-firetv-backup" }
}

@Serializable
data class BackupSettings(
    val theme: String = AppTheme.AURORA.name, val accent: String = AccentColor.THEME.name, val textScale: Float = 1f,
    val autoplayNext: Boolean = true, val confirmExit: Boolean = false, val overlaySeconds: Int = 5, val dpadZapping: Boolean = true,
    val buffer: String = BufferProfile.BALANCED.name, val maxRetries: Int = 3, val epgZone: String = "", val epgNameMatching: Boolean = false,
    val channelSort: Int = 0, val inactivityLockMin: Int = 0, val resizeMode: Int = 0, val livePreview: Boolean = true, val mergeDuplicates: Boolean = false,
    val quality: String = QualityMode.LOCK_1080.name,
    val uiScale: Float = 1f,
    val themeMode: String = ThemeMode.DARK.name,
    val subColor: String = SubColor.WHITE.name, val subBackground: String = SubBackground.TRANSPARENT.name, val subSize: String = SubSize.MEDIUM.name,
    val subFont: String = SubFont.SANS.name, val subEdge: String = SubEdge.OUTLINE.name, val subPosition: String = SubPosition.BOTTOM.name,
    val showMoviesTab: Boolean = true, val showSeriesTab: Boolean = true, val showPreviewPane: Boolean = true, val keepScreenAwake: Boolean = true
)

@Serializable
data class BackupProfile(
    val name: String, val avatar: Int = 0, val kids: Boolean = false, val hideAdult: Boolean = true, val blockedGroups: String = "",
    val audioLang: String = "", val subtitleLang: String = ""
)

@Serializable
data class BackupSource(val name: String, val type: String, val refreshHours: Int = 24, val enabled: Boolean = true)

object BackupCodec {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    const val MAX_BYTES = 2 * 1024 * 1024

    fun encode(b: BackupFile): String = json.encodeToString(b)

    /** Validates format, version and size; throws [AppException] with a user-facing message otherwise. */
    fun decode(text: String): BackupFile {
        if (text.length > MAX_BYTES) throw AppException(ErrorKind.TOO_LARGE, "This does not look like a KVOD TV backup (file is too large).")
        val b = try { json.decodeFromString<BackupFile>(text) } catch (e: Exception) { throw AppException(ErrorKind.INPUT, "This file is not a valid KVOD TV backup.", e) }
        if (b.format != BackupFile.FORMAT) throw AppException(ErrorKind.INPUT, "This file is not a KVOD TV backup.")
        if (b.version > 2) throw AppException(ErrorKind.INPUT, "This backup was made by a newer version of the app.")
        return b
    }
}

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val ctx: Context, private val db: AppDatabase, private val settings: SettingsRepository, private val profiles: ProfileRepository
) {
    suspend fun build(): BackupFile {
        val s = settings.current()
        return BackupFile(
            createdAt = System.currentTimeMillis(),
            settings = BackupSettings(s.theme.name, s.accent.name, s.textScale, s.autoplayNext, s.confirmExit, s.overlaySeconds, s.dpadZapping, s.buffer.name, s.maxRetries,
                s.epgZone, s.epgNameMatching, s.channelSort, s.inactivityLockMin, s.resizeMode, s.livePreview, s.mergeDuplicates, s.quality.name, s.uiScale,
                s.themeMode.name, s.subtitle.color.name, s.subtitle.background.name, s.subtitle.size.name, s.subtitle.font.name, s.subtitle.edge.name, s.subtitle.position.name,
                s.showMoviesTab, s.showSeriesTab, s.showPreviewPane, s.keepScreenAwake),
            profiles = profiles.profiles.first().map { BackupProfile(it.name, it.avatar, it.kids, it.hideAdult, it.blockedGroups, it.audioLang, it.subtitleLang) },
            sources = db.sources().all().filter { it.type != "LOCAL_MEDIA" }.map { BackupSource(it.name, it.type, it.refreshHours, it.enabled) },
            userData = buildUserData()
        )
    }

    private val pendingFile get() = java.io.File(ctx.filesDir, "pending_userdata.json")
    private val pendingJson = Json { ignoreUnknownKeys = true }

    private suspend fun buildUserData(): List<BackupUserData> {
        val src = db.sources().all().associate { it.id to it.name }
        fun name(id: Long) = src[id] ?: ""
        return profiles.profiles.first().map { p ->
            val favs = db.user().favoritesOf(p.id)
            val refs = ArrayList<ItemRef>()
            db.channels().byIds(favs.filter { it.kind == "LIVE" }.map { it.itemId }).forEach { refs += ItemRef("LIVE", name(it.sourceId), it.name, it.groupName, it.tvgId) }
            db.vod().moviesByIds(favs.filter { it.kind == "MOVIE" }.map { it.itemId }).forEach { refs += ItemRef("MOVIE", name(it.sourceId), it.title, it.groupName) }
            db.vod().seriesByIds(favs.filter { it.kind == "SERIES" }.map { it.itemId }).forEach { refs += ItemRef("SERIES", name(it.sourceId), it.title, it.groupName) }
            val prog = db.user().progressOf(p.id)
            val movies = db.vod().moviesByIds(prog.filter { it.kind == "MOVIE" }.map { it.itemId }).associateBy { it.id }
            val eps = db.vod().episodesByIds(prog.filter { it.kind == "EPISODE" }.map { it.itemId }).associateBy { it.id }
            val series = db.vod().seriesByIds(eps.values.map { it.seriesId }.distinct()).associateBy { it.id }
            val progRefs = prog.mapNotNull { w ->
                val ref = when (w.kind) {
                    "MOVIE" -> movies[w.itemId]?.let { ItemRef("MOVIE", name(it.sourceId), it.title, it.groupName) }
                    "EPISODE" -> eps[w.itemId]?.let { e -> series[e.seriesId]?.let { s -> ItemRef("EPISODE", name(s.sourceId), s.title, s.groupName, season = e.season, episode = e.episode) } }
                    else -> null
                }
                ref?.let { ProgressRef(it, w.title, w.positionMs, w.durationMs, w.completed, w.updatedAt) }
            }
            val hist = db.user().historyOf(p.id)
            val chans = db.channels().byIds(hist.map { it.itemId }.distinct()).associateBy { it.id }
            val recent = hist.mapNotNull { h -> chans[h.itemId]?.let { RecentRef(ItemRef("LIVE", name(it.sourceId), it.name, it.groupName, it.tvgId), h.watchedAt) } }
            val items = db.playlists().itemsOfProfile(p.id)
            val plRefs = db.playlists().observe(p.id).first().map { pl ->
                val its = items.filter { it.playlistId == pl.id }
                val out = ArrayList<ItemRef>()
                db.channels().byIds(its.filter { it.kind == "LIVE" }.map { it.itemId }).forEach { out += ItemRef("LIVE", name(it.sourceId), it.name, it.groupName, it.tvgId) }
                db.vod().moviesByIds(its.filter { it.kind == "MOVIE" }.map { it.itemId }).forEach { out += ItemRef("MOVIE", name(it.sourceId), it.title, it.groupName) }
                db.vod().seriesByIds(its.filter { it.kind == "SERIES" }.map { it.itemId }).forEach { out += ItemRef("SERIES", name(it.sourceId), it.title, it.groupName) }
                BackupPlaylist(pl.name, out)
            }
            BackupUserData(p.name, refs, progRefs, recent, plRefs)
        }.filter { !it.isEmpty }
    }

    private suspend fun resolve(r: ItemRef): String? {
        val n = TextUtil.normalize(r.name); val g = TextUtil.normalize(r.group)
        return when (r.kind) {
            "LIVE" -> db.channels().find(r.source, n, g).let { l -> (l.firstOrNull { it.tvgId == r.tvgId && r.tvgId != null } ?: l.firstOrNull())?.id }
            "MOVIE" -> db.vod().findMovies(r.source, n, g).firstOrNull()?.id
            "SERIES" -> db.vod().findSeries(r.source, n, g).firstOrNull()?.id
            "EPISODE" -> db.vod().findSeries(r.source, n, g).firstOrNull()?.let { s -> db.vod().episodeAt(s.id, r.season ?: return null, r.episode ?: return null)?.id }
            else -> null
        }
    }

    /** Re-attaches what can be matched now; returns (applied, stillPending). Unmatched entries stay in the pending file. */
    private suspend fun applyUserData(list: List<BackupUserData>): Pair<Int, List<BackupUserData>> {
        val byName = profiles.profiles.first().associateBy { it.name.lowercase() }
        var applied = 0
        val left = ArrayList<BackupUserData>()
        for (u in list) {
            val pid = byName[u.profile.lowercase()]?.id ?: continue   // profile does not exist (yet): drop, never create silently
            val keepFav = ArrayList<ItemRef>(); val keepProg = ArrayList<ProgressRef>(); val keepRecent = ArrayList<RecentRef>()
            for (f in u.favorites.take(5000)) { val id = resolve(f); if (id == null) keepFav += f else { db.user().addFavorite(FavoriteEntity(pid, f.kind, id, System.currentTimeMillis())); applied++ } }
            for (w in u.progress.take(2000)) {
                val id = resolve(w.item)
                if (id == null) keepProg += w else {
                    val existing = db.user().progress(pid, id)
                    if (existing == null || existing.updatedAt < w.updatedAt) { db.user().upsertProgress(WatchProgressEntity(pid, id, w.item.kind, w.title, null, w.positionMs, w.durationMs, w.completed, w.updatedAt)); applied++ }
                }
            }
            for (r in u.recentChannels.take(300)) { val id = resolve(r.item); if (id == null) keepRecent += r else { db.user().addHistory(PlaybackHistoryEntity(profileId = pid, itemId = id, kind = "LIVE", watchedAt = r.at)); applied++ } }
            val keepPl = ArrayList<BackupPlaylist>()
            for (pl in u.playlists.take(200)) {
                if (pl.name.isBlank()) continue
                val existing = db.playlists().observe(pid).first().firstOrNull { it.name.equals(pl.name, ignoreCase = true) }
                val plId = existing?.id ?: db.playlists().insert(PlaylistEntity(profileId = pid, name = pl.name.take(PlaylistRepository.MAX_NAME), createdAt = System.currentTimeMillis()))
                val keep = ArrayList<ItemRef>()
                for (r in pl.items.take(5000)) { val id = resolve(r); if (id == null) keep += r else { db.playlists().addItem(PlaylistItemEntity(plId, r.kind, id, System.currentTimeMillis())); applied++ } }
                if (keep.isNotEmpty()) keepPl += BackupPlaylist(pl.name, keep)
            }
            val rest = BackupUserData(u.profile, keepFav, keepProg, keepRecent, keepPl)
            if (!rest.isEmpty) left += rest
        }
        return applied to left
    }

    /** Called after every successful import/refresh: retries references that could not be matched before. */
    suspend fun applyPending() = withContext(Dispatchers.IO) {
        val f = pendingFile
        if (!f.exists()) return@withContext
        val pending = runCatching { pendingJson.decodeFromString<List<BackupUserData>>(f.readText()) }.getOrNull() ?: run { f.delete(); return@withContext }
        val (_, left) = applyUserData(pending)
        if (left.isEmpty()) f.delete() else f.writeText(pendingJson.encodeToString(left))
    }

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val text = BackupCodec.encode(build())
        try { ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw AppException(ErrorKind.STORAGE, "The file could not be written.") }
        catch (e: java.io.IOException) { throw AppException(ErrorKind.STORAGE, "Writing the backup failed.", e) }
    }

    /** Returns a short summary. Existing profiles/sources with the same name are kept untouched (restore never overwrites). */
    suspend fun restore(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = try { ctx.contentResolver.openInputStream(uri)?.use { com.iptv.firetv.core.network.BoundedInputStream(it, BackupCodec.MAX_BYTES.toLong()).readBytes() } }
            catch (e: java.io.IOException) { throw AppException(ErrorKind.STORAGE, "Reading the file failed.", e) } ?: throw AppException(ErrorKind.STORAGE, "The file could not be opened.")
        val b = BackupCodec.decode(String(bytes, Charsets.UTF_8))
        val st = b.settings
        // Backups from builds before themes had dark/light variants name MIDNIGHT / MATERIAL_DARK / MATERIAL_GRAY / LIGHT.
        val (restoredTheme, legacyMode) = AppTheme.fromStored(st.theme)
        settings.set(SettingKeys.THEME, restoredTheme.name)
        settings.set(SettingKeys.ACCENT, AccentColor.entries.firstOrNull { it.name == st.accent }?.name ?: AccentColor.THEME.name)
        settings.set(SettingKeys.TEXT_SCALE, st.textScale.coerceIn(0.8f, 1.5f))
        settings.set(SettingKeys.UI_SCALE, st.uiScale.coerceIn(0.8f, 1.25f))
        settings.set(SettingKeys.AUTOPLAY_NEXT, st.autoplayNext); settings.set(SettingKeys.CONFIRM_EXIT, st.confirmExit)
        settings.set(SettingKeys.OVERLAY_SECONDS, st.overlaySeconds.coerceIn(2, 30)); settings.set(SettingKeys.DPAD_ZAP, st.dpadZapping)
        settings.set(SettingKeys.BUFFER, BufferProfile.entries.firstOrNull { it.name == st.buffer }?.name ?: BufferProfile.BALANCED.name)
        settings.set(SettingKeys.MAX_RETRIES, st.maxRetries.coerceIn(0, 5)); settings.set(SettingKeys.EPG_ZONE, st.epgZone)
        settings.set(SettingKeys.EPG_NAME_MATCH, st.epgNameMatching); settings.set(SettingKeys.CHANNEL_SORT, st.channelSort.coerceIn(0, 2))
        settings.set(SettingKeys.INACTIVITY_LOCK, st.inactivityLockMin.coerceIn(0, 240)); settings.set(SettingKeys.RESIZE_MODE, st.resizeMode.coerceIn(0, 2))
        settings.set(SettingKeys.QUALITY, QualityMode.entries.firstOrNull { it.name == st.quality }?.name ?: QualityMode.LOCK_1080.name)
        settings.set(SettingKeys.LIVE_PREVIEW, st.livePreview); settings.set(SettingKeys.MERGE, st.mergeDuplicates)
        settings.set(SettingKeys.THEME_MODE, legacyMode?.name ?: ThemeMode.entries.firstOrNull { it.name == st.themeMode }?.name ?: ThemeMode.DARK.name)
        settings.setSubtitleStyle(SubtitleStyle(
            SubtitleStyle.parse(st.subColor, SubColor.WHITE), SubtitleStyle.parse(st.subBackground, SubBackground.TRANSPARENT), SubtitleStyle.parse(st.subSize, SubSize.MEDIUM),
            SubtitleStyle.parse(st.subFont, SubFont.SANS), SubtitleStyle.parse(st.subEdge, SubEdge.OUTLINE), SubtitleStyle.parse(st.subPosition, SubPosition.BOTTOM)))
        settings.set(SettingKeys.SHOW_MOVIES, st.showMoviesTab); settings.set(SettingKeys.SHOW_SERIES, st.showSeriesTab)
        settings.set(SettingKeys.SHOW_PREVIEW_PANE, st.showPreviewPane); settings.set(SettingKeys.KEEP_AWAKE, st.keepScreenAwake)

        val existingProfiles = profiles.profiles.first().map { it.name.lowercase() }.toSet()
        var addedProfiles = 0
        for (p in b.profiles.take(20)) if (p.name.isNotBlank() && p.name.lowercase() !in existingProfiles) {
            profiles.save(ProfileEntity(name = p.name.take(30), avatar = p.avatar, kids = p.kids, hideAdult = p.hideAdult || p.kids, blockedGroups = p.blockedGroups, audioLang = p.audioLang, subtitleLang = p.subtitleLang)); addedProfiles++
        }
        val existingSources = db.sources().all().map { it.name.lowercase() }.toSet()
        var addedSources = 0
        for (s in b.sources.take(50)) if (s.name.isNotBlank() && s.name.lowercase() !in existingSources && runCatching { com.iptv.firetv.core.model.SourceType.valueOf(s.type) }.isSuccess) {
            db.sources().insert(SourceEntity(name = s.name.take(60), type = s.type, enabled = s.enabled, sortOrder = db.sources().nextOrder(), refreshHours = s.refreshHours.coerceIn(0, 720),
                lastStatus = "NEEDS_SETUP", lastError = "Restored from a backup. Edit the source to enter its address or credentials (backups never contain them).")); addedSources++
        }
        val (applied, left) = applyUserData(b.userData)
        if (left.isNotEmpty()) runCatching { pendingFile.writeText(pendingJson.encodeToString(left)) }
        "Settings restored. Added $addedProfiles profile(s) and $addedSources source(s)" + (if (addedSources > 0) " that need their access details" else "") +
            if (b.userData.isNotEmpty()) ". Re-attached $applied favourite/history item(s)" + (if (left.isNotEmpty()) "; the rest will be matched after the sources are imported." else ".") else "."
    }
}
