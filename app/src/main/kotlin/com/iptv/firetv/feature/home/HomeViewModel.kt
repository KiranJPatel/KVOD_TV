package com.iptv.firetv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.firetv.app.Routes
import com.iptv.firetv.core.database.ChannelEntity
import com.iptv.firetv.core.database.SeriesEntity
import com.iptv.firetv.core.database.SourceEntity
import com.iptv.firetv.core.database.VodItemEntity
import com.iptv.firetv.core.model.ContentKind
import com.iptv.firetv.core.model.NowNext
import com.iptv.firetv.data.CatalogRepository
import com.iptv.firetv.data.ContinueItem
import com.iptv.firetv.data.EpgRepository
import com.iptv.firetv.data.SourceRepository
import com.iptv.firetv.data.UserDataRepository
import com.iptv.firetv.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HeroItem(
    val title: String, val tag: String, val description: String, val image: String?, val action: String, val route: String,
    val isLive: Boolean = false, val channelName: String? = null, val nowLine: String? = null, val progress: Float? = null,
    val favKind: ContentKind? = null, val favId: String? = null
)

data class HomeState(
    val loading: Boolean = true,
    val hasSources: Boolean = false,
    val live: List<ChannelEntity> = emptyList(),
    val nowNext: Map<String, NowNext> = emptyMap(),
    val continueItems: List<ContinueItem> = emptyList(),
    val recent: List<ChannelEntity> = emptyList(),
    val favorites: List<ChannelEntity> = emptyList(),
    val movies: List<VodItemEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val recentlyAdded: List<Any> = emptyList(),
    val sourcesTotal: Int = 0,
    val sourcesFailed: Int = 0,
    val profileName: String = ""
) {
    val hasContent get() = live.isNotEmpty() || movies.isNotEmpty() || series.isNotEmpty()
}

private data class A(val live: List<ChannelEntity>, val cont: List<ContinueItem>, val recent: List<ChannelEntity>, val fav: List<ChannelEntity>)
private data class Bundle2(val movies: List<VodItemEntity>, val series: List<SeriesEntity>, val sources: List<SourceEntity>, val profile: String)

@HiltViewModel
class HomeViewModel @Inject constructor(
    catalog: CatalogRepository, private val user: UserDataRepository, epg: EpgRepository, profiles: ProfileRepository, private val sources: SourceRepository,
    settingsRepo: com.iptv.firetv.data.SettingsRepository
) : ViewModel() {
    /** (show Movies, show Series): rows for a hidden top-level tab are not shown on Home either. */
    val visibleTabs: StateFlow<Pair<Boolean, Boolean>> = settingsRepo.settings.map { it.showMoviesTab to it.showSeriesTab }.stateIn(viewModelScope, SharingStarted.Eagerly, true to true)

    private val a = combine(catalog.channels(null, "", 40), user.continueWatching(), user.recentChannels(), user.favoriteChannels()) { l, c, r, f -> A(l, c, r, f) }
    private val b = combine(catalog.movies(null, "", 0, 30), catalog.series(null, "", 0, 30), sources.sources, profiles.activeProfile) { m, s, src, p -> Bundle2(m, s, src, p?.name.orEmpty()) }

    val state: StateFlow<HomeState> = combine(a, b) { x, y ->
        val nn = epg.nowNext((x.live + x.fav).distinctBy { it.id })
        val newest = (y.movies.map { it to it.addedAt } + y.series.map { it to it.addedAt }).sortedByDescending { it.second }.take(20).map { it.first as Any }
        val enabled = y.sources.filter { it.enabled }
        HomeState(false, y.sources.isNotEmpty(), x.live, nn, x.cont, x.recent, x.fav, y.movies, y.series, newest,
            enabled.size, enabled.count { it.lastStatus == "FAILED" }, y.profile)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private val _override = MutableStateFlow<HeroItem?>(null)
    val heroOverride: StateFlow<HeroItem?> = _override
    fun focusHero(h: HeroItem) { _override.value = h }

    fun defaultHero(s: HomeState): HeroItem? {
        s.continueItems.firstOrNull()?.let { return HeroItem(it.progress.title, "Continue watching", "Pick up where you left off.", it.progress.poster, "Resume", it.route) }
        s.movies.firstOrNull { !it.plot.isNullOrBlank() }?.let { return heroOf(it) }
        s.movies.firstOrNull()?.let { return heroOf(it) }
        s.live.firstOrNull()?.let { return heroOf(it, s.nowNext[it.id]) }
        return null
    }

    fun heroOf(m: VodItemEntity) = HeroItem(m.title, listOfNotNull("Movie", m.genre, m.year).joinToString(" \u2022 "), m.plot ?: "From ${m.groupName}.", m.backdrop ?: m.poster, "Watch now",
        Routes.movie(m.id), favKind = ContentKind.MOVIE, favId = m.id)

    fun heroOf(c: ChannelEntity, nn: NowNext?): HeroItem {
        val fmt = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        val now = nn?.now
        val line = now?.let { "Now ${fmt.format(java.util.Date(it.startMs))}\u2013${fmt.format(java.util.Date(it.endMs))}" + (nn.next?.let { n -> " \u2022 Next: ${n.title}" } ?: "") }
        return HeroItem(now?.title ?: c.name, "Live \u2022 ${c.groupName}", now?.description ?: "Live channel from ${c.groupName}.", c.logo, "Watch now", Routes.player("live", c.id),
            isLive = true, channelName = c.name, nowLine = line, progress = now?.progress(System.currentTimeMillis()), favKind = ContentKind.LIVE, favId = c.id)
    }

    fun heroOf(s: SeriesEntity) = HeroItem(s.title, listOfNotNull("Series", s.genre, s.year).joinToString(" \u2022 "), s.plot ?: "From ${s.groupName}.", s.poster, "View episodes",
        Routes.series(s.id), favKind = ContentKind.SERIES, favId = s.id)

    fun toggleFavorite(h: HeroItem) { val k = h.favKind ?: return; val id = h.favId ?: return; viewModelScope.launch { user.toggleFavorite(k, id) } }

    fun loadSample() { viewModelScope.launch { sources.addSample() } }
}
