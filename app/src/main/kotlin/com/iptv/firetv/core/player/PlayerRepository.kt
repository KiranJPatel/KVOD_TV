package com.iptv.firetv.core.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import com.iptv.firetv.core.model.QualityMode
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.iptv.firetv.core.common.DiagnosticsLog
import com.iptv.firetv.core.common.Redactor
import com.iptv.firetv.core.network.Downloader
import com.iptv.firetv.core.network.MediaHttp
import com.iptv.firetv.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface PlayerRepository {
    val player: StateFlow<ExoPlayer?>
    val state: StateFlow<PlayerUiState>
    /** The system media session (null while no player exists). */
    val session: MediaSession?
    suspend fun play(request: PlayRequest)
    fun retry()
    fun togglePlayPause()
    fun seekBy(deltaMs: Long)
    fun selectAudio(id: String)
    fun selectText(id: String?)
    fun setPreferredLanguages(audio: String, text: String)
    fun setMuted(muted: Boolean)
    /** In-app player volume 0..1 (independent of the TV's own volume); applied immediately and on every new playback. */
    fun setVolume(volume: Float)
    /** Applies the video quality policy immediately (also used when the setting changes mid-playback). */
    fun setQualityMode(mode: QualityMode)
    fun positionMs(): Long
    fun durationMs(): Long
    fun currentRequest(): PlayRequest?
    /**
     * Attaches the duplicate entries of the channel that is starting. They are looked up after playback began so the first
     * channel never waits for them; if the first stream has already failed or is known to be bad, playback moves on at once.
     */
    fun attachAlternates(itemId: String, alternates: List<StreamAlt>)
    /**
     * Turns the muted Live TV preview of [itemId] into the real playback without re-tuning (no new connection, no new decoder).
     * Returns false when the preview is not that channel or is not healthy; the caller then plays normally.
     */
    fun promotePreview(itemId: String): Boolean
    /** Real measurements of the current playback (decoder, dropped frames, underruns, bandwidth). */
    val diagnostics: PlaybackDiagnostics
    /** Stops playback and releases the decoder/network resources. The next play() creates a new instance. */
    fun release()
}

/**
 * One ExoPlayer for the whole app, created lazily, never inside a composable, released when playback ends or the app
 * leaves the foreground. Transient failures are retried with bounded exponential backoff; permanent ones surface a
 * [PlaybackIssue] with actionable recovery options.
 */
