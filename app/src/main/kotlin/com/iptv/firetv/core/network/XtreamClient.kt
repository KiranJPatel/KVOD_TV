package com.iptv.firetv.core.network

import com.iptv.firetv.core.common.AppException
import com.iptv.firetv.core.common.ErrorKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class XtreamConfig(val baseUrl: String, val username: String, val password: String, val liveExt: String = "m3u8", val userAgent: String? = null)
data class XtreamAccount(val authorized: Boolean, val status: String?, val expiresAt: Long?, val maxConnections: String?)

fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

/**
 * Xtream Codes-compatible API client. The URL builder is deliberately configurable: the base URL may or may not include
 * `player_api.php`, and the live container extension can be chosen per source. Credentials only ever appear in request
 * URLs that are passed to [Downloader]; they are never logged or shown (see Redactor).
 */
@Singleton
class XtreamClient @Inject constructor(private val downloader: Downloader) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun normalizeBase(raw: String): String? {
        val ok = UrlValidator.validate(raw) as? UrlCheck.Ok ?: return null
        var s = ok.url.toString().substringBefore('?')
        for (suffix in listOf("/player_api.php", "/get.php", "/panel_api.php", "/xmltv.php")) s = s.removeSuffix(suffix)
        return s.trimEnd('/')
    }

    private fun api(cfg: XtreamConfig, action: String?, extra: Map<String, String> = emptyMap()): String {
        val b = "${cfg.baseUrl.trimEnd('/')}/player_api.php".toHttpUrlOrNull()?.newBuilder()
            ?: throw AppException(ErrorKind.INPUT, "The server address is not valid.")
        b.addQueryParameter("username", cfg.username).addQueryParameter("password", cfg.password)
        if (action != null) b.addQueryParameter("action", action)
        extra.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build().toString()
    }

    private suspend fun fetch(cfg: XtreamConfig, action: String?, extra: Map<String, String> = emptyMap()): JsonElement {
        val text = downloader.getText(api(cfg, action, extra), cfg.userAgent)
        return try { json.parseToJsonElement(text) }
        catch (e: Exception) { throw AppException(ErrorKind.PLAYLIST, "The server did not return Xtream API data. Check the address and that the provider supports this API.", e) }
    }

    suspend fun account(cfg: XtreamConfig): XtreamAccount {
        val root = fetch(cfg, null) as? JsonObject ?: throw AppException(ErrorKind.PLAYLIST, "Unexpected response from the server.")
        val ui = root.obj("user_info") ?: throw AppException(ErrorKind.AUTH, "The server did not accept these credentials.")
        val auth = ui.str("auth")
        if (auth == "0" || auth.equals("false", true)) throw AppException(ErrorKind.AUTH, "The username or password was rejected by the server.")
        // Expiry and status are shown only when the provider returns them.
        return XtreamAccount(true, ui.str("status"), ui.str("exp_date")?.toLongOrNull()?.times(1000), ui.str("max_connections"))
    }

    suspend fun array(cfg: XtreamConfig, action: String, extra: Map<String, String> = emptyMap()): List<JsonObject> {
        val e = fetch(cfg, action, extra)
        return (e as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
    }

    suspend fun categories(cfg: XtreamConfig, action: String): Map<String, String> =
        array(cfg, action).mapNotNull { o -> o.str("category_id")?.let { it to (o.str("category_name") ?: "Uncategorized") } }.toMap()

    suspend fun seriesInfo(cfg: XtreamConfig, seriesId: String): JsonObject =
        fetch(cfg, "get_series_info", mapOf("series_id" to seriesId)) as? JsonObject ?: JsonObject(emptyMap())

    suspend fun vodInfo(cfg: XtreamConfig, vodId: String): JsonObject =
        fetch(cfg, "get_vod_info", mapOf("vod_id" to vodId)) as? JsonObject ?: JsonObject(emptyMap())

    /** ref formats: xtream:live:<id>, xtream:movie:<id>:<ext>, xtream:series:<id>:<ext>. */
    fun streamUrl(cfg: XtreamConfig, ref: String): String {
        val p = ref.split(":")
        val base = cfg.baseUrl.trimEnd('/')
        fun seg(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        val u = seg(cfg.username); val pw = seg(cfg.password)
        return when (p.getOrNull(1)) {
            "live" -> "$base/live/$u/$pw/${p[2]}.${cfg.liveExt}"
            "movie" -> "$base/movie/$u/$pw/${p[2]}.${p.getOrElse(3) { "mp4" }}"
            "series" -> "$base/series/$u/$pw/${p[2]}.${p.getOrElse(3) { "mp4" }}"
            else -> throw AppException(ErrorKind.INPUT, "Unknown stream reference.")
        }
    }

    fun epgUrl(cfg: XtreamConfig): String {
        val b = "${cfg.baseUrl.trimEnd('/')}/xmltv.php".toHttpUrlOrNull()!!.newBuilder()
        return b.addQueryParameter("username", cfg.username).addQueryParameter("password", cfg.password).build().toString()
    }
}
