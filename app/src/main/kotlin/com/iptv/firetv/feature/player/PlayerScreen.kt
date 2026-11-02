package com.iptv.firetv.feature.player

import com.iptv.firetv.core.common.tr
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape as RCS
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.iptv.firetv.app.Routes
import com.iptv.firetv.app.navigateTop
import com.iptv.firetv.core.player.PlaybackStatus
import com.iptv.firetv.core.ui.Artwork
import com.iptv.firetv.core.ui.ConfirmDialog
import com.iptv.firetv.core.ui.Dimens
import com.iptv.firetv.core.ui.FocusSurface
import com.iptv.firetv.core.ui.LiveBadge
import com.iptv.firetv.core.ui.LevelPanel
import com.iptv.firetv.core.ui.SubtitleStyleEditor
import com.iptv.firetv.core.model.ContentKind
import com.iptv.firetv.core.player.SubtitleStyle
import com.iptv.firetv.feature.playlists.PlaylistPickerDialog
import com.iptv.firetv.core.ui.LocalIptvColors
import com.iptv.firetv.core.ui.OptionDialog
import com.iptv.firetv.core.ui.ProgressLine
import com.iptv.firetv.core.ui.RoundIconButton
import com.iptv.firetv.core.ui.StatePanel
import com.iptv.firetv.core.ui.TvButton
import com.iptv.firetv.core.ui.TvDialog
import com.iptv.firetv.core.ui.formatDuration
import com.iptv.firetv.core.ui.formatTime
import kotlinx.coroutines.delay

private enum class Dlg { NONE, AUDIO, TEXT, QUALITY, SETTINGS, CHANNELS, EXIT, VOLUME, BRIGHTNESS, PLAYLIST, SUBSTYLE }

