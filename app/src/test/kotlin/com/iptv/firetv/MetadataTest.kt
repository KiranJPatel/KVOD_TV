package com.iptv.firetv

import com.iptv.firetv.data.MetadataQuery
import com.iptv.firetv.data.TmdbParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataTest {
    @Test fun titlesAreCleanedForLookup() {
        assertEquals("Fictional Movie One" to "2020", MetadataQuery.clean("Fictional Movie One (2020)"))
        assertEquals("Some Film" to "2019", MetadataQuery.clean("Some.Film.2019.1080p.BluRay.x264"))
        assertEquals("Movie" to null, MetadataQuery.clean("[EN] Movie - 4K"))
        assertEquals("2012" to null, MetadataQuery.clean("2012"))
        assertEquals("Show" to null, MetadataQuery.clean("Show (HD)"))
    }

    private fun obj(s: String) = Json.parseToJsonElement(s) as JsonObject

    @Test fun onlyExactTitleMatchesAreAcceptedAndFieldsAreMapped() {
        val r = obj("""{"results":[{"title":"Other","overview":"x"},{"title":"Fictional Movie One","overview":"Plot","poster_path":"/p.jpg","backdrop_path":"/b.jpg","release_date":"2020-05-01","vote_average":7.46}]}""")
        val hit = TmdbParser.pick(r, "fictional movie one", listOf("title", "original_title"), "release_date")!!
        assertEquals("Plot", hit.plot); assertEquals("https://image.tmdb.org/t/p/w500/p.jpg", hit.poster); assertEquals("https://image.tmdb.org/t/p/w780/b.jpg", hit.backdrop)
        assertEquals("2020", hit.year); assertEquals("7.5", hit.rating)
    }

    @Test fun nearMissesAndEmptyResultsReturnNothing() {
        assertNull(TmdbParser.pick(obj("""{"results":[{"title":"Fictional Movie Two"}]}"""), "Fictional Movie One", listOf("title"), "release_date"))
        assertNull(TmdbParser.pick(obj("""{"results":[]}"""), "x", listOf("title"), "release_date"))
        assertNull(TmdbParser.pick(obj("""{"status_code":7}"""), "x", listOf("title"), "release_date"))
    }
}
