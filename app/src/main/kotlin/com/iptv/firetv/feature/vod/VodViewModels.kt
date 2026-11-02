package com.iptv.firetv.feature.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import com.iptv.firetv.core.database.EpisodeEntity
import com.iptv.firetv.core.database.GroupCount
import com.iptv.firetv.core.database.SeriesEntity
import com.iptv.firetv.core.database.VodItemEntity
import com.iptv.firetv.core.database.WatchProgressEntity
import com.iptv.firetv.core.model.ContentKind
import com.iptv.firetv.data.CatalogRepository
import com.iptv.firetv.data.ImportRepository
import com.iptv.firetv.data.SourceRepository
import com.iptv.firetv.data.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class MoviesViewModel @Inject constructor(private val catalog: CatalogRepository, private val user: UserDataRepository) : ViewModel() {
    val group = MutableStateFlow<String?>(null)
    val query = MutableStateFlow("")
    val sort = MutableStateFlow(0)
    var lastFocusedId: String? = null
    val groups: StateFlow<List<GroupCount>> = catalog.movieGroups().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val paged: Flow<PagingData<VodItemEntity>> = combine(group, query.debounce(250), sort) { g, q, s -> Triple(g, q, s) }
        .flatMapLatest { (g, q, s) -> catalog.pagedMovies(g, q, s) }.cachedIn(viewModelScope)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SeriesViewModel @Inject constructor(private val catalog: CatalogRepository) : ViewModel() {
    val group = MutableStateFlow<String?>(null)
    val query = MutableStateFlow("")
    val sort = MutableStateFlow(0)
    var lastFocusedId: String? = null
    val groups: StateFlow<List<GroupCount>> = catalog.seriesGroups().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val paged: Flow<PagingData<SeriesEntity>> = combine(group, query.debounce(250), sort) { g, q, s -> Triple(g, q, s) }
        .flatMapLatest { (g, q, s) -> catalog.pagedSeries(g, q, s) }.cachedIn(viewModelScope)
}

data class MovieDetail(val movie: VodItemEntity? = null, val loaded: Boolean = false, val favorite: Boolean = false, val progress: WatchProgressEntity? = null, val attribution: Boolean = false)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(saved: SavedStateHandle, private val catalog: CatalogRepository, private val user: UserDataRepository,
                                               private val imports: ImportRepository, private val sources: SourceRepository, private val metadata: com.iptv.firetv.data.MetadataRepository) : ViewModel() {
    private val id = saved.get<String>("id").orEmpty()
    private val _state = MutableStateFlow(MovieDetail())
    val state: StateFlow<MovieDetail> = _state

    init { viewModelScope.launch {
        val m = catalog.movie(id)   // null when removed or restricted for the active profile
        _state.value = MovieDetail(m, true, id in user.favoriteIds(ContentKind.MOVIE).first(), user.progress(id))
        if (m != null) sources.xtreamConfigOf(m.sourceId)?.let { cfg ->
            runCatching { imports.enrichXtreamMovie(cfg, m) }.getOrNull()?.let { e -> _state.value = _state.value.copy(movie = e) }
        }
        // Optional provider lookup fills only what the source did not supply.
        if (m != null && metadata.isReady()) { val e = metadata.enrichMovie(_state.value.movie ?: m); _state.value = _state.value.copy(movie = e, attribution = true) }
    } }

    fun toggleFavorite() { viewModelScope.launch { _state.value = _state.value.copy(favorite = user.toggleFavorite(ContentKind.MOVIE, id)) } }
}

data class SeriesDetail(val series: SeriesEntity? = null, val loaded: Boolean = false, val favorite: Boolean = false, val loadingEpisodes: Boolean = false, val error: String? = null, val attribution: Boolean = false)

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(saved: SavedStateHandle, private val catalog: CatalogRepository, private val user: UserDataRepository,
                                                private val imports: ImportRepository, private val sources: SourceRepository, private val metadata: com.iptv.firetv.data.MetadataRepository) : ViewModel() {
    private val id = saved.get<String>("id").orEmpty()
    private val _state = MutableStateFlow(SeriesDetail())
    val state: StateFlow<SeriesDetail> = _state
    val episodes: StateFlow<List<EpisodeEntity>> = catalog.episodes(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val progress = MutableStateFlow<Map<String, WatchProgressEntity>>(emptyMap())

    init { viewModelScope.launch {
        val s = catalog.series(id)
        _state.value = SeriesDetail(s, true, id in user.favoriteIds(ContentKind.SERIES).first())
        if (s != null) loadEpisodesIfNeeded(s)
        if (s != null && metadata.isReady()) { val e = metadata.enrichSeries(_state.value.series ?: s); _state.value = _state.value.copy(series = e, attribution = true) }
    } }

    fun refreshProgress() { viewModelScope.launch { progress.value = episodes.value.mapNotNull { e -> user.progress(e.id)?.let { e.id to it } }.toMap() } }

    private suspend fun loadEpisodesIfNeeded(s: SeriesEntity) {
        val cfg = sources.xtreamConfigOf(s.sourceId) ?: return
        if (episodes.value.isNotEmpty()) return
        _state.value = _state.value.copy(loadingEpisodes = true)
        try { imports.loadXtreamEpisodes(cfg, s); _state.value = _state.value.copy(loadingEpisodes = false) }
        catch (e: Exception) { _state.value = _state.value.copy(loadingEpisodes = false, error = (e as? com.iptv.firetv.core.common.AppException)?.userMessage ?: "Episodes could not be loaded.") }
        refreshProgress()
    }

    fun retry() { viewModelScope.launch { _state.value.series?.let { _state.value = _state.value.copy(error = null); loadEpisodesIfNeeded(it) } } }
    fun toggleFavorite() { viewModelScope.launch { _state.value = _state.value.copy(favorite = user.toggleFavorite(ContentKind.SERIES, id)) } }
}
