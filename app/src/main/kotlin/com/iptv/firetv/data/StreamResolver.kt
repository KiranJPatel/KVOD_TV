package com.iptv.firetv.data

import com.iptv.firetv.core.common.AppException
import com.iptv.firetv.core.common.ErrorKind
import com.iptv.firetv.core.network.XtreamClient
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedStream(val url: String, val headers: Map<String, String>, val drmLicenseUrl: String? = null)

/** Turns a stored stream reference into a playable URL at play time, so Xtream credentials are never copied per item. */
@Singleton
class StreamResolver @Inject constructor(private val sources: SourceRepository, private val xtream: XtreamClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun resolve(sourceId: Long, ref: String, headersJson: String?): ResolvedStream {
        val headers = headersJson?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }.orEmpty().toMutableMap()
        val sec = sources.secretsOf(sourceId)
        if (!headers.containsKey("User-Agent") && !sec?.userAgent.isNullOrBlank()) headers["User-Agent"] = sec!!.userAgent!!
        val (plainHeaders, drm) = com.iptv.firetv.core.model.DrmHeaders.split(headers)
        if (ref.startsWith("xtream:")) {
            val cfg = sources.xtreamConfigOf(sourceId) ?: throw AppException(ErrorKind.AUTH, "The source credentials are missing.")
            return ResolvedStream(xtream.streamUrl(cfg, ref), plainHeaders, drm)
        }
        // Multicast lists write udp://@239.0.0.1:1234; the "@" is a convention, not user info, and confuses URI parsing.
        val url = if (ref.startsWith("udp://@", true)) "udp://" + ref.substring(7) else ref
        return ResolvedStream(url, plainHeaders, drm)
    }
}