@Singleton
class Media3PlayerRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val downloader: Downloader,
    private val settings: SettingsRepository
) : PlayerRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _player = MutableStateFlow<ExoPlayer?>(null)
    override val player: StateFlow<ExoPlayer?> = _player
    private val _state = MutableStateFlow(PlayerUiState())
    override val state: StateFlow<PlayerUiState> = _state

    private var request: PlayRequest? = null
    private var retryJob: Job? = null
    private var retryController = RetryController()
    @Volatile private var currentHeaders: Map<String, String> = emptyMap()
    private val mediaClient by lazy { MediaHttp.client(downloader.httpClient) }
    private var candidates: List<StreamAlt> = emptyList()
    private val failover = FailoverController()
    private val health = StreamHealth()
    private var watchdog: Job? = null
    private var bufferProfile = com.iptv.firetv.core.model.BufferProfile.BALANCED
    private var startedAtMs = 0L
    override val diagnostics = PlaybackDiagnostics()

    /**
     * Every data source (playlist-less, segment or progressive) is created with the headers of the stream that is playing now.
     * DefaultDataSource sends http(s) through OkHttp and also covers udp:// (multicast MPEG-TS), content:// and file://.
     */
    private val dataSourceFactory = DataSource.Factory {
        val h = currentHeaders
        val ua = h.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT
        val rest = h.filterKeys { !it.equals("User-Agent", true) }
        DefaultDataSource.Factory(ctx, OkHttpDataSource.Factory(mediaClient).setUserAgent(ua).setDefaultRequestProperties(rest)).createDataSource()
    }
    /** Start decoding MPEG-TS at the first decodable access unit instead of waiting for a full IDR keyframe: faster first picture. */
    private val tsFlags = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
    private var audioPref = ""; private var textPref = ""
    private var muted = false
    private var userVolume = 1f
    private var qualityMode = QualityMode.LOCK_1080
    private var lastLoggedHeights: List<Int> = emptyList()
    private val stalls = StallDetector()
    private var mediaSession: MediaSession? = null
    override val session: MediaSession? get() = mediaSession

    private suspend fun ensurePlayer(): ExoPlayer {
        _player.value?.let { return it }
        val s = settings.current()
        qualityMode = s.quality
        userVolume = s.playerVolume
        retryController = RetryController(RetryPolicy(s.maxRetries))
        val b = s.buffer
        bufferProfile = b
        val extractors = DefaultExtractorsFactory().setTsExtractorFlags(tsFlags)
        val p = ExoPlayer.Builder(ctx)
            // Falls back to another decoder (for example software) when the preferred one fails to initialise, instead of failing playback.
            .setRenderersFactory(DefaultRenderersFactory(ctx).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(ctx, extractors).setDataSourceFactory(dataSourceFactory))
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(b.minMs, b.maxMs, b.startMs, b.rebufferMs).build())
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            // Keeps the network (and CPU) awake while playing so a screensaver or dim timer never stalls a live stream.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        applyQuality(p, qualityMode)
        p.addListener(listener)
        p.addAnalyticsListener(diagnostics)
        // System media session: hardware media keys, Fire TV remote play/pause and Alexa transport commands reach the player.
        mediaSession = runCatching { MediaSession.Builder(ctx, p).setId("iptv-main").build() }.getOrNull()
        _player.value = p
        return p
    }

    /**
     * Quality policy for a 1080p panel: never request more than 1080p, and (when locked) never adapt below the best
     * variant at or under the cap. The viewport constraint is lifted on purpose: Media3 otherwise limits tracks to the
     * physical display mode, which is 720p whenever the Fire TV output resolution is set to 720p.
     */
    private fun applyQuality(p: ExoPlayer, mode: QualityMode) {
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setViewportSize(Int.MAX_VALUE, Int.MAX_VALUE, true)
            .setMaxVideoSize(Int.MAX_VALUE, mode.maxHeight)
            .setForceHighestSupportedBitrate(mode.lockHighest)
            .build()
    }

    private fun refreshVideo() {
        val p = _player.value ?: return
        val vs = p.videoSize
        val f = p.videoFormat
        _state.update {
            it.copy(video = if (vs.height > 0) VideoInfo(vs.width, vs.height, f?.bitrate?.takeIf { b -> b > 0 }?.div(1000), f?.frameRate?.takeIf { r -> r > 0f }, f?.sampleMimeType) else null)
        }
    }

    private fun offeredHeights(tracks: Tracks): List<Int> {
        val out = HashSet<Int>()
        for (g in tracks.groups) if (g.type == C.TRACK_TYPE_VIDEO) for (i in 0 until g.length) if (g.isTrackSupported(i)) g.getTrackFormat(i).height.takeIf { it > 0 }?.let(out::add)
        return out.sortedDescending()
    }

    private val listener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) { refreshVideo() }
        override fun onRenderedFirstFrame() {
            val ms = android.os.SystemClock.elapsedRealtime() - startedAtMs
            _state.update { it.copy(firstFrameMs = ms) }
            DiagnosticsLog.add("player", "First picture after $ms ms (stream ${failover.index + 1} of ${failover.total}, decoder ${diagnostics.videoDecoder ?: "?"})")
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            val p = _player.value ?: return
            val rebuffer = playbackState == Player.STATE_BUFFERING && _state.value.status == PlaybackStatus.READY
            _state.update {
                it.copy(
                    status = when (playbackState) {
                        Player.STATE_BUFFERING -> if (it.status == PlaybackStatus.READY) PlaybackStatus.BUFFERING else PlaybackStatus.LOADING
                        Player.STATE_READY -> PlaybackStatus.READY
                        Player.STATE_ENDED -> PlaybackStatus.ENDED
                        else -> if (it.status == PlaybackStatus.ERROR) it.status else PlaybackStatus.IDLE
                    },
                    canSeek = p.isCurrentMediaItemSeekable && p.duration != C.TIME_UNSET,
                    isLive = p.isCurrentMediaItemLive || (request?.isLive == true)
                )
            }
            if (rebuffer && qualityMode == QualityMode.LOCK_1080 && stalls.onStall(System.currentTimeMillis()) && !_state.value.suggestAuto) {
                DiagnosticsLog.add("player", "Repeated rebuffering while locked to 1080p; suggesting Auto")
                _state.update { it.copy(suggestAuto = true) }
            }
            if (playbackState == Player.STATE_READY) { watchdog?.cancel(); retryController.reset(); _state.update { it.copy(issue = null, retryAttempt = 0) } }
            // A live stream that stalls for too long moves on to a duplicate copy (if one exists) instead of buffering forever.
            if (rebuffer && request?.isLive == true) armWatchdog(bufferProfile.stallTimeoutMs)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) { _state.update { it.copy(isPlaying = isPlaying) } }
        override fun onTracksChanged(tracks: Tracks) {
            val heights = offeredHeights(tracks)
            if (heights.isNotEmpty() && heights != lastLoggedHeights) { lastLoggedHeights = heights; DiagnosticsLog.add("player", "Video variants offered: ${heights.joinToString { "${it}p" }}; policy ${qualityMode.name} picks ${QualityPolicy.pick(heights, qualityMode)?.let { "${it}p" } ?: "none within cap"}") }
            refreshVideo()
            _state.update { it.copy(offeredHeights = heights, audio = options(tracks, C.TRACK_TYPE_AUDIO), text = options(tracks, C.TRACK_TYPE_TEXT), textEnabled = tracks.groups.any { g -> g.type == C.TRACK_TYPE_TEXT && g.isSelected }) }
        }
        override fun onPlayerError(error: PlaybackException) {
            val p = _player.value
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && p != null) { p.seekToDefaultPosition(); p.prepare(); return }
            val http = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
            val issue = PlaybackErrorMapper.map(error.errorCode, http)
            DiagnosticsLog.add("player", "Error ${issue.code} ${issue.diagnostic} for ${Redactor.url(candidates.getOrNull(failover.index)?.url ?: request?.url)}")
            watchdog?.cancel()
            candidates.getOrNull(failover.index)?.let { health.markBad(it.url) }
            // A duplicate copy of the same channel is tried at once, before any waiting or retrying of the one that failed.
            if (failover.hasNext) { switchToNext("error ${issue.code}"); return }
            when (val d = retryController.onError(issue)) {
                RetryDecision.GiveUp -> _state.update { it.copy(status = PlaybackStatus.ERROR, issue = issue, isPlaying = false) }
                is RetryDecision.Retry -> {
                    _state.update { it.copy(status = PlaybackStatus.LOADING, retryAttempt = d.attempt, issue = null) }
                    retryJob?.cancel()
                    retryJob = scope.launch {
                        delay(d.delayMs)
                        val pl = _player.value ?: return@launch
                        if (failover.total > 1) { failover.rewind(); startCandidate(pl, pl.currentPosition.coerceAtLeast(0)) }
                        else { pl.prepare(); pl.play() }
                    }
                }
            }
        }
    }

    // ---- failover between duplicate copies of one channel

    private fun armWatchdog(timeoutMs: Long) {
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(timeoutMs)
            val p = _player.value ?: return@launch
            if (p.playbackState == Player.STATE_READY || !failover.hasNext) return@launch
            candidates.getOrNull(failover.index)?.let { health.markBad(it.url) }
            switchToNext("no picture for ${timeoutMs / 1000} s")
        }
    }

    private fun switchToNext(reason: String) {
        val p = _player.value ?: return
        val from = failover.index
        val to = failover.advance()
        DiagnosticsLog.add("player", "Switching from stream ${from + 1} to ${to + 1} of ${failover.total} ($reason)")
        _state.update { it.copy(status = PlaybackStatus.LOADING, issue = null, streamIndex = to, streamCount = failover.total) }
        startCandidate(p, 0)
    }

    override fun attachAlternates(itemId: String, alternates: List<StreamAlt>) {
        val r = request ?: return
        if (r.itemId != itemId || alternates.isEmpty() || candidates.isEmpty()) return
        val known = candidates.map { it.url }.toSet()
        val fresh = alternates.filter { it.url !in known }
        if (fresh.isEmpty()) return
        // Copies that failed recently go last; the one currently playing keeps its place.
        val playing = candidates[failover.index]
        val rest = health.order(candidates.filterIndexed { i, _ -> i != failover.index } + fresh)
        candidates = listOf(playing) + rest
        failover.jumpTo(0); failover.extend(candidates.size)
        _state.update { it.copy(streamIndex = 0, streamCount = failover.total) }
        // The stream that was chosen is known to be bad and nothing is showing yet: do not wait for it to time out again.
        val p = _player.value
        if (p != null && health.isBad(playing.url) && p.playbackState != Player.STATE_READY && failover.hasNext) switchToNext("known bad stream")
    }

    override fun promotePreview(itemId: String): Boolean {
        val r = request ?: return false
        val p = _player.value ?: return false
        if (!r.preview || r.itemId != itemId) return false
        if (_state.value.status == PlaybackStatus.ERROR || p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) return false
        request = r.copy(preview = false)
        muted = false; p.volume = userVolume; p.playWhenReady = true
        runCatching { ctx.startService(android.content.Intent(ctx, PlaybackService::class.java)) }
        DiagnosticsLog.add("player", "Preview promoted to full playback without re-tuning")
        return true
    }

    private fun options(tracks: Tracks, type: Int): List<TrackOption> {
        val out = ArrayList<TrackOption>()
        tracks.groups.forEachIndexed { gi, g ->
            if (g.type != type) return@forEachIndexed
            for (ti in 0 until g.length) {
                if (!g.isTrackSupported(ti)) continue
                val f = g.getTrackFormat(ti)
                val lang = f.language?.takeIf { it != "und" }?.let { l -> java.util.Locale.forLanguageTag(l).displayLanguage.ifBlank { l } } ?: "Track ${out.size + 1}"
                val extra = listOfNotNull(f.label?.takeIf { it != lang }, if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) "${f.channelCount}ch" else null).joinToString(" · ")
                out.add(TrackOption("$gi:$ti", if (extra.isEmpty()) lang else "$lang · $extra", g.isTrackSelected(ti)))
            }
        }
        return out
    }

    override suspend fun play(request: PlayRequest) = withContext(Dispatchers.Main.immediate) {
        this@Media3PlayerRepository.request = request
        retryController.reset(); retryJob?.cancel(); watchdog?.cancel()
        val p = ensurePlayer()
        p.volume = if (muted) 0f else userVolume
        candidates = health.order(listOf(StreamAlt(request.url, request.headers, request.drmLicenseUrl)) + request.alternates)
        failover.begin(candidates.size)
        qualityMode = settings.current().quality; applyQuality(p, qualityMode); lastLoggedHeights = emptyList(); stalls.reset(); diagnostics.reset()
        _state.value = PlayerUiState(status = PlaybackStatus.LOADING, isLive = request.isLive, quality = qualityMode, streamCount = failover.total)
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setPreferredAudioLanguage(audioPref.ifBlank { null }).setPreferredTextLanguage(textPref.ifBlank { null }).build()
        startedAtMs = android.os.SystemClock.elapsedRealtime()
        startCandidate(p, request.startPositionMs)
        // Notification/transport controls only for real playback, never for the muted Live TV preview.
        if (!request.preview) runCatching { ctx.startService(android.content.Intent(ctx, PlaybackService::class.java)) }
    }

    /** Builds and starts the copy at [FailoverController.index]. HLS gets MPEG-TS tuned for a fast first picture and CEA-608 captions exposed. */
    private fun startCandidate(p: ExoPlayer, startPositionMs: Long) {
        val request = request ?: return
        val c = candidates.getOrNull(failover.index) ?: return
        currentHeaders = c.headers
        val hls = c.url.substringBefore('?').lowercase().endsWith(".m3u8")
        val mb = MediaItem.Builder().setUri(c.url).setMediaId(request.itemId)
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(request.title).setDisplayTitle(request.title).build())
        if (hls) mb.setMimeType(MimeTypes.APPLICATION_M3U8)
        val subs = request.subtitles.filter { SubtitleFiles.isSafeUri(it.uri) }.map {
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(it.uri)).setMimeType(it.mime).setLabel(it.label).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        }
        if (subs.isNotEmpty()) mb.setSubtitleConfigurations(subs)
        c.drmLicenseUrl?.takeIf { it.startsWith("http", true) }?.let { mb.setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).setLicenseUri(it).build()) }
        val item = mb.build()
        val start = if (request.isLive) C.TIME_UNSET else startPositionMs
        if (hls) {
            val source = HlsMediaSource.Factory(dataSourceFactory)
                .setExtractorFactory(DefaultHlsExtractorFactory(tsFlags, true))
                .setAllowChunklessPreparation(true)
                .createMediaSource(item)
            p.setMediaSource(source, start)
        } else p.setMediaItem(item, start)
        p.prepare(); p.playWhenReady = true
        if (request.isLive) armWatchdog(bufferProfile.startTimeoutMs)
    }

    override fun retry() { val r = request ?: return; retryController.reset(); scope.launch { play(r) } }
    override fun togglePlayPause() { _player.value?.let { if (it.isPlaying) it.pause() else it.play() } }
    override fun seekBy(deltaMs: Long) { _player.value?.let { if (it.isCurrentMediaItemSeekable) it.seekTo((it.currentPosition + deltaMs).coerceIn(0, if (it.duration == C.TIME_UNSET) Long.MAX_VALUE else it.duration)) } }

    override fun selectAudio(id: String) = select(C.TRACK_TYPE_AUDIO, id)
    override fun selectText(id: String?) {
        val p = _player.value ?: return
        if (id == null) p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        else { p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build(); select(C.TRACK_TYPE_TEXT, id) }
    }

    private fun select(type: Int, id: String) {
        val p = _player.value ?: return
        val (gi, ti) = id.split(":").let { it[0].toIntOrNull() to it.getOrNull(1)?.toIntOrNull() }
        if (gi == null || ti == null) return
        val group = p.currentTracks.groups.getOrNull(gi)?.takeIf { it.type == type } ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, ti)).build()
    }

    override fun setQualityMode(mode: QualityMode) { qualityMode = mode; _player.value?.let { applyQuality(it, mode) }; _state.update { it.copy(quality = mode, suggestAuto = false) }; stalls.reset() }
    override fun setPreferredLanguages(audio: String, text: String) { audioPref = audio; textPref = text }
    override fun setMuted(muted: Boolean) { this.muted = muted; _player.value?.volume = if (muted) 0f else userVolume }
    override fun setVolume(volume: Float) { userVolume = volume.coerceIn(0f, 1f); if (!muted) _player.value?.volume = userVolume }
    override fun positionMs() = _player.value?.currentPosition ?: 0L
    override fun durationMs() = _player.value?.duration?.takeIf { it != C.TIME_UNSET } ?: 0L
    override fun currentRequest() = request

    override fun release() {
        retryJob?.cancel(); watchdog?.cancel()
        candidates = emptyList()
        runCatching { ctx.stopService(android.content.Intent(ctx, PlaybackService::class.java)) }
        mediaSession?.release(); mediaSession = null   // the session must be released before its player
        _player.value?.let { it.removeListener(listener); it.removeAnalyticsListener(diagnostics); it.release() }
        _player.value = null
        muted = false
        _state.value = PlayerUiState(quality = qualityMode)
    }

    private companion object { const val DEFAULT_USER_AGENT = "IPTV-FireTV/0.1" }
}
