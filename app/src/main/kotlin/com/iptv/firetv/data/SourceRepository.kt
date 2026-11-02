package com.iptv.firetv.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.iptv.firetv.core.common.AppException
import com.iptv.firetv.core.common.DiagnosticsLog
import com.iptv.firetv.core.common.ErrorKind
import com.iptv.firetv.core.common.Redactor
import com.iptv.firetv.core.common.TextUtil
import com.iptv.firetv.core.database.AppDatabase
import com.iptv.firetv.core.database.ImportJobEntity
import com.iptv.firetv.core.database.SourceEntity
import com.iptv.firetv.core.database.VodItemEntity
import com.iptv.firetv.core.model.SourceType
import com.iptv.firetv.core.network.Downloader
import com.iptv.firetv.core.network.XtreamClient
import com.iptv.firetv.core.network.XtreamConfig
import com.iptv.firetv.core.network.UrlCheck
import com.iptv.firetv.core.network.UrlValidator
import com.iptv.firetv.core.security.SecretCodec
import com.iptv.firetv.core.security.SourceSecrets
import com.iptv.firetv.core.security.open
import com.iptv.firetv.core.security.seal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: AppDatabase,
    private val secrets: SecretCodec,
    private val imports: ImportRepository,
    private val epg: EpgRepository,
    private val downloader: Downloader,
    private val xtream: XtreamClient,
    private val settings: SettingsRepository,
    private val backup: BackupRepository
) {
    val sources: Flow<List<SourceEntity>> = db.sources().observeAll()
    private val lock = Mutex()
    private val _busy = MutableStateFlow<Map<Long, String>>(emptyMap())
    /** sourceId -> progress text for sources currently refreshing. */
    val busy: StateFlow<Map<Long, String>> = _busy

    private val storeDir: File get() = File(ctx.filesDir, "sources").also { it.mkdirs() }

    suspend fun get(id: Long) = db.sources().byId(id)
    suspend fun secretsOf(id: Long): SourceSecrets? = db.sources().byId(id)?.let { secrets.open(it.secretCipher) }
    suspend fun history(id: Long): List<ImportJobEntity> = db.imports().recent(id)

    suspend fun xtreamConfigOf(sourceId: Long): XtreamConfig? {
        val s = secretsOf(sourceId) ?: return null
        val base = s.url ?: return null
        return XtreamConfig(base, s.username.orEmpty(), s.password.orEmpty(), s.liveExt ?: "m3u8", s.userAgent)
    }

    suspend fun create(name: String, type: SourceType, sec: SourceSecrets, refreshHours: Int, localFile: File? = null): Long {
        val host = sec.url?.let { Redactor.url(it).substringAfter("://").substringBefore("/") }
        val id = db.sources().insert(SourceEntity(name = name.trim().ifBlank { type.label }, type = type.name, secretCipher = secrets.seal(sec), displayHost = host,
            refreshHours = refreshHours, sortOrder = db.sources().nextOrder()))
        if (localFile != null) {
            val dest = File(storeDir, "$id.dat"); localFile.copyTo(dest, overwrite = true)
            db.sources().byId(id)?.let { db.sources().update(it.copy(localPath = dest.absolutePath)) }
        }
        return id
    }

    suspend fun updateSecrets(id: Long, sec: SourceSecrets) {
        val s = db.sources().byId(id) ?: return
        val host = sec.url?.let { Redactor.url(it).substringAfter("://").substringBefore("/") }
        db.sources().update(s.copy(secretCipher = secrets.seal(sec), displayHost = host))
    }

    suspend fun replaceLocalFile(id: Long, file: File) {
        val s = db.sources().byId(id) ?: return
        val dest = File(storeDir, "$id.dat"); file.copyTo(dest, overwrite = true)
        db.sources().update(s.copy(localPath = dest.absolutePath))
    }

    suspend fun rename(id: Long, name: String) = db.sources().byId(id)?.let { db.sources().update(it.copy(name = name.trim().ifBlank { it.name })) }
    suspend fun setEnabled(id: Long, enabled: Boolean) = db.sources().byId(id)?.let { db.sources().update(it.copy(enabled = enabled)) }
    suspend fun setRefreshHours(id: Long, hours: Int) = db.sources().byId(id)?.let { db.sources().update(it.copy(refreshHours = hours)) }
    suspend fun setActive(id: Long) = settings.set(SettingKeys.ACTIVE_SOURCE, id)

    suspend fun move(id: Long, delta: Int) = db.withTransaction {
        val all = db.sources().all().toMutableList()
        val i = all.indexOfFirst { it.id == id }; val j = i + delta
        if (i < 0 || j !in all.indices) return@withTransaction
        val a = all[i]; all[i] = all[j]; all[j] = a
        all.forEachIndexed { idx, s -> if (s.sortOrder != idx) db.sources().update(s.copy(sortOrder = idx)) }
    }

    /** Removes the source and its catalog. [purgeUserData] also deletes favourites and progress that point at its items. */
    suspend fun remove(id: Long, purgeUserData: Boolean) {
        val s = db.sources().byId(id) ?: return
        db.withTransaction {
            if (purgeUserData) { db.user().deleteFavoritesOfSource(id); db.user().deleteProgressOfSource(id); db.playlists().deleteItemsOfSource(id) }
            db.channels().deleteBySource(id); db.vod().deleteMoviesBySource(id); db.vod().deleteSeriesBySource(id); db.vod().deleteOrphanEpisodes()
            db.epg().deleteProgramsBySource(id); db.epg().deleteEpgChannelsBySource(id); db.imports().deleteBySource(id); db.imports().deleteOverridesBySource(id)
            db.sources().delete(id)
        }
        s.localPath?.let { File(it).delete() }
        epg.clearIndexCache()
        if (settings.current().activeSourceId == id) settings.set(SettingKeys.ACTIVE_SOURCE, -1L)
        DiagnosticsLog.add("sources", "Removed source #$id")
    }

    // ---------------------------------------------------------------- refresh

    suspend fun refresh(id: Long): Result<ImportSummary?> = lock.withLock {
        val s = db.sources().byId(id) ?: return Result.failure(AppException(ErrorKind.INPUT, "Source not found."))
        fun progress(t: String) = _busy.update { it + (id to t) }
        progress("Starting")
        try {
            val sec = secrets.open(s.secretCipher)
            val summary: ImportSummary? = when (SourceType.valueOf(s.type)) {
                SourceType.M3U_URL -> {
                    val url = sec?.url ?: throw AppException(ErrorKind.INPUT, "This source has no address. Edit it to add one.")
                    val tmp = imports.newCacheFile("m3u")
                    try {
                        progress("Downloading"); downloader.download(url, tmp, userAgent = sec.userAgent) { progress("Downloading ${it / 1024} KB") }
                        imports.commitM3u(id, tmp, url, ::progress)
                    } finally { tmp.delete() }
                }
                SourceType.M3U_FILE -> {
                    val f = s.localPath?.let(::File)?.takeIf { it.exists() } ?: throw AppException(ErrorKind.STORAGE, "The stored playlist file is missing. Edit the source and choose the file again.")
                    imports.commitM3u(id, f, null, ::progress)
                }
                SourceType.XTREAM -> {
                    val cfg = xtreamConfigOf(id) ?: throw AppException(ErrorKind.INPUT, "Credentials are missing. Edit the source.")
                    val r = imports.importXtream(id, cfg, ::progress)
                    runCatching { // Provider guide is optional; failures never fail the catalog refresh.
                        val tmp = imports.newCacheFile("xmltv")
                        try { progress("Loading guide"); downloader.download(xtream.epgUrl(cfg), tmp, userAgent = cfg.userAgent); epg.importXmltv(id, tmp, sec?.epgOffsetMin ?: 0) } finally { tmp.delete() }
                    }.onFailure { DiagnosticsLog.add("epg", "Provider guide unavailable: ${(it as? AppException)?.userMessage ?: it.javaClass.simpleName}") }
                    r
                }
                SourceType.XMLTV_URL -> {
                    val url = sec?.url ?: throw AppException(ErrorKind.INPUT, "This source has no address. Edit it to add one.")
                    val tmp = imports.newCacheFile("xmltv")
                    try { progress("Downloading"); downloader.download(url, tmp, userAgent = sec.userAgent); progress("Importing guide"); epg.importXmltv(id, tmp, sec.epgOffsetMin ?: 0) { progress("Imported $it programmes") } } finally { tmp.delete() }
                    null
                }
                SourceType.XMLTV_FILE -> {
                    val f = s.localPath?.let(::File)?.takeIf { it.exists() } ?: throw AppException(ErrorKind.STORAGE, "The stored guide file is missing. Edit the source and choose the file again.")
                    epg.importXmltv(id, f, sec?.epgOffsetMin ?: 0) { progress("Imported $it programmes") }; null
                }
                SourceType.LOCAL_MEDIA -> null
            }
            val fresh = db.sources().byId(id) ?: return Result.success(summary)
            db.sources().update(fresh.copy(lastRefreshAt = System.currentTimeMillis(), lastStatus = "OK", lastError = null,
                channelCount = db.channels().countBySource(id), vodCount = db.vod().countMovies(id), seriesCount = db.vod().countSeries(id), programCount = db.epg().programCount(id)))
            epg.clearIndexCache()
            runCatching { backup.applyPending() }   // re-attach favourites/history restored from a backup
            Result.success(summary)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (t: Throwable) {
            val msg = (t as? AppException)?.userMessage ?: "Unexpected error (${t.javaClass.simpleName})"
            db.sources().byId(id)?.let { db.sources().update(it.copy(lastStatus = "FAILED", lastError = msg)) }
            DiagnosticsLog.add("sources", "Refresh failed for #$id: $msg ${(t as? AppException)?.diagnostic.orEmpty()}")
            Result.failure(t)
        } finally { _busy.update { it - id } }
    }

    /** Refreshes enabled URL-based sources whose interval has elapsed. Used by the WorkManager job. */
    suspend fun refreshDue(now: Long = System.currentTimeMillis()): Int {
        var n = 0
        for (s in db.sources().all()) {
            val type = SourceType.valueOf(s.type)
            if (!s.enabled || s.refreshHours <= 0 || type !in listOf(SourceType.M3U_URL, SourceType.XTREAM, SourceType.XMLTV_URL)) continue
            if (now - (s.lastRefreshAt ?: 0) >= s.refreshHours * 3_600_000L) { refresh(s.id); n++ }
        }
        epg.prune()
        return n
    }

    /** Commits a file that was already downloaded/copied and previewed in the wizard (avoids downloading twice). */
    suspend fun finishStaged(id: Long, type: SourceType, file: File, baseUrl: String?, onProgress: (String) -> Unit = {}): Result<ImportSummary?> = lock.withLock {
        try {
            val summary = when (type) {
                SourceType.M3U_URL, SourceType.M3U_FILE -> imports.commitM3u(id, file, baseUrl, onProgress)
                SourceType.XMLTV_URL, SourceType.XMLTV_FILE -> { epg.importXmltv(id, file, secretsOf(id)?.epgOffsetMin ?: 0) { onProgress("Imported $it programmes") }; null }
                else -> null
            }
            db.sources().byId(id)?.let { f -> db.sources().update(f.copy(lastRefreshAt = System.currentTimeMillis(), lastStatus = "OK", lastError = null,
                channelCount = db.channels().countBySource(id), vodCount = db.vod().countMovies(id), seriesCount = db.vod().countSeries(id), programCount = db.epg().programCount(id))) }
            epg.clearIndexCache()
            runCatching { backup.applyPending() }
            Result.success(summary)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (t: Throwable) {
            val msg = (t as? AppException)?.userMessage ?: "Unexpected error (${t.javaClass.simpleName})"
            db.sources().byId(id)?.let { db.sources().update(it.copy(lastStatus = "FAILED", lastError = msg)) }
            Result.failure(t)
        }
    }

    suspend fun categoryTypes(id: Long): List<CategoryType> {
        val ov = db.imports().overrides(id).associate { it.groupNorm to it.kind }
        fun map(l: List<com.iptv.firetv.core.database.GroupCount>, origin: String) = l.map { CategoryType(it.norm, it.name, it.count, origin, ov[it.norm]) }
        return (map(db.channels().groupsOfSource(id), "LIVE") + map(db.vod().movieGroupsOfSource(id), "MOVIE") + map(db.vod().seriesGroupsOfSource(id), "SERIES")).sortedBy { it.name.lowercase() }
    }

    /** kind = LIVE, MOVIE, SERIES, or null to return to automatic. Takes effect on the next refresh. */
    suspend fun setCategoryType(id: Long, norm: String, name: String, kind: String?) {
        if (kind == null) db.imports().clearOverride(id, norm) else db.imports().setOverride(com.iptv.firetv.core.database.GroupOverrideEntity(id, norm, name, kind))
    }

    suspend fun enabledSources() = db.sources().all()

    // ---------------------------------------------------------------- local media & sample

    suspend fun addLocalMedia(uri: Uri, title: String) {
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val src = db.sources().firstOfType(SourceType.LOCAL_MEDIA.name)
            ?: SourceEntity(name = "Local media", type = SourceType.LOCAL_MEDIA.name, sortOrder = db.sources().nextOrder()).let { db.sources().byId(db.sources().insert(it))!! }
        val t = TextUtil.normalize(title); val now = System.currentTimeMillis()
        db.vod().upsertMovies(listOf(VodItemEntity(TextUtil.sha256Hex("local|${src.id}|$uri"), src.id, title, t, "Local media", TextUtil.normalize("Local media"), null,
            streamRef = uri.toString(), adult = false, addedAt = now, updatedAt = now)))
        db.sources().update(src.copy(vodCount = db.vod().countMovies(src.id), lastRefreshAt = now, lastStatus = "OK"))
    }


    /** Explicit demo mode: clearly labelled, fictitious channels with non-functional addresses plus a synthetic guide. */
    suspend fun addSample(): Long {
        val playlist = ctx.assets.open("sample/sample_playlist.m3u").use { it.readBytes() }
        val tmp = imports.newCacheFile("sample").also { it.writeBytes(playlist) }
        val id = create("Sample (fictitious demo)", SourceType.M3U_FILE, SourceSecrets(), 0, tmp)
        tmp.delete()
        refresh(id)
        val guide = imports.newCacheFile("sample-epg").also { it.writeText(SampleData.xmltv(System.currentTimeMillis())) }
        val gid = create("Sample guide (synthetic)", SourceType.XMLTV_FILE, SourceSecrets(), 0, guide)
        guide.delete()
        refresh(gid)
        return id
    }
}

/** One playlist group with its current content type and the user's override (null = automatic). */
data class CategoryType(val norm: String, val name: String, val count: Int, val origin: String, val override: String?)

object SampleData {
    private val fmt = java.text.SimpleDateFormat("yyyyMMddHHmmss Z", java.util.Locale.US)
    /** Generates a 3-day synthetic guide around [now] for the sample channels (ids sample.one .. sample.six). */
    fun xmltv(now: Long): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv generator-info-name=\"KVOD TV sample\">\n")
        val ids = listOf("sample.one" to "Sample One", "sample.two" to "Sample Two", "sample.news" to "Sample News", "sample.sports" to "Sample Sports", "sample.kids" to "Sample Kids", "sample.music" to "Sample Music")
        ids.forEach { (id, n) -> sb.append("<channel id=\"$id\"><display-name>$n</display-name></channel>\n") }
        val hour = 3_600_000L
        val start0 = (now / hour) * hour - 12 * hour
        val titles = listOf("Morning Show", "Fictional Drama", "Test Pattern Hour", "Sample Documentary", "Imaginary News", "Demo Matchday", "Cartoon Placeholder", "Synthetic Concert")
        ids.forEachIndexed { ci, (id, _) ->
            var t = start0 + ci * 20 * 60_000L
            var k = ci
            while (t < now + 60 * hour) {
                val len = (if ((k + ci) % 3 == 0) 30L else if ((k + ci) % 3 == 1) 60L else 90L) * 60_000L
                val title = titles[k % titles.size]
                sb.append("<programme start=\"${fmt.format(java.util.Date(t))}\" stop=\"${fmt.format(java.util.Date(t + len))}\" channel=\"$id\">")
                sb.append("<title lang=\"en\">$title</title><desc lang=\"en\">Synthetic programme #$k for demonstration only.</desc><category>Demo</category></programme>\n")
                t += len; k++
            }
        }
        return sb.append("</tv>\n").toString()
    }
}
