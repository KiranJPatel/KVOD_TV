package com.iptv.firetv.feature.epg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.firetv.core.database.ChannelEntity
import com.iptv.firetv.core.database.ProgramEntity
import com.iptv.firetv.core.model.ContentKind
import com.iptv.firetv.data.CatalogRepository
import com.iptv.firetv.data.EpgRepository
import com.iptv.firetv.data.UserDataRepository
import com.iptv.firetv.feature.player.ChannelQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class GuideState(
    val loading: Boolean = true, val channels: List<ChannelEntity> = emptyList(), val programs: Map<String, List<ProgramEntity>> = emptyMap(),
    val windowStart: Long = 0, val windowEnd: Long = 0, val hasGuideData: Boolean = false, val availableDays: Int = 0,
    val zoneLabel: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class EpgViewModel @Inject constructor(
    private val catalog: CatalogRepository, private val epg: EpgRepository, private val user: UserDataRepository, val queue: ChannelQueue
) : ViewModel() {
    val dayOffset = MutableStateFlow(0)
    val favoritesOnly = MutableStateFlow(false)
    val query = MutableStateFlow("")
    val selected = MutableStateFlow<ProgramEntity?>(null)
    val selectedChannel = MutableStateFlow<ChannelEntity?>(null)
    val favoriteIds: StateFlow<Set<String>> = user.favoriteIds(ContentKind.LIVE).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val guide: StateFlow<GuideState> = combine(dayOffset, favoritesOnly, query.debounce(250)) { d, f, q -> Triple(d, f, q) }.flatMapLatest { (d, fav, q) ->
        val channelsFlow = if (fav) user.favoriteChannels() else catalog.channels(null, q, 200)
        channelsFlow.let { cf -> flow { cf.collect { list ->
            val zone: ZoneId = epg.zone()
            val dayStart = LocalDate.now(zone).plusDays(d.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            val from = if (d == 0) (System.currentTimeMillis() / 1_800_000L) * 1_800_000L - 3_600_000L else dayStart
            val to = (if (d == 0) from + 25 * 3_600_000L else dayStart + 24 * 3_600_000L)
            val shown = (if (fav && q.isNotBlank()) list.filter { it.name.contains(q, true) } else list).take(150)
            val progs = epg.guide(shown, from, to)
            val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            val last = epg.lastProgramEnd()
            val days = if (last == null || last <= todayStart) 0 else (((last - todayStart - 1) / 86_400_000L).toInt() + 1).coerceAtMost(7)
            emit(GuideState(false, shown, progs, from, to, progs.isNotEmpty(), days, zone.id))
        } } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuideState())

    fun prepareQueue(channels: List<ChannelEntity>) = queue.set(channels.map { it.id })
    fun toggleFavorite(id: String) { viewModelScope.launch { user.toggleFavorite(ContentKind.LIVE, id) } }
}
