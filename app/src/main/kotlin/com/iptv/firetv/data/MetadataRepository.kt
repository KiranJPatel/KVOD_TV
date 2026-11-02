package com.iptv.firetv.data

import com.iptv.firetv.core.common.AppException
import com.iptv.firetv.core.common.DiagnosticsLog
import com.iptv.firetv.core.common.TextUtil
import com.iptv.firetv.core.database.AppDatabase
import com.iptv.firetv.core.database.SeriesEntity
import com.iptv.firetv.core.database.VodItemEntity
import com.iptv.firetv.core.network.Downloader
import com.iptv.firetv.core.network.obj
import com.iptv.firetv.core.network.str
import com.iptv.firetv.core.security.SecretCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

data class MetadataHit(val plot: String?, val poster: String?, val backdrop: String?, val year: String?, val rating: String?)

/** Turns a playlist title into a lookup query: strips years, brackets, quality/codec tags and separators. */
object MetadataQuery {
    private val bracketed = Regex("""[\[(][^\])]*[\])]""")
    private val parenYear = Regex("""[\[(]((?:19|20)\d{2})[\])]""")
    private val bareYear = Regex("""\b((?:19|20)\d{2})\b""")
    private val tags = Regex("""(?i)\b(2160p|1080p|720p|480p|4k|uhd|hdr|bluray|blu-ray|brrip|webrip|web-dl|hdtv|x264|x265|h264|h265|hevc|dubbed|multi)\b""")

    fun clean(raw: String): Pair<String, String?> {
        var t = raw
        var year = parenYear.find(t)?.groupValues?.get(1)
        t = t.replace(bracketed, " ")
        if (year == null) bareYear.findAll(t).lastOrNull()?.let { m -> if (t.trim() != m.value) { year = m.value; t = t.removeRange(m.range) } }
        t = tags.replace(t, " ").replace(Regex("""[._]+"""), " ").replace(Regex("""\s+"""), " ").trim(' ', '-')
        return t to year
    }
}

/** Picks a result only when its title matches the query exactly (after normalisation); it never guesses. */
object TmdbParser {
    fun pick(root: JsonObject, wanted: String, titleKeys: List<String>, dateKey: String): MetadataHit? {
        val w = TextUtil.normalize(wanted)
        val hit = (root["results"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.firstOrNull { o -> titleKeys.any { k -> o.str(k)?.let { TextUtil.normalize(it) == w } == true } } ?: return null
        val rating = hit.str("vote_average")?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "%.1f".format(java.util.Locale.US, it) }
        return MetadataHit(hit.str("overview"), hit.str("poster_path")?.let { "https://image.tmdb.org/t/p/w500$it" },
            hit.str("backdrop_path")?.let { "https://image.tmdb.org/t/p/w780$it" }, hit.str(dateKey)?.take(4), rating)
    }
}

/**
 * Optional, opt-in artwork/description lookup (TMDB). The user supplies their own API key, which is stored encrypted;
 * nothing is bundled. Only the cleaned title (and year) of items that lack details is sent, results are cached in the
 * catalogue, requests are throttled to ~4/s, and failures are silent. TMDB attribution is shown wherever the data appears.
 */
@Singleton
class MetadataRepository @Inject constructor(
    private val settings: SettingsRepository, private val secrets: SecretCodec, private val downloader: Downloader, private val db: AppDatabase
) {
    /** Overridable for tests. */
    var baseUrl: HttpUrl = "https://api.themoviedb.org/3/".toHttpUrl()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val lock = Mutex(); private var lastCall = 0L
    private val attempted = HashSet<String>()

    suspend fun hasKey() = settings.metadataKeyCipher() != null
    suspend fun isReady() = settings.current().metadataEnabled && hasKey()
    suspend fun saveKey(plain: String?) { settings.setMetadataKeyCipher(plain?.trim()?.takeIf { it.isNotEmpty() }?.let { secrets.encrypt(it) }) }

    private suspend fun search(path: String, query: String, year: String?, yearParam: String): JsonObject? {
        val key = settings.metadataKeyCipher()?.let { runCatching { secrets.decrypt(it) }.getOrNull() } ?: return null
        return lock.withLock {
            val wait = 260 - (System.currentTimeMillis() - lastCall); if (wait > 0) delay(wait)
            lastCall = System.currentTimeMillis()
            try {
                val url = baseUrl.newBuilder().addPathSegments(path).addQueryParameter("query", query).addQueryParameter("include_adult", "false")
                    .apply { if (year != null) addQueryParameter(yearParam, year) }.addQueryParameter("api_key", key).build().toString()
                json.parseToJsonElement(downloader.getText(url, maxBytes = 2L * 1024 * 1024)) as? JsonObject
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                DiagnosticsLog.add("metadata", "lookup failed (${(e as? AppException)?.diagnostic ?: e.javaClass.simpleName})"); null   // the URL (and key) are never logged
            }
        }
    }

    suspend fun enrichMovie(m: VodItemEntity): VodItemEntity {
        if ((m.plot != null && m.poster != null && m.backdrop != null) || !attempted.add(m.id) || !isReady()) return m
        val (q, y) = MetadataQuery.clean(m.title)
        if (q.length < 2) return m
        val hit = search("search/movie", q, y, "year")?.let { TmdbParser.pick(it, q, listOf("title", "original_title"), "release_date") } ?: return m
        val u = m.copy(plot = m.plot ?: hit.plot?.ifBlank { null }, poster = m.poster ?: hit.poster, backdrop = m.backdrop ?: hit.backdrop, year = m.year ?: hit.year, rating = m.rating ?: hit.rating)
        if (u != m) db.vod().upsertMovies(listOf(u))
        return u
    }

    suspend fun enrichSeries(s: SeriesEntity): SeriesEntity {
        if ((s.plot != null && s.poster != null) || !attempted.add(s.id) || !isReady()) return s
        val (q, y) = MetadataQuery.clean(s.title)
        if (q.length < 2) return s
        val hit = search("search/tv", q, y, "first_air_date_year")?.let { TmdbParser.pick(it, q, listOf("name", "original_name"), "first_air_date") } ?: return s
        val u = s.copy(plot = s.plot ?: hit.plot?.ifBlank { null }, poster = s.poster ?: hit.poster, year = s.year ?: hit.year, rating = s.rating ?: hit.rating)
        if (u != s) db.vod().upsertSeries(listOf(u))
        return u
    }

    companion object { const val ATTRIBUTION = "This product uses the TMDB API but is not endorsed or certified by TMDB." }
}
