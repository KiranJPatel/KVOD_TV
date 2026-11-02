package com.iptv.firetv.core.common

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

object TextUtil {
    private val combiningLatin = Regex("[\\u0300-\\u036f]+")

    /**
     * Unicode-aware search/identity key: NFKD, strips Latin diacritics, lower-cases, keeps letters/digits and the
     * combining marks of non-Latin scripts (so Devanagari/Tamil vowel signs survive), drops everything else.
     */
    fun normalize(input: String): String {
        val d = Normalizer.normalize(input, Normalizer.Form.NFKD).replace(combiningLatin, "")
        val sb = StringBuilder(d.length)
        for (ch in d.lowercase(Locale.ROOT)) {
            if (ch.isLetterOrDigit()) sb.append(ch)
            else when (Character.getType(ch)) {
                Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt() -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun sha256Hex(s: String, chars: Int = 24): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(chars)

    private val adultRegex = Regex("(?i)(^|[^a-z])(xxx|adults?|porn|erotic|18\\+|hentai)([^a-z]|$)")
    fun looksAdult(name: String, group: String?): Boolean =
        adultRegex.containsMatchIn(name) || (group != null && adultRegex.containsMatchIn(group))
}
