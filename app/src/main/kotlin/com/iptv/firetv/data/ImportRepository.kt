package com.iptv.firetv.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.iptv.firetv.core.common.AppException
import com.iptv.firetv.core.common.DiagnosticsLog
import com.iptv.firetv.core.common.ErrorKind
import com.iptv.firetv.core.common.ChannelNaming
import com.iptv.firetv.core.common.TextUtil
import com.iptv.firetv.core.database.AppDatabase
import com.iptv.firetv.core.database.ChannelEntity
import com.iptv.firetv.core.database.EpisodeEntity
import com.iptv.firetv.core.database.ImportJobEntity
import com.iptv.firetv.core.database.SeriesEntity
import com.iptv.firetv.core.database.VodItemEntity
import com.iptv.firetv.core.model.ContentKind
import com.iptv.firetv.core.model.ParseIssue
import com.iptv.firetv.core.model.ParseStats
import com.iptv.firetv.core.model.ParsedEntry
import com.iptv.firetv.core.network.BoundedInputStream
import com.iptv.firetv.core.network.XtreamClient
import com.iptv.firetv.core.network.XtreamConfig
import com.iptv.firetv.core.network.obj
import com.iptv.firetv.core.network.str
import com.iptv.firetv.core.parser.EntryClassifier
import com.iptv.firetv.core.parser.M3uParser
import com.iptv.firetv.core.parser.XmltvItem
import com.iptv.firetv.core.parser.XmltvParser
import android.util.Xml
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class StagedFile(val file: File, val displayName: String, val sizeBytes: Long)

data class ImportPreview(
    val fileName: String, val sizeBytes: Long, val stats: ParseStats, val sample: List<ParsedEntry>,
    val issues: List<ParseIssue>, val duplicates: Int
)

data class XmltvPreview(val fileName: String, val sizeBytes: Long, val channels: Int, val programmes: Int, val skipped: Int, val error: String?)

data class ImportSummary(
    val channels: Int = 0, val movies: Int = 0, val series: Int = 0, val episodes: Int = 0,
    val duplicates: Int = 0, val invalid: Int = 0, val epgUrls: List<String> = emptyList()
) { val total get() = channels + movies + series }

/** Stable-identity helpers. Ids never depend on list order or stream URL, so favourites and history survive refreshes. */
object Ids {
    fun channel(sourceId: Long, tvgId: String?, nameNorm: String, groupNorm: String, ordinal: Int) =
        TextUtil.sha256Hex("c|$sourceId|${tvgId.orEmpty()}|$nameNorm|$groupNorm|$ordinal")
    fun movie(sourceId: Long, titleNorm: String, groupNorm: String, ordinal: Int) = TextUtil.sha256Hex("m|$sourceId|$titleNorm|$groupNorm|$ordinal")
    fun series(sourceId: Long, titleNorm: String) = TextUtil.sha256Hex("s|$sourceId|$titleNorm")
    fun episode(seriesId: String, season: Int, ep: Int, ordinal: Int) = TextUtil.sha256Hex("e|$seriesId|$season|$ep|$ordinal")
    fun xtream(kind: String, sourceId: Long, remoteId: String) = TextUtil.sha256Hex("x|$kind|$sourceId|$remoteId")
}

