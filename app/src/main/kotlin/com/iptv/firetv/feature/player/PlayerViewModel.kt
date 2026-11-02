package com.iptv.firetv.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.firetv.core.common.AppException
import com.iptv.firetv.core.database.ChannelEntity
import com.iptv.firetv.core.model.ContentKind
import com.iptv.firetv.core.model.NowNext
import com.iptv.firetv.core.player.PlayRequest
import com.iptv.firetv.core.player.PlaybackStatus
import com.iptv.firetv.core.player.StreamAlt
import com.iptv.firetv.core.player.PlayerRepository
import com.iptv.firetv.data.AppSettings
import com.iptv.firetv.data.CatalogRepository
import com.iptv.firetv.data.EpgRepository
import com.iptv.firetv.data.ProfileRepository
import com.iptv.firetv.data.SettingKeys
import com.iptv.firetv.data.SettingsRepository
import com.iptv.firetv.data.StreamResolver
import com.iptv.firetv.data.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerScreenState(
    val kind: String = "live", val itemId: String = "", val title: String = "", val subtitle: String? = null, val logo: String? = null,
    val isLive: Boolean = true, val nowNext: NowNext? = null, val isFavorite: Boolean = false,
    val fatal: String? = null, val queue: List<ChannelEntity> = emptyList(), val canZap: Boolean = false, val hasLast: Boolean = false,
    val number: Int? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    saved: SavedStateHandle, val repo: PlayerRepository, private val catalog: CatalogRepository, private val user: UserDataRepository,
    private val epg: EpgRepository, private val resolver: StreamResolver, private val settingsRepo: SettingsRepository,
    private val profiles: ProfileRepository, val queue: ChannelQueue
) : ViewModel() {
    val playback = repo.state
    private val _screen = MutableStateFlow(PlayerScreenState())
    val screen: StateFlow<PlayerScreenState> = _screen
    val settings: StateFlow<AppSettings?> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /** Random channel mode for up/down/CH+/CH- (session based, see [ChannelQueue.shuffle]). */
    val shuffle: StateFlow<Boolean> = queue.shuffle

    private var kind = saved.get<String>("kind") ?: "live"
    private var itemId = saved.get<String>("id") ?: ""
    private var poster: String? = null
    private var title = ""
    private var previousId: String? = null
    private var resumeAt = 0L
    private var stopped = false
    private var loadJob: Job? = null
    private var epgJob: Job? = null
    private var extrasJob: Job? = null

    init {
        viewModelScope.launch { start(kind, itemId, -1) }
        viewModelScope.launch { // persist VOD progress every 10 s while playing
            while (true) { delay(10_000); if (kind != "live" && repo.state.value.isPlaying) saveProgress() }
        }
        viewModelScope.launch { // end of an episode: mark watched and optionally continue
            var handled = false
            repo.state.collect { st ->
                if (st.status == PlaybackStatus.ENDED && !handled && kind != "live") { handled = true; onEnded() }
                if (st.status != PlaybackStatus.ENDED) handled = false
            }
        }
    }

    private fun start(k: String, id: String, startPos: Long) { loadJob?.cancel(); loadJob = viewModelScope.launch { load(k, id, startPos) } }

    private suspend fun load(k: String, id: String, startPos: Long) {
        epgJob?.cancel(); extrasJob?.cancel()
        repo.setMuted(false)   // a Live TV preview may have been playing muted
        _screen.value = _screen.value.copy(fatal = null)
        val prof = profiles.activeProfile.first()
        repo.setPreferredLanguages(prof?.audioLang.orEmpty(), prof?.subtitleLang.orEmpty())
        try {
            when (k) {
                "live" -> {
                    val c = catalog.channel(id) ?: return fail("This channel is not available. It may have been removed or is restricted for this profile.")
                    kind = k; itemId = id; title = c.name; poster = c.logo
                    // Fast path: this channel is already playing in the Live TV preview. Keep that connection and decoder.
                    val promoted = repo.promotePreview(id)
                    // The picture comes first. Favourite state, the zap list and the duplicate (backup) streams are filled in right after,
                    // so none of those database lookups sits between pressing OK and the stream request.
                    _screen.value = PlayerScreenState("live", id, c.name, c.groupName, c.logo, true, null, false, null, emptyList(), queue.ids.size > 1, previousId != null, c.number)
                    if (!promoted) {
                        val r = resolver.resolve(c.sourceId, c.streamRef, c.headersJson)
                        repo.play(PlayRequest(id, c.name, r.url, r.headers, isLive = true, drmLicenseUrl = r.drmLicenseUrl))
                    }
                    loadLiveExtras(c)
                    epgJob = viewModelScope.launch { while (true) { _screen.value = _screen.value.copy(nowNext = epg.nowNext(listOf(c))[c.id]); delay(30_000) } }
                }
                "movie" -> {
                    val m = catalog.movie(id) ?: return fail("This title is not available. It may have been removed or is restricted for this profile.")
                    kind = k; itemId = id; title = m.title; poster = m.poster
                    val r = resolver.resolve(m.sourceId, m.streamRef, m.headersJson)
                    val pos = if (startPos >= 0) startPos else user.progress(id)?.takeIf { !it.completed }?.positionMs ?: 0L
                    _screen.value = PlayerScreenState("movie", id, m.title, m.genre, m.poster, false, null, id in user.favoriteIds(ContentKind.MOVIE).first(), null)
                    repo.play(PlayRequest(id, m.title, r.url, r.headers, false, pos, subtitles = listOfNotNull(settingsRepo.subtitleFor(id)), drmLicenseUrl = r.drmLicenseUrl))
                }
                "episode" -> {
                    val (e, s) = catalog.episode(id) ?: return fail("This episode is not available. It may have been removed or is restricted for this profile.")
                    kind = k; itemId = id; title = "${s.title} \u2022 S${e.season}E${e.episode}"; poster = e.poster ?: s.poster
                    val r = resolver.resolve(s.sourceId, e.streamRef, e.headersJson)
                    val pos = if (startPos >= 0) startPos else user.progress(id)?.takeIf { !it.completed }?.positionMs ?: 0L
                    _screen.value = PlayerScreenState("episode", id, title, e.title, poster, false, null, false, null)
                    repo.play(PlayRequest(id, title, r.url, r.headers, false, pos, subtitles = listOfNotNull(settingsRepo.subtitleFor(id)), drmLicenseUrl = r.drmLicenseUrl))
                }
                else -> fail("Unknown content type.")
            }
        } catch (e: AppException) { fail(e.userMessage) }
    }

    /** Everything about a live channel that is not needed to start the picture. */
    private fun loadLiveExtras(c: ChannelEntity) {
        extrasJob?.cancel()
        extrasJob = viewModelScope.launch {
            user.recordChannelWatched(c.id); settingsRepo.set(SettingKeys.LAST_CHANNEL, c.id)
            val fav = c.id in user.favoriteIds(ContentKind.LIVE).first()
            // Opened without a list (for example from Home): zap through all channels instead of having nothing to zap to.
            if (queue.ids.isEmpty()) queue.set(catalog.channelIds(null, ""))
            val list = queue.ids.takeIf { it.isNotEmpty() }?.let { catalog.channelsByIds(it.take(500)) }.orEmpty()
            if (itemId == c.id) _screen.value = _screen.value.copy(isFavorite = fav, queue = list, canZap = queue.ids.size > 1)
            // Duplicate entries of this channel become fallback streams (never listed). Credentials are resolved per copy.
            val alts = catalog.alternatesOf(c).mapNotNull { a ->
                runCatching { resolver.resolve(a.sourceId, a.streamRef, a.headersJson) }.getOrNull()?.let { StreamAlt(it.url, it.headers, it.drmLicenseUrl, a.name) }
            }
            if (alts.isNotEmpty() && itemId == c.id) repo.attachAlternates(c.id, alts)
        }
    }

    private fun fail(msg: String) { _screen.value = _screen.value.copy(fatal = msg) }

    fun zap(delta: Int) { if (kind != "live") return; queue.next(itemId, delta)?.takeIf { it != itemId }?.let { previousId = itemId; start("live", it, -1) } }
    fun lastChannel() { val p = previousId ?: return; previousId = itemId; start("live", p, -1) }
    fun toggleShuffle() { queue.setShuffle(!queue.shuffle.value) }

    // Volume and brightness: applied immediately while the control is open, saved when it closes.
    fun applyVolume(v: Float) { repo.setVolume(v) }
    fun saveVolume(v: Float) { viewModelScope.launch { settingsRepo.set(SettingKeys.PLAYER_VOLUME, v.coerceIn(0f, 1f)) } }
    fun saveBrightness(v: Float) { viewModelScope.launch { settingsRepo.set(SettingKeys.PLAYER_BRIGHTNESS, v.coerceIn(0.2f, 1f)) } }
    fun saveSubtitleStyle(st: com.iptv.firetv.core.player.SubtitleStyle) { viewModelScope.launch { settingsRepo.setSubtitleStyle(st) } }

    fun playChannel(id: String) { if (id != itemId) { previousId = itemId; start("live", id, -1) } }
    val subtitleMessage = MutableStateFlow<String?>(null)

    /** Remembers an external subtitle file for this item and restarts playback at the current position so it is applied. */
    fun addSubtitle(uri: String, fileName: String) {
        if (kind == "live") return
        val mime = com.iptv.firetv.core.player.SubtitleFiles.mimeFor(fileName)
        if (mime == null || !com.iptv.firetv.core.player.SubtitleFiles.isSafeUri(uri)) { subtitleMessage.value = "Unsupported subtitle file. Use .srt, .vtt, .ass/.ssa, .ttml or .sup."; return }
        viewModelScope.launch {
            settingsRepo.setSubtitle(itemId, com.iptv.firetv.core.player.SubtitleSource(uri, fileName.substringBeforeLast('.'), mime))
            subtitleMessage.value = "Subtitles loaded: $fileName"
            start(kind, itemId, repo.positionMs())
        }
    }

    fun retry() { if (_screen.value.fatal != null) start(kind, itemId, -1) else repo.retry() }
    fun setQuality(m: com.iptv.firetv.core.model.QualityMode) { repo.setQualityMode(m); viewModelScope.launch { settingsRepo.set(SettingKeys.QUALITY, m.name) } }
    fun setResize(mode: Int) { viewModelScope.launch { settingsRepo.set(SettingKeys.RESIZE_MODE, mode) } }
    fun positionMs() = repo.positionMs()
    fun durationMs() = repo.durationMs()

    fun toggleFavorite() { viewModelScope.launch {
        val k = when (kind) { "live" -> ContentKind.LIVE; "movie" -> ContentKind.MOVIE; else -> return@launch }
        _screen.value = _screen.value.copy(isFavorite = user.toggleFavorite(k, itemId))
    } }

    private suspend fun saveProgress() {
        if (kind == "live") return
        user.saveProgress(if (kind == "movie") ContentKind.MOVIE else ContentKind.EPISODE, itemId, title, poster, repo.positionMs(), repo.durationMs())
    }

    private suspend fun onEnded() {
        user.saveProgress(if (kind == "movie") ContentKind.MOVIE else ContentKind.EPISODE, itemId, title, poster, repo.durationMs(), repo.durationMs())
        if (kind == "episode" && settings.value?.autoplayNext != false) {
            val cur = catalog.episode(itemId)?.first ?: return
            catalog.nextEpisode(cur)?.let { start("episode", it.id, 0) }
        }
    }

    fun onStop() { stopped = true; resumeAt = repo.positionMs(); viewModelScope.launch { saveProgress() } }
    fun onStart() { if (stopped && repo.player.value == null) { stopped = false; start(kind, itemId, if (kind == "live") -1 else resumeAt) } else stopped = false }

    override fun onCleared() {
        val k = kind; val id = itemId; val t = title; val p = poster; val pos = repo.positionMs(); val dur = repo.durationMs()
        if (k != "live" && dur > 0) CoroutineScope(Dispatchers.IO).launch { user.saveProgress(if (k == "movie") ContentKind.MOVIE else ContentKind.EPISODE, id, t, p, pos, dur) }
        repo.release()
        super.onCleared()
    }
}