@Composable
fun PlayerScreen(nav: NavHostController, vm: PlayerViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val screen by vm.screen.collectAsStateWithLifecycle()
    val pb by vm.playback.collectAsStateWithLifecycle()
    val player by vm.repo.player.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val overlaySeconds = settings?.overlaySeconds ?: 5
    val zapEnabled = settings?.dpadZapping ?: true

    var overlay by remember { mutableStateOf(true) }
    var touched by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(Dlg.NONE) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var quality by remember { mutableStateOf<String?>(null) }
    var banner by remember { mutableStateOf(false) }
    var styleRequested by remember { mutableStateOf(false) }
    val shuffle by vm.shuffle.collectAsStateWithLifecycle()
    var volume by remember { mutableFloatStateOf(1f) }
    var preMute by remember { mutableFloatStateOf(0.7f) }
    var brightness by remember { mutableFloatStateOf(1f) }
    // The level panel is drawn over the picture; while it is open the control dock is hidden so the effect can be judged.
    val adjusting = dialog == Dlg.VOLUME || dialog == Dlg.BRIGHTNESS
    val volumeButton = remember { FocusRequester() }
    val brightnessButton = remember { FocusRequester() }
    val muteButton = remember { FocusRequester() }
    var returnFocusTo by remember { mutableStateOf<FocusRequester?>(null) }
    // Mute keeps the real volume so Unmute (and the next channel) restores it; muting itself is never saved as the volume.
    fun toggleMute() { if (volume > 0f) { preMute = volume; volume = 0f } else volume = preMute.coerceAtLeast(0.1f); vm.applyVolume(volume) }
    // Volume and brightness start from the saved values once settings have loaded.
    LaunchedEffect(settings != null) { settings?.let { volume = it.playerVolume; brightness = it.playerBrightness; vm.applyVolume(volume) } }
    val subStyle = settings?.subtitle ?: SubtitleStyle()
    val inPip by com.iptv.firetv.app.PipState.active.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { com.iptv.firetv.app.PipState.playerVisible.value = true; onDispose { com.iptv.firetv.app.PipState.playerVisible.value = false } }
    val root = remember { FocusRequester() }
    val subPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = runCatching { ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }.getOrNull() ?: "subtitles.srt"
            vm.addSubtitle(uri.toString(), name)
        }
    }
    val subMsg by vm.subtitleMessage.collectAsStateWithLifecycle()
    LaunchedEffect(subMsg) { subMsg?.let { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_LONG).show(); vm.subtitleMessage.value = null } }
    val firstControl = remember { FocusRequester() }

    fun show() { overlay = true; touched++ }

    // Lifecycle: save progress and let the activity release decoders on stop; restore playback on return.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.onStop() else if (e == Lifecycle.Event.ON_START) vm.onStart() }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }

    LaunchedEffect(overlay, touched, dialog) { if (overlay && dialog == Dlg.NONE) { delay(overlaySeconds * 1000L); overlay = false } }
    LaunchedEffect(overlay) { while (overlay) { pos = vm.positionMs(); dur = vm.durationMs(); quality = pb.video?.let { v -> listOfNotNull(v.label, v.bitrateKbps?.let { "%.1f Mbps".format(it / 1000f) }, v.frameRate?.let { "${it.toInt()} fps" }).joinToString(" \u00b7 ") }; delay(500) } }
    // Zapping with the overlay hidden shows only a small channel banner for two seconds.
    LaunchedEffect(screen.itemId) { if (!overlay && screen.isLive && screen.title.isNotEmpty()) { banner = true; delay(2000); banner = false } }
    LaunchedEffect(overlay, dialog) { delay(80); runCatching { if (overlay && dialog == Dlg.NONE) { val target = returnFocusTo ?: firstControl; returnFocusTo = null; target.requestFocus() } else if (!overlay && dialog == Dlg.NONE) root.requestFocus() } }
    LaunchedEffect(pb.status) { if (pb.status == PlaybackStatus.ERROR || pb.status == PlaybackStatus.LOADING) show() }

    BackHandler {
        when {
            dialog != Dlg.NONE -> dialog = Dlg.NONE
            overlay -> overlay = false
            settings?.confirmExit == true -> dialog = Dlg.EXIT
            else -> nav.popBackStack()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(root).focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                touched++
                val code = ev.nativeKeyEvent.keyCode
                when (code) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> { vm.repo.togglePlayPause(); show(); true }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { if (pb.canSeek) vm.repo.seekBy(30_000); show(); true }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> { if (pb.canSeek) vm.repo.seekBy(-10_000); show(); true }
                    KeyEvent.KEYCODE_CHANNEL_UP -> { vm.zap(1); show(); true }
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.zap(-1); show(); true }
                    KeyEvent.KEYCODE_LAST_CHANNEL -> { vm.lastChannel(); show(); true }
                    else -> if (dialog != Dlg.NONE) false else if (overlay) {
                        // Overlay visible: the control row has no vertical neighbours, so Up/Down can safely change channel.
                        when (code) {
                            KeyEvent.KEYCODE_DPAD_UP -> if (screen.isLive && zapEnabled) { vm.zap(-1); true } else false
                            KeyEvent.KEYCODE_DPAD_DOWN -> if (screen.isLive && zapEnabled) { vm.zap(1); true } else false
                            else -> false
                        }
                    } else when (code) {
                        KeyEvent.KEYCODE_DPAD_UP -> { if (screen.isLive && zapEnabled) vm.zap(-1) else show(); true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { if (screen.isLive && zapEnabled) vm.zap(1) else show(); true }
                        KeyEvent.KEYCODE_DPAD_LEFT -> { if (pb.canSeek && !screen.isLive) vm.repo.seekBy(-10_000); show(); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { if (pb.canSeek && !screen.isLive) vm.repo.seekBy(30_000); show(); true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { show(); true }
                        else -> false
                    }
                }
            }
    ) {
        AndroidView(
            factory = { c -> PlayerView(c).apply { useController = false; setShutterBackgroundColor(android.graphics.Color.BLACK); isFocusable = false; isFocusableInTouchMode = false } },
            update = { v ->
                v.player = player
                // Subtitle appearance comes from Settings > Subtitles; embedded styles are ignored so the user's choices always win.
                v.subtitleView?.apply {
                    setApplyEmbeddedStyles(false); setApplyEmbeddedFontSizes(false)
                    setStyle(subStyle.toCaptionStyle()); setFractionalTextSize(subStyle.size.fraction); setBottomPaddingFraction(subStyle.position.bottomPadding)
                }
                v.resizeMode = when (settings?.resizeMode ?: 0) { 1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM; 2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL; else -> AspectRatioFrameLayout.RESIZE_MODE_FIT }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        // Picture brightness: a black layer over the video. (An app cannot change a TV's backlight, so this dims the picture itself.)
        if (brightness < 0.995f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = ((1f - brightness) * 0.9f).coerceIn(0f, 0.8f))))

        // Loading / buffering never blocks the controls.
        if (screen.fatal == null && pb.issue == null && (pb.status == PlaybackStatus.LOADING || pb.status == PlaybackStatus.BUFFERING))
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(if (pb.retryAttempt > 0) "Reconnecting (attempt ${pb.retryAttempt})\u2026" else if (pb.streamIndex > 0) "Trying backup stream ${pb.streamIndex + 1} of ${pb.streamCount}\u2026" else if (pb.status == PlaybackStatus.BUFFERING) "Buffering\u2026" else "Starting\u2026", Modifier.padding(top = 12.dp), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                if (pb.suggestAuto) Text(tr("Slow connection? Press OK, open Quality and choose Auto."), Modifier.padding(top = 8.dp), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }

        val problem = screen.fatal ?: pb.issue?.message
        if (problem != null) Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
            StatePanel(tr("Playback problem"), problem + (pb.issue?.let { "  (${it.code})" } ?: ""), Modifier.fillMaxWidth(), isError = true) {
                TvButton(tr("Retry"), { vm.retry() }, icon = Icons.Filled.Replay, focusRequester = firstControl)
                if (screen.isLive && screen.canZap) TvButton(tr("Next channel"), { vm.zap(1) }, primary = false, icon = Icons.Filled.SkipNext)
                TvButton(tr("Change source"), { nav.navigateTop(Routes.SOURCES) }, primary = false)
                TvButton(tr("Back"), { nav.popBackStack() }, primary = false)
            }
        }

        AnimatedVisibility(banner && !overlay && problem == null && !inPip, enter = fadeIn(tween(200)), exit = fadeOut(tween(200)), modifier = Modifier.align(Alignment.TopStart)) {
            Row(Modifier.padding(Dimens.SafeH, Dimens.SafeV).background(Color(0xCC0E1116), RoundedCornerShape(14.dp)).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                screen.number?.let { Text(it.toString().padStart(3, '0'), style = MaterialTheme.typography.headlineSmall, color = LocalIptvColors.current.focusGlow) }
                Text(screen.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        AnimatedVisibility(overlay && problem == null && !inPip, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (adjusting) 0f else 1f }) {
                // Top info strip
                Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xEB0E1116), Color.Transparent))).padding(horizontal = Dimens.SafeH, vertical = Dimens.SafeV),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (screen.logo != null || screen.isLive) Artwork(screen.logo, screen.title, Modifier.size(56.dp))
                    Column(Modifier.weight(1f)) {
                        Text(screen.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        screen.nowNext?.now?.takeIf { screen.isLive }?.let { Text("${screen.subtitle.orEmpty()} \u2022 ${it.title}", style = MaterialTheme.typography.bodyLarge, color = Color(0xFFD0D8E4), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            ?: screen.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFD0D8E4), maxLines = 1) }
                    }
                    if (screen.isLive) LiveBadge()
                    quality?.let { Text(it, Modifier.background(Color(0xD90E1116), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = Color.White) }
                    screen.number?.let { Text(it.toString().padStart(3, '0'), style = MaterialTheme.typography.displayMedium, color = LocalIptvColors.current.focusGlow) }
                }
                // Bottom dock
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF70E1116)))).padding(start = Dimens.SafeH, end = Dimens.SafeH, top = 36.dp, bottom = Dimens.SafeV),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val audioLabel = pb.audio.firstOrNull { it.selected }?.label ?: "Auto"
                    val subLabel = if (pb.textEnabled) pb.text.firstOrNull { it.selected }?.label ?: "On" else "Off"
                    if (screen.isLive) {
                        val nn = screen.nowNext
                        if (nn?.now != null) {
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(tr("NOW"), style = MaterialTheme.typography.labelLarge, color = LocalIptvColors.current.focusGlow)
                                Text(nn.now.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                Text("${formatTime(ctx, nn.now.startMs)} \u2013 ${formatTime(ctx, nn.now.endMs)}", style = MaterialTheme.typography.bodyLarge, color = Color(0xFFA7B4C6))
                                Box(Modifier.weight(1f))
                                Text("Audio: $audioLabel \u2022 Subtitles: $subLabel", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA7B4C6))
                            }
                            ProgressLine(nn.now.progress(System.currentTimeMillis()))
                            nn.next?.let { Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(tr("NEXT"), style = MaterialTheme.typography.labelLarge, color = Color(0xFFA7B4C6))
                                Text("${it.title}  \u2022  ${formatTime(ctx, it.startMs)}", style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } }
                        } else Text("No guide data for this channel \u2022 Audio: $audioLabel \u2022 Subtitles: $subLabel", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA7B4C6))
                    } else Text("Audio: $audioLabel \u2022 Subtitles: $subLabel", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA7B4C6))
                    // A seek bar is shown only when the stream reports that it is seekable with a known duration.
                    if (pb.canSeek && dur > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(formatDuration(pos), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        ProgressLine((pos.toFloat() / dur).coerceIn(0f, 1f), Modifier.weight(1f))
                        Text(formatDuration(dur), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    } else if (screen.isLive) Text(tr("Live stream \u2022 not seekable"), style = MaterialTheme.typography.bodySmall, color = Color(0xFFA7B4C6))
                    // One row; labels show on the focused button only so every control fits. Favourite sits directly left of Play/Pause.
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
                        if (screen.isLive && screen.hasLast) RoundIconButton(Icons.Filled.Replay, tr("Last channel"), { vm.lastChannel() }, labelOnFocusOnly = true)
                        RoundIconButton(Icons.Filled.Audiotrack, tr("Audio"), { dialog = Dlg.AUDIO }, enabled = pb.audio.isNotEmpty(), labelOnFocusOnly = true)
                        RoundIconButton(Icons.Filled.Subtitles, tr("Subtitles"), { dialog = Dlg.TEXT }, enabled = pb.text.isNotEmpty() || !screen.isLive, labelOnFocusOnly = true)
                        RoundIconButton(Icons.Filled.VolumeUp, tr("Volume"), { returnFocusTo = volumeButton; dialog = Dlg.VOLUME }, focusRequester = volumeButton, labelOnFocusOnly = true)
                        RoundIconButton(Icons.Filled.BrightnessMedium, tr("Brightness"), { returnFocusTo = brightnessButton; dialog = Dlg.BRIGHTNESS }, focusRequester = brightnessButton, labelOnFocusOnly = true)
                        // Mute sits directly left of Favourite.
                        RoundIconButton(if (volume <= 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeMute, tr(if (volume <= 0f) "Unmute" else "Mute"), { toggleMute(); touched++ }, focusRequester = muteButton, active = volume <= 0f, labelOnFocusOnly = true)
                        if (screen.kind != "episode") RoundIconButton(if (screen.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, tr(if (screen.isFavorite) "In favorites" else "Favorite"), { vm.toggleFavorite() }, active = screen.isFavorite, labelOnFocusOnly = true)
                        RoundIconButton(if (pb.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, tr(if (pb.isPlaying) "Pause" else "Play"), { vm.repo.togglePlayPause(); touched++ }, size = Dimens.RoundControlPrimary, primary = true, focusRequester = firstControl, labelOnFocusOnly = true)
                        if (screen.isLive && screen.canZap) RoundIconButton(Icons.Filled.Shuffle, tr(if (shuffle) "Random: On" else "Random: Off"), { vm.toggleShuffle() }, active = shuffle, labelOnFocusOnly = true)
                        if (screen.isLive && screen.queue.isNotEmpty()) RoundIconButton(Icons.Filled.ViewList, tr("Channels"), { dialog = Dlg.CHANNELS }, labelOnFocusOnly = true)
                        if (screen.kind == "live" || screen.kind == "movie") RoundIconButton(Icons.Filled.PlaylistAdd, tr("Add to playlist"), { dialog = Dlg.PLAYLIST }, labelOnFocusOnly = true)
                        if (screen.isLive) RoundIconButton(Icons.Filled.CalendarMonth, tr("Guide"), { nav.navigateTop(Routes.EPG) }, labelOnFocusOnly = true)
                        RoundIconButton(Icons.Filled.HighQuality, tr("Quality"), { dialog = Dlg.QUALITY }, labelOnFocusOnly = true)
                        RoundIconButton(Icons.Filled.Settings, tr("Picture"), { dialog = Dlg.SETTINGS }, labelOnFocusOnly = true)
                    }
                }
            }
        }
        if (dialog == Dlg.VOLUME) {
            DisposableEffect(Unit) { onDispose { if (volume > 0f) vm.saveVolume(volume) } }
            LevelPanel(tr("Volume"), if (volume <= 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, volume, { volume = it; vm.applyVolume(it) }, { dialog = Dlg.NONE },
                Modifier.align(Alignment.BottomCenter).padding(bottom = Dimens.SafeV + 24.dp))
        }
        if (dialog == Dlg.BRIGHTNESS) {
            DisposableEffect(Unit) { onDispose { vm.saveBrightness(brightness) } }
            LevelPanel(tr("Brightness"), Icons.Filled.BrightnessMedium, brightness, { brightness = it }, { dialog = Dlg.NONE },
                Modifier.align(Alignment.BottomCenter).padding(bottom = Dimens.SafeV + 24.dp), min = 0.2f)
        }
    }

    when (dialog) {
        Dlg.AUDIO -> OptionDialog(tr("Audio track"), pb.audio.map { it.label to it.id }, pb.audio.firstOrNull { it.selected }?.id, { vm.repo.selectAudio(it) }, { dialog = Dlg.NONE })
        Dlg.TEXT -> OptionDialog(tr("Subtitles"), listOf(tr("Off") to "off") + pb.text.map { it.label to it.id } + (if (!screen.isLive) listOf(tr("Load subtitle file\u2026") to "__load__") else emptyList()) + listOf(tr("Subtitle style\u2026") to "__style__"), if (!pb.textEnabled) "off" else pb.text.firstOrNull { it.selected }?.id,
            { when (it) { "off" -> vm.repo.selectText(null); "__load__" -> subPicker.launch(arrayOf("application/x-subrip", "text/vtt", "text/*", "application/*", "*/*")); "__style__" -> styleRequested = true; else -> vm.repo.selectText(it) } }, { if (styleRequested) { styleRequested = false; dialog = Dlg.SUBSTYLE } else dialog = Dlg.NONE })
        Dlg.SUBSTYLE -> SubtitleStyleSheet(subStyle, { vm.saveSubtitleStyle(it) }, { dialog = Dlg.NONE })
        Dlg.VOLUME, Dlg.BRIGHTNESS -> Unit   // drawn inline by LevelPanel above
        Dlg.PLAYLIST -> PlaylistPickerDialog(if (screen.kind == "movie") ContentKind.MOVIE else ContentKind.LIVE, screen.itemId, screen.title, { dialog = Dlg.NONE })
        Dlg.QUALITY -> QualitySheet(pb, { vm.setQuality(it) }, { dialog = Dlg.NONE })
        Dlg.SETTINGS -> OptionDialog(tr("Picture"), listOf(tr("Fit (letterbox)") to 0, tr("Zoom (crop)") to 1, tr("Stretch") to 2), settings?.resizeMode ?: 0, { vm.setResize(it) }, { dialog = Dlg.NONE })
        Dlg.CHANNELS -> OptionDialog(tr("Channels"), screen.queue.map { it.name to it.id }, screen.itemId, { vm.playChannel(it) }, { dialog = Dlg.NONE })
        Dlg.EXIT -> ConfirmDialog(tr("Stop watching?"), tr("Playback will stop and you will return to the previous screen."), tr("Exit"), { dialog = Dlg.NONE; nav.popBackStack() }, { dialog = Dlg.NONE })
        Dlg.NONE -> Unit
    }
}

