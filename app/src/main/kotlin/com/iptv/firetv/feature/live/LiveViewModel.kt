package com.iptv.firetv.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.firetv.core.database.ChannelEntity
import com.iptv.firetv.core.database.GroupCount
import com.iptv.firetv.core.database.SourceEntity
import com.iptv.firetv.core.model.ContentKind
import com.iptv.firetv.core.model.NowNext
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.iptv.firetv.data.CatalogRepository
import kotlinx.coroutines.flow.first
import com.iptv.firetv.data.EpgRepository
import com.iptv.firetv.data.SettingKeys
import com.iptv.firetv.data.SettingsRepository
import com.iptv.firetv.data.SourceRepository
import com.iptv.firetv.data.UserDataRepository
import com.iptv.firetv.data.StreamResolver
import com.iptv.firetv.core.player.PlayRequest
import com.iptv.firetv.core.player.PlayerRepository
import com.iptv.firetv.core.common.AppException
import com.iptv.firetv.feature.player.ChannelQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

const val GROUP_FAVORITES = "__fav__"
const val GROUP_RECENT = "__recent__"
/** A user playlist is selected as a category with the id "__pl__<playlistId>". */
const val GROUP_PLAYLIST_PREFIX = "__pl__"
fun playlistGroup(id: Long) = GROUP_PLAYLIST_PREFIX + id
fun playlistIdOf(group: String?): Long? = group?.takeIf { it.startsWith(GROUP_PLAYLIST_PREFIX) }?.removePrefix(GROUP_PLAYLIST_PREFIX)?.toLongOrNull()

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LiveViewModel @Inject constructor(
    private val catalog: CatalogRepository, private val user: UserDataRepository, private val epg: EpgRepository,
    private val settings: SettingsRepository, sources: SourceRepository, val queue: ChannelQueue,
    val repo: PlayerRepository, private val resolver: StreamResolver, private val playlistRepo: com.iptv.firetv.data.PlaylistRepository
) : ViewModel() {
    val group = MutableStateFlow<String?>(null)
    val query = MutableStateFlow("")
    val selected = MutableStateFlow<ChannelEntity?>(null)
    /** Remembered so focus can return to the same channel after playback. */
    var lastFocusedId: String? = null
    /** True after the user started playback from the list, so focus returns to that channel (and only then). */
    var restoreFocusOnReturn = false

    val groups: StateFlow<List<GroupCount>> = catalog.channelGroups().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sources: StateFlow<List<SourceEntity>> = sources.sources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val appSettings = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val playlists: StateFlow<List<com.iptv.firetv.core.database.PlaylistRow>> = playlistRepo.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favoriteIds: StateFlow<Set<String>> = user.favoriteIds(ContentKind.LIVE).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Paged list for the current category/filter. Favourites and Recently watched are small, so they are wrapped as a single page. */
    val paged: Flow<PagingData<ChannelEntity>> = combine(group, query.debounce(200).distinctUntilChanged()) { g, q -> g to q }
        .flatMapLatest { (g, q) ->
            fun filtered(l: List<ChannelEntity>) = PagingData.from(if (q.isBlank()) l else l.filter { it.name.contains(q, ignoreCase = true) })
            val playlist = playlistIdOf(g)
            when {
                g == GROUP_FAVORITES -> user.favoriteChannels().map { filtered(it) }
                g == GROUP_RECENT -> user.recentChannels(50).map { filtered(it) }
                playlist != null -> playlistRepo.channels(playlist).map { filtered(it) }
                else -> catalog.pagedChannels(g, q)
            }
        }.cachedIn(viewModelScope)

    /** Bumped when a guide mapping changes so the screen reloads now/next immediately. */
    val guideBump = MutableStateFlow(0)
    val nowNext = MutableStateFlow<Map<String, NowNext>>(emptyMap())
    /** Called by the screen for the channels currently loaded (first 300), every minute. */
    suspend fun loadNowNext(list: List<ChannelEntity>) { nowNext.value = epg.nowNext(list.take(300)) }

    /** Ordered ids of the whole current list (category, favourites, recent or playlist, with the text filter applied). */
    private suspend fun currentIds(): List<String> {
        val g = group.value; val q = query.value
        fun List<ChannelEntity>.filtered() = (if (q.isBlank()) this else filter { it.name.contains(q, ignoreCase = true) }).map { it.id }
        val playlist = playlistIdOf(g)
        return when {
            g == GROUP_FAVORITES -> user.favoriteChannels().first().filtered()
            g == GROUP_RECENT -> user.recentChannels(50).first().filtered()
            playlist != null -> playlistRepo.channels(playlist).first().filtered()
            else -> catalog.channelIds(g, q)
        }
    }

    /** Channel up/down in the player walks the same list the user was browsing, in list order. */
    suspend fun buildQueue() { queue.set(currentIds()); queue.setShuffle(false) }

    /** Starts random play: remembers the list, switches the player to random mode and returns the first random channel (null when the list is empty). */
    suspend fun playRandom(): String? {
        val ids = currentIds()
        if (ids.isEmpty()) return null
        queue.set(ids); queue.setShuffle(true)
        return queue.randomPick(null)
    }

    /** Id of the channel currently shown in the preview pane (null when no preview is running). */
    val previewId = MutableStateFlow<String?>(null)

    init {
        // Preview starts only after focus rests on a channel for ~1 s, so fast scrolling never opens streams.
        viewModelScope.launch {
            combine(selected.debounce(900), settings.settings.map { it.livePreview && it.showPreviewPane }.distinctUntilChanged()) { c, on -> c to on }
                .collect { (c, on) -> if (!on || c == null) stopPreview() else startPreview(c) }
        }
    }

    private suspend fun startPreview(c: ChannelEntity) {
        try {
            val r = resolver.resolve(c.sourceId, c.streamRef, c.headersJson)
            repo.setMuted(true)
            previewId.value = c.id
            repo.play(PlayRequest(c.id, c.name, r.url, r.headers, isLive = true, preview = true, drmLicenseUrl = r.drmLicenseUrl))
        } catch (e: AppException) { previewId.value = null }
    }

    /** Stops only our own preview; a real playback started from the player screen is never touched. */
    fun stopPreview() { if (repo.currentRequest()?.preview == true) repo.release(); previewId.value = null }
    fun restartPreview() { val c = selected.value ?: return; if (repo.player.value == null) viewModelScope.launch { if (appSettings.value?.livePreview != false) startPreview(c) } }
    override fun onCleared() { stopPreview(); super.onCleared() }

    // ---- manual guide mapping (explicit user mapping beats automatic matching)
    suspend fun searchGuideChannels(q: String) = epg.searchEpgChannels(q)
    suspend fun currentMapping(channelId: String) = epg.mappingOf(channelId)
    fun setMapping(channelId: String, epgId: String?) { viewModelScope.launch { epg.setMapping(channelId, epgId); guideBump.value++ } }

    fun toggleFavorite(id: String) { viewModelScope.launch { user.toggleFavorite(ContentKind.LIVE, id) } }
    fun setSort(sort: Int) { viewModelScope.launch { settings.set(SettingKeys.CHANNEL_SORT, sort) } }
    fun setSource(id: Long) { viewModelScope.launch { settings.set(SettingKeys.ACTIVE_SOURCE, id) } }
}