@Singleton
class ImportRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: AppDatabase,
    private val xtream: XtreamClient,
    private val epg: EpgRepository
) {
    private val json = Json { encodeDefaults = false }
    private val m3u = M3uParser()
    private val xmltv = XmltvParser { Xml.newPullParser() }

    val cacheDir: File get() = File(ctx.cacheDir, "imports").also { it.mkdirs() }

    /** Copies a Storage Access Framework document into app-private cache (bounded). No storage permission is needed. */
    suspend fun stageUri(uri: Uri, maxBytes: Long = 150L * 1024 * 1024, onBytes: (Long) -> Unit = {}): StagedFile = withContext(Dispatchers.IO) {
        var name = "playlist"; var size = -1L
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) { c.getString(0)?.let { name = it }; if (!c.isNull(1)) size = c.getLong(1) }
            }
        }
        if (size > maxBytes) throw AppException(ErrorKind.TOO_LARGE, "The file is larger than ${maxBytes / (1024 * 1024)} MB.")
        val dest = File(cacheDir, "staged-${System.nanoTime()}.dat")
        try {
            val ins = ctx.contentResolver.openInputStream(uri) ?: throw AppException(ErrorKind.STORAGE, "The file could not be opened. Pick it again.")
            var total = 0L
            BoundedInputStream(ins, maxBytes).use { src ->
                dest.outputStream().buffered().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) { coroutineContext.ensureActive(); val n = src.read(buf); if (n < 0) break; out.write(buf, 0, n); total += n; onBytes(total) }
                }
            }
            StagedFile(dest, name, total)
        } catch (e: AppException) { dest.delete(); throw e
        } catch (e: java.io.IOException) { dest.delete(); throw AppException(ErrorKind.STORAGE, "Reading the file failed.", e)
        } catch (e: SecurityException) { dest.delete(); throw AppException(ErrorKind.STORAGE, "Access to the file was denied. Pick it again.", e) }
    }

    fun newCacheFile(prefix: String) = File(cacheDir, "$prefix-${System.nanoTime()}.dat")

    // ---------------------------------------------------------------- M3U

    suspend fun previewM3u(staged: StagedFile, baseUrl: String? = null): ImportPreview = withContext(Dispatchers.IO) {
        val session = M3uParser.Session()
        val sample = ArrayList<ParsedEntry>(8)
        val seen = HashSet<String>(); var dup = 0
        staged.file.inputStream().buffered().use { ins ->
            for (e in m3u.parse(ins, session, baseUrl)) {
                coroutineContext.ensureActive()
                if (sample.size < 8) sample.add(e)
                val key = "${e.tvgId.orEmpty()}|${TextUtil.normalize(e.name)}|${TextUtil.normalize(e.group.orEmpty())}|${e.url}"
                if (!seen.add(key)) dup++
            }
        }
        if (session.stats.entries == 0) throw AppException(ErrorKind.PLAYLIST,
            "No playable entries were found. This does not look like an M3U playlist" + (session.issues.firstOrNull()?.let { " (${it.message})" } ?: "."))
        ImportPreview(staged.displayName, staged.sizeBytes, session.stats, sample, session.issues.take(30), dup)
    }

    suspend fun commitM3u(sourceId: Long, file: File, baseUrl: String?, onProgress: (String) -> Unit = {}): ImportSummary = withContext(Dispatchers.IO) {
        val stamp = System.currentTimeMillis()
        val jobId = db.imports().insert(ImportJobEntity(sourceId = sourceId, startedAt = stamp, finishedAt = null, status = "RUNNING", total = 0, invalid = 0, duplicates = 0, message = null))
        val session = M3uParser.Session()
        var dups = 0; var channels = 0; var movies = 0; var episodes = 0
        val overrides = db.imports().overrides(sourceId).associate { it.groupNorm to it.kind }
        try {
            db.withTransaction {
                val seen = HashMap<String, Int>(); val exact = HashSet<String>()
                val chBatch = ArrayList<ChannelEntity>(500); val mvBatch = ArrayList<VodItemEntity>(500); val epBatch = ArrayList<EpisodeEntity>(500)
                val seriesMap = LinkedHashMap<String, SeriesEntity>(); val epSeen = HashSet<String>()
                var index = 0
                suspend fun flushMovies() {
                    val times = db.vod().movieTimes(mvBatch.map { it.id }).associate { it.id to it.addedAt }
                    db.vod().upsertMovies(mvBatch.map { m -> times[m.id]?.let { m.copy(addedAt = it) } ?: m }); mvBatch.clear()
                }
                file.inputStream().buffered().use { ins ->
                    for (e in m3u.parse(ins, session, baseUrl)) {
                        coroutineContext.ensureActive()
                        index++
                        val nameNorm = TextUtil.normalize(e.name)
                        val groupName = e.group?.takeIf { it.isNotBlank() } ?: "Uncategorized"
                        val groupNorm = TextUtil.normalize(groupName)
                        val adult = TextUtil.looksAdult(e.name, e.group)
                        val headers = if (e.httpHeaders.isEmpty()) null else json.encodeToString(e.httpHeaders)
                        // Explicit user correction of the content type wins over automatic classification.
                        val kind = overrides[groupNorm]?.let { k -> runCatching { ContentKind.valueOf(k) }.getOrNull() } ?: e.kind
                        val baseKey = "${kind.name}|${e.tvgId.orEmpty()}|$nameNorm|$groupNorm"
                        if (!exact.add("$baseKey|${e.url}")) { dups++; continue }
                        val ordinal = seen.merge(baseKey, 1) { a, b -> a + b }!! - 1
                        when (kind) {
                            ContentKind.LIVE, ContentKind.UNKNOWN -> {
                                val chId = Ids.channel(sourceId, e.tvgId, nameNorm, groupNorm, ordinal)
                                chBatch.add(ChannelEntity(chId, sourceId, e.name, nameNorm, groupName, groupNorm,
                                    e.logo, e.channelNumber, e.tvgId, e.url, headers, adult, index, stamp,
                                    dupKey = ChannelNaming.dupKey(e.name, chId), qualityRank = ChannelNaming.qualityRank(e.name))); channels++
                                if (chBatch.size >= 500) { db.channels().upsertAll(chBatch); chBatch.clear(); onProgress("Saved $channels channels") }
                            }
                            ContentKind.MOVIE -> {
                                mvBatch.add(VodItemEntity(Ids.movie(sourceId, nameNorm, groupNorm, ordinal), sourceId, e.name, nameNorm, groupName, groupNorm, e.logo,
                                    genre = groupName, streamRef = e.url, headersJson = headers, adult = adult, addedAt = stamp - index, updatedAt = stamp)); movies++
                                if (mvBatch.size >= 500) { flushMovies(); onProgress("Saved $movies movies") }
                            }
                            ContentKind.SERIES, ContentKind.EPISODE -> {
                                val info = EntryClassifier.episodeInfo(e.name)
                                val title = info?.first ?: e.name
                                val sNorm = TextUtil.normalize(title)
                                val sid = Ids.series(sourceId, sNorm)
                                seriesMap.getOrPut(sid) { SeriesEntity(sid, sourceId, title, sNorm, groupName, groupNorm, e.logo, genre = groupName, adult = adult, addedAt = stamp - index, updatedAt = stamp) }
                                val season = info?.second ?: 1; val epn = info?.third ?: (ordinal + 1)
                                val eid = Ids.episode(sid, season, epn, ordinal)
                                if (epSeen.add(eid)) {
                                    epBatch.add(EpisodeEntity(eid, sid, season, epn, if (info != null) "Episode $epn" else e.name, null, null, e.logo, e.url, headers, stamp)); episodes++
                                    if (epBatch.size >= 500) { db.vod().upsertEpisodes(epBatch); epBatch.clear() }
                                }
                            }
                        }
                    }
                }
                if (chBatch.isNotEmpty()) db.channels().upsertAll(chBatch)
                if (mvBatch.isNotEmpty()) flushMovies()
                if (seriesMap.isNotEmpty()) {
                    val times = db.vod().seriesTimes(seriesMap.keys.toList()).associate { it.id to it.addedAt }
                    seriesMap.values.chunked(500).forEach { chunk -> db.vod().upsertSeries(chunk.map { s -> times[s.id]?.let { s.copy(addedAt = it) } ?: s }) }
                }
                if (epBatch.isNotEmpty()) db.vod().upsertEpisodes(epBatch)
                // Remove entries the provider no longer lists. Favourites/history are keyed by stable ids and are kept.
                db.channels().deleteStale(sourceId, stamp); db.vod().deleteStaleMovies(sourceId, stamp); db.vod().deleteStaleSeries(sourceId, stamp); db.vod().deleteOrphanEpisodes()
            }
            val summary = ImportSummary(channels, movies, seriesCountOf(sourceId), episodes, dups, session.stats.invalid, session.stats.epgUrls.toList())
            db.imports().update(ImportJobEntity(jobId, sourceId, stamp, System.currentTimeMillis(), "OK", summary.total, summary.invalid, dups, session.issues.firstOrNull()?.message))
            DiagnosticsLog.add("import", "M3U import ok: $channels live, $movies movies, $episodes episodes, $dups duplicates, ${session.stats.invalid} invalid")
            summary
        } catch (t: Throwable) {
            db.imports().update(ImportJobEntity(jobId, sourceId, stamp, System.currentTimeMillis(), "FAILED", 0, session.stats.invalid, dups, t.message?.take(200)))
            throw t
        }
    }

    private suspend fun seriesCountOf(sourceId: Long) = db.vod().countSeries(sourceId)

    // ---------------------------------------------------------------- Xtream

    suspend fun importXtream(sourceId: Long, cfg: XtreamConfig, onProgress: (String) -> Unit = {}): ImportSummary = withContext(Dispatchers.IO) {
        val stamp = System.currentTimeMillis()
        val jobId = db.imports().insert(ImportJobEntity(sourceId = sourceId, startedAt = stamp, finishedAt = null, status = "RUNNING", total = 0, invalid = 0, duplicates = 0, message = null))
        try {
            onProgress("Checking account")
            xtream.account(cfg)
            onProgress("Loading live channels")
            val liveCats = xtream.categories(cfg, "get_live_categories")
            val live = xtream.array(cfg, "get_live_streams")
            onProgress("Loading movies")
            val vodCats = runCatching { xtream.categories(cfg, "get_vod_categories") }.getOrDefault(emptyMap())
            val vod = runCatching { xtream.array(cfg, "get_vod_streams") }.getOrDefault(emptyList())
            onProgress("Loading series")
            val serCats = runCatching { xtream.categories(cfg, "get_series_categories") }.getOrDefault(emptyMap())
            val ser = runCatching { xtream.array(cfg, "get_series") }.getOrDefault(emptyList())
            if (live.isEmpty() && vod.isEmpty() && ser.isEmpty()) throw AppException(ErrorKind.PLAYLIST, "The account returned no channels, movies or series.")

            db.withTransaction {
                live.chunked(500).forEachIndexed { i, chunk ->
                    coroutineContext.ensureActive()
                    db.channels().upsertAll(chunk.mapIndexedNotNull { k, o ->
                        val id = o.str("stream_id") ?: return@mapIndexedNotNull null; val name = o.str("name") ?: return@mapIndexedNotNull null
                        val g = liveCats[o.str("category_id")] ?: "Uncategorized"
                        ChannelEntity(Ids.xtream("live", sourceId, id), sourceId, name, TextUtil.normalize(name), g, TextUtil.normalize(g), o.str("stream_icon"),
                            o.str("num")?.toIntOrNull(), o.str("epg_channel_id"), "xtream:live:$id", null, TextUtil.looksAdult(name, g), i * 500 + k, stamp,
                            dupKey = ChannelNaming.dupKey(name, Ids.xtream("live", sourceId, id)), qualityRank = ChannelNaming.qualityRank(name))
                    })
                }
                vod.chunked(500).forEach { chunk ->
                    coroutineContext.ensureActive()
                    db.vod().upsertMovies(chunk.mapNotNull { o ->
                        val id = o.str("stream_id") ?: return@mapNotNull null; val name = o.str("name") ?: return@mapNotNull null
                        val g = vodCats[o.str("category_id")] ?: "Uncategorized"; val ext = o.str("container_extension") ?: "mp4"
                        val added = (o.str("added")?.toLongOrNull()?.times(1000)) ?: stamp
                        VodItemEntity(Ids.xtream("movie", sourceId, id), sourceId, name, TextUtil.normalize(name), g, TextUtil.normalize(g), o.str("stream_icon"), genre = g,
                            rating = o.str("rating"), year = o.str("year"), streamRef = "xtream:movie:$id:$ext", adult = TextUtil.looksAdult(name, g), addedAt = added, updatedAt = stamp)
                    })
                }
                ser.chunked(500).forEach { chunk ->
                    coroutineContext.ensureActive()
                    db.vod().upsertSeries(chunk.mapNotNull { o ->
                        val id = o.str("series_id") ?: return@mapNotNull null; val name = o.str("name") ?: return@mapNotNull null
                        val g = serCats[o.str("category_id")] ?: "Uncategorized"
                        val added = (o.str("last_modified")?.toLongOrNull()?.times(1000)) ?: stamp
                        SeriesEntity(Ids.xtream("series", sourceId, id), sourceId, name, TextUtil.normalize(name), g, TextUtil.normalize(g), o.str("cover"), o.str("plot"),
                            o.str("year") ?: o.str("releaseDate")?.take(4), o.str("genre") ?: g, o.str("rating"), id, TextUtil.looksAdult(name, g), added, stamp)
                    })
                }
                db.channels().deleteStale(sourceId, stamp); db.vod().deleteStaleMovies(sourceId, stamp); db.vod().deleteStaleSeries(sourceId, stamp); db.vod().deleteOrphanEpisodes()
            }
            val summary = ImportSummary(live.size, vod.size, ser.size, 0)
            db.imports().update(ImportJobEntity(jobId, sourceId, stamp, System.currentTimeMillis(), "OK", summary.total, 0, 0, null))
            DiagnosticsLog.add("import", "Xtream import ok: ${live.size} live, ${vod.size} movies, ${ser.size} series")
            summary
        } catch (t: Throwable) {
            db.imports().update(ImportJobEntity(jobId, sourceId, stamp, System.currentTimeMillis(), "FAILED", 0, 0, 0, (t as? AppException)?.userMessage ?: t.javaClass.simpleName))
            throw t
        }
    }

    /** Loads seasons/episodes for one Xtream series on demand and stores them. */
    suspend fun loadXtreamEpisodes(cfg: XtreamConfig, series: SeriesEntity) = withContext(Dispatchers.IO) {
        val ext = series.externalId ?: return@withContext
        val root = xtream.seriesInfo(cfg, ext)
        val info = root.obj("info")
        val eps = root.obj("episodes") ?: return@withContext
        val out = ArrayList<EpisodeEntity>()
        for ((seasonKey, arr) in eps) {
            val list = arr as? kotlinx.serialization.json.JsonArray ?: continue
            for (el in list) {
                val o = el as? kotlinx.serialization.json.JsonObject ?: continue
                val id = o.str("id") ?: continue
                val season = o.str("season")?.toIntOrNull() ?: seasonKey.toIntOrNull() ?: 1
                val n = o.str("episode_num")?.toIntOrNull() ?: 0
                val inf = o.obj("info")
                out.add(EpisodeEntity(Ids.xtream("episode", series.sourceId, id), series.id, season, n, o.str("title") ?: "Episode $n", inf?.str("plot"),
                    inf?.str("duration_secs")?.toIntOrNull(), inf?.str("movie_image"), "xtream:series:$id:${o.str("container_extension") ?: "mp4"}", null, System.currentTimeMillis()))
            }
        }
        if (out.isNotEmpty()) db.vod().upsertEpisodes(out)
        if (info != null && series.plot == null) db.vod().upsertSeries(listOf(series.copy(plot = info.str("plot"), genre = info.str("genre") ?: series.genre)))
    }

    suspend fun enrichXtreamMovie(cfg: XtreamConfig, movie: VodItemEntity): VodItemEntity = withContext(Dispatchers.IO) {
        if (!movie.streamRef.startsWith("xtream:movie:") || movie.plot != null) return@withContext movie
        val id = movie.streamRef.split(":").getOrNull(2) ?: return@withContext movie
        val info = runCatching { xtream.vodInfo(cfg, id).obj("info") }.getOrNull() ?: return@withContext movie
        val m = movie.copy(plot = info.str("plot") ?: info.str("description"), backdrop = (info["backdrop_path"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content },
            durationSec = info.str("duration_secs")?.toIntOrNull(), genre = info.str("genre") ?: movie.genre, year = info.str("releasedate")?.take(4) ?: movie.year, rating = info.str("rating") ?: movie.rating,
            poster = movie.poster ?: info.str("movie_image"))
        db.vod().upsertMovies(listOf(m)); m
    }

    // ---------------------------------------------------------------- XMLTV

    suspend fun previewXmltv(staged: StagedFile): XmltvPreview = withContext(Dispatchers.IO) {
        val stats = com.iptv.firetv.core.parser.XmltvStats()
        staged.file.inputStream().buffered().use { ins -> for (i in xmltv.parse(ins, epg.zone(), stats)) { coroutineContext.ensureActive() } }
        if (stats.channels == 0 && stats.programmes == 0) throw AppException(ErrorKind.PLAYLIST, "No XMLTV channels or programmes were found" + (stats.error?.let { " ($it)" } ?: "."))
        XmltvPreview(staged.displayName, staged.sizeBytes, stats.channels, stats.programmes, stats.skipped, stats.error)
    }
}