/** Side sheet (bottom-right, like the approved design) for the video quality policy. Hosted in a Dialog so Back and focus work as usual. */
@Composable
private fun QualitySheet(pb: com.iptv.firetv.core.player.PlayerUiState, onPick: (com.iptv.firetv.core.model.QualityMode) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(horizontal = Dimens.SafeH, vertical = Dimens.SafeV), contentAlignment = Alignment.BottomEnd) {
            Column(Modifier.width(400.dp).clip(RoundedCornerShape(24.dp)).background(cs.surface).padding(26.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Video quality"), style = MaterialTheme.typography.headlineSmall, color = cs.onSurface)
                com.iptv.firetv.core.model.QualityMode.entries.forEach { m ->
                    val sel = m == pb.quality
                    com.iptv.firetv.core.ui.FocusSurface({ onPick(m); onDismiss() }, Modifier.fillMaxWidth().height(Dimens.OptionRowHeight), RoundedCornerShape(16.dp), 1.02f, restBorder = false,
                        containerColor = if (sel) cs.onBackground else Color.Transparent,
                        focusedContainerColor = if (sel) cs.onBackground else com.iptv.firetv.core.ui.LocalIptvColors.current.elevated,
                        focusRequester = if (sel) first else null) {
                        Text(tr(m.label), Modifier.align(Alignment.CenterStart).padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyLarge, color = if (sel) cs.background else cs.onSurface)
                    }
                }
                val now = pb.video?.let { v -> listOfNotNull(v.label, v.bitrateKbps?.let { "%.1f Mbps".format(it / 1000f) }, v.frameRate?.let { "${it.toInt()} fps" }).joinToString(" \u00b7 ") }
                Text(listOfNotNull(now?.let { tr("Playing") + " " + it }, pb.offeredHeights.takeIf { it.isNotEmpty() }?.let { tr("Stream offers") + " " + it.joinToString(", ") { h -> "${h}p" } }).joinToString("\n"),
                    Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** Side sheet with the subtitle style editor, so the look can be changed while watching. Changes are saved immediately. */
@Composable
private fun SubtitleStyleSheet(style: SubtitleStyle, onChange: (SubtitleStyle) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val first = remember { FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(horizontal = Dimens.SafeH, vertical = Dimens.SafeV), contentAlignment = Alignment.CenterEnd) {
            Column(Modifier.width(470.dp).clip(RoundedCornerShape(Dimens.PanelRadius)).background(cs.surface).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Subtitle style"), style = MaterialTheme.typography.headlineSmall, color = cs.onSurface)
                // Local copy so every OK press shows its effect at once, even before the saved setting round-trips.
                var local by remember { mutableStateOf(style) }
                SubtitleStyleEditor(local, { local = it; onChange(it) }, firstFocus = first, previewHeight = 90.dp)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
