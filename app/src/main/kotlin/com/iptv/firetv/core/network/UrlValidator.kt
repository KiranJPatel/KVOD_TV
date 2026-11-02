package com.iptv.firetv.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed class UrlCheck {
    data class Ok(val url: HttpUrl, val insecure: Boolean) : UrlCheck()
    data class Invalid(val reason: String) : UrlCheck()
}

object UrlValidator {
    /** Accepts only http/https with a host. Plain HTTP is allowed but flagged so the UI can warn. */
    fun validate(raw: String): UrlCheck {
        val s = raw.trim()
        if (s.isEmpty()) return UrlCheck.Invalid("Enter a URL.")
        if (s.any { it.isWhitespace() }) return UrlCheck.Invalid("The URL must not contain spaces.")
        val withScheme = if (s.contains("://")) s else "https://$s"
        val url = withScheme.toHttpUrlOrNull() ?: return UrlCheck.Invalid("This is not a valid web address.")
        if (url.scheme != "http" && url.scheme != "https") return UrlCheck.Invalid("Only http and https addresses are supported.")
        if (url.host.isBlank()) return UrlCheck.Invalid("The address has no host name.")
        return UrlCheck.Ok(url, insecure = url.scheme == "http")
    }

    fun looksLikePlaylistFile(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".m3u") || n.endsWith(".m3u8") || n.endsWith(".txt")
    }

    fun looksLikeEpgFile(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".xml") || n.endsWith(".xmltv") || n.endsWith(".gz")
    }
}
