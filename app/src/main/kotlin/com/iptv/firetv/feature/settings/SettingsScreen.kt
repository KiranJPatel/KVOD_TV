package com.iptv.firetv.feature.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.iptv.firetv.BuildConfig
import com.iptv.firetv.app.Routes
import com.iptv.firetv.app.navigateTop
import com.iptv.firetv.core.common.DiagnosticsLog
import com.iptv.firetv.core.common.tr
import com.iptv.firetv.core.model.AccentColor
import com.iptv.firetv.core.model.AppTheme
import com.iptv.firetv.core.model.BufferProfile
import com.iptv.firetv.core.model.ThemeMode
import com.iptv.firetv.core.ui.AppFonts
import com.iptv.firetv.core.ui.ConfirmDialog
import com.iptv.firetv.core.ui.Dimens
import com.iptv.firetv.core.ui.FocusSurface
import com.iptv.firetv.core.ui.LocalIptvColors
import com.iptv.firetv.core.ui.NavItem
import com.iptv.firetv.core.ui.OptionDialog
import com.iptv.firetv.core.ui.RegisterContentEntry
import com.iptv.firetv.core.ui.ScreenFrame
import com.iptv.firetv.core.ui.SegmentedChoice
import com.iptv.firetv.core.ui.SettingRow
import com.iptv.firetv.core.ui.SettingsGroup
import com.iptv.firetv.core.ui.SubtitleStyleEditor
import com.iptv.firetv.core.ui.SwitchRow
import com.iptv.firetv.core.ui.TvButton
import com.iptv.firetv.core.ui.TvDialog
import com.iptv.firetv.core.ui.tonesFor
import com.iptv.firetv.data.AppSettings
import com.iptv.firetv.data.SettingKeys
import com.iptv.firetv.data.SettingsRepository
import com.iptv.firetv.data.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository, private val user: UserDataRepository, @ApplicationContext private val ctx: Context,
    private val backup: com.iptv.firetv.data.BackupRepository,
    private val metadata: com.iptv.firetv.data.MetadataRepository
) : ViewModel() {
    val hasMetadataKey = MutableStateFlow(false)
    init { viewModelScope.launch { hasMetadataKey.value = metadata.hasKey() } }
    fun saveMetadataKey(k: String?) { viewModelScope.launch { metadata.saveKey(k); hasMetadataKey.value = metadata.hasKey(); if (!hasMetadataKey.value) repo.set(SettingKeys.METADATA_ENABLED, false) } }
    val message = MutableStateFlow<String?>(null)
    fun export(uri: android.net.Uri) { viewModelScope.launch { message.value = try { backup.export(uri); "Backup saved." } catch (e: com.iptv.firetv.core.common.AppException) { e.userMessage } } }
    fun restore(uri: android.net.Uri) { viewModelScope.launch { message.value = try { backup.restore(uri) } catch (e: com.iptv.firetv.core.common.AppException) { e.userMessage } } }
    val settings: StateFlow<AppSettings?> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val cacheBytes = MutableStateFlow(0L)
    init { refreshCache() }

    fun <T> set(key: androidx.datastore.preferences.core.Preferences.Key<T>, v: T) { viewModelScope.launch { repo.set(key, v) } }
    fun refreshCache() { viewModelScope.launch { cacheBytes.value = withContext(Dispatchers.IO) { ctx.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } } } }
    fun clearCache() { viewModelScope.launch { withContext(Dispatchers.IO) { ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }; refreshCache() } }
    fun reset() { viewModelScope.launch { repo.resetAll() } }
    fun clearSearches() { viewModelScope.launch { user.clearSearches() } }
    fun setSubtitle(st: com.iptv.firetv.core.player.SubtitleStyle) { viewModelScope.launch { repo.setSubtitleStyle(st) } }
}

private val zones = listOf("System default" to "", "India (Asia/Kolkata)" to "Asia/Kolkata", "UTC" to "UTC", "London" to "Europe/London", "Berlin / Paris" to "Europe/Berlin", "Dubai" to "Asia/Dubai",
    "Singapore" to "Asia/Singapore", "Tokyo" to "Asia/Tokyo", "Sydney" to "Australia/Sydney", "New York" to "America/New_York", "Los Angeles" to "America/Los_Angeles")

private class Section(val title: String, val icon: ImageVector)
private val sections = listOf(
    Section("Appearance", Icons.Filled.Palette), Section("Subtitles", Icons.Filled.Subtitles), Section("Playback", Icons.Filled.PlayCircle),
    Section("Menu & navigation", Icons.Filled.ViewQuilt), Section("Guide (EPG)", Icons.Filled.CalendarMonth), Section("Parental controls & profiles", Icons.Filled.Lock),
    Section("Storage", Icons.Filled.Storage), Section("Privacy & diagnostics", Icons.Filled.PrivacyTip), Section("Artwork & descriptions", Icons.Filled.Image), Section("About", Icons.Filled.Info)
)

@Composable
private fun Note(text: String) = Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** One theme in the gallery: a miniature of the app in that theme's colours and fonts, with a check mark on the chosen one. */
@Composable
private fun ThemeCard(theme: AppTheme, dark: Boolean, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, focus: FocusRequester? = null) {
    val t = tonesFor(theme, dark)
    val fonts = AppFonts.forTheme(theme)
    FocusSurface(onClick, modifier.height(138.dp), RoundedCornerShape(Dimens.CardRadius + 2.dp), 1.04f, containerColor = t.bg, focusedContainerColor = t.bg, focusRequester = focus) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(t.primary))
                Box(Modifier.size(18.dp).clip(CircleShape).background(t.tertiary))
                Box(Modifier.size(18.dp).clip(CircleShape).background(t.primaryContainer))
                Box(Modifier.weight(1f))
                if (selected) Box(Modifier.size(24.dp).clip(CircleShape).background(t.primary), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = t.bg) }
            }
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(t.surface).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                Text(tr(theme.label), style = MaterialTheme.typography.titleLarge.copy(fontFamily = fonts.display), color = t.text, maxLines = 1)
            }
            Text(theme.tagline, style = MaterialTheme.typography.bodySmall.copy(fontFamily = fonts.text), color = t.secondary, maxLines = 1)
        }
    }
}

@Composable
fun SettingsScreen(nav: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val cache by vm.cacheBytes.collectAsStateWithLifecycle()
    var dlg by remember { mutableStateOf("") }
    var section by remember { mutableStateOf(0) }
    val msg by vm.message.collectAsStateWithLifecycle()
    val hasKey by vm.hasMetadataKey.collectAsStateWithLifecycle()
    var keyText by remember { mutableStateOf("") }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.export(uri) }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.restore(uri) }
    val navFocus = remember { FocusRequester() }
    val firstTheme = remember { FocusRequester() }
    // Down from the top bar lands on the section list (on the section that is open).
    RegisterContentEntry(navFocus)
    val st = s ?: return
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val previewDark = when (st.themeMode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> systemDark }
    fun onOff(b: Boolean) = tr(if (b) "On" else "Off")

    ScreenFrame(title = tr("Settings")) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Section list
            Column(Modifier.width(310.dp).fillMaxHeight().clip(RoundedCornerShape(Dimens.PanelRadius)).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, LocalIptvColors.current.divider, RoundedCornerShape(Dimens.PanelRadius)).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sections.forEachIndexed { i, sec ->
                    NavItem(tr(sec.title), sec.icon, section == i, { section = i }, focusRequester = if (i == section) navFocus else null, onFocused = { section = i })
                }
            }
            // Section content
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (section) {
                    0 -> {
                        SettingsGroup(tr("Theme")) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                AppTheme.entries.chunked(3).forEachIndexed { r, rowThemes ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        rowThemes.forEachIndexed { c, th ->
                                            ThemeCard(th, previewDark, st.theme == th, { vm.set(SettingKeys.THEME, th.name) }, Modifier.weight(1f), focus = if (r == 0 && c == 0) firstTheme else null)
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(tr("Brightness of the theme"), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                    SegmentedChoice(ThemeMode.entries.map { tr(it.label) to it }, st.themeMode, { vm.set(SettingKeys.THEME_MODE, it.name) })
                                }
                            }
                        }
                        SettingsGroup(tr("Display")) {
                            SettingRow(tr("Accent colour"), { dlg = "accent" }, icon = Icons.Filled.ColorLens, value = tr(st.accent.label), subtitle = tr("Optional: replaces the theme's main colour."))
                            SettingRow(tr("Text size"), { dlg = "text" }, icon = Icons.Filled.FormatSize, value = "${(st.textScale * 100).toInt()}%")
                            SettingRow(tr("Interface size"), { dlg = "ui" }, icon = Icons.Filled.AspectRatio, value = "${(st.uiScale * 100).toInt()}%")
                            SettingRow(tr("Language"), { dlg = "lang" }, icon = Icons.Filled.Language, value = com.iptv.firetv.core.common.I18n.languages.first { it.second == st.language }.first)
                        }
                    }
                    1 -> {
                        SettingsGroup(tr("Subtitle style")) {
                            SubtitleStyleEditor(st.subtitle, { vm.setSubtitle(it) }, Modifier.padding(6.dp))
                        }
                        Note(tr("These choices apply to every video in the player. You can also open Subtitle style from the Subtitles button while watching."))
                    }
                    2 -> SettingsGroup(tr("Playback")) {
                        SwitchRow(tr("Autoplay next episode"), st.autoplayNext, { vm.set(SettingKeys.AUTOPLAY_NEXT, !st.autoplayNext) }, icon = Icons.Filled.SkipNext)
                        SwitchRow(tr("Confirm before exiting playback"), st.confirmExit, { vm.set(SettingKeys.CONFIRM_EXIT, !st.confirmExit) }, icon = Icons.Filled.ExitToApp)
                        SwitchRow(tr("D-pad Up/Down changes channel"), st.dpadZapping, { vm.set(SettingKeys.DPAD_ZAP, !st.dpadZapping) }, icon = Icons.Filled.SwapVert)
                        SwitchRow(tr("Keep the TV awake while a channel is playing"), st.keepScreenAwake, { vm.set(SettingKeys.KEEP_AWAKE, !st.keepScreenAwake) }, icon = Icons.Filled.ScreenLockPortrait,
                            subtitle = tr("Stops the screensaver and sleep timer from interrupting playback."))
                        SettingRow(tr("Overlay hides after"), { dlg = "overlay" }, icon = Icons.Filled.Timer, value = "${st.overlaySeconds} s")
                        SettingRow(tr("Video quality"), { dlg = "quality" }, icon = Icons.Filled.HighQuality, value = tr(st.quality.label))
                        SettingRow(tr("Buffering"), { dlg = "buffer" }, icon = Icons.Filled.Speed, value = tr(st.buffer.label))
                        SettingRow(tr("Automatic retries on errors"), { dlg = "retries" }, icon = Icons.Filled.Replay, value = "${st.maxRetries}")
                    }
                    3 -> {
                        SettingsGroup(tr("Top menu")) {
                            SwitchRow(tr("Show Movies tab"), st.showMoviesTab, { vm.set(SettingKeys.SHOW_MOVIES, !st.showMoviesTab) }, icon = Icons.Filled.Movie, subtitle = tr("Turn off if your playlists have no movies."))
                            SwitchRow(tr("Show Series tab"), st.showSeriesTab, { vm.set(SettingKeys.SHOW_SERIES, !st.showSeriesTab) }, icon = Icons.Filled.Slideshow, subtitle = tr("Turn off if your playlists have no series."))
                        }
                        SettingsGroup(tr("Live TV")) {
                            SwitchRow(tr("Show channel preview pane"), st.showPreviewPane, { vm.set(SettingKeys.SHOW_PREVIEW_PANE, !st.showPreviewPane) }, icon = Icons.Filled.Visibility,
                                subtitle = tr("The panel on the right with the channel details and what is on now. Turn off for a wider channel list."))
                            SwitchRow(tr("Live video preview in Live TV (uses more memory)"), st.livePreview, { vm.set(SettingKeys.LIVE_PREVIEW, !st.livePreview) }, icon = Icons.Filled.Tv,
                                subtitle = tr("Plays the focused channel, muted, inside the preview pane."))
                        }
                    }
                    4 -> {
                        SettingsGroup(tr("Guide (EPG)")) {
                            SettingRow(tr("EPG time zone"), { dlg = "zone" }, icon = Icons.Filled.Schedule, value = zones.firstOrNull { it.second == st.epgZone }?.first ?: st.epgZone)
                            SwitchRow(tr("Match channels by exact name when tvg-id is missing"), st.epgNameMatching, { vm.set(SettingKeys.EPG_NAME_MATCH, !st.epgNameMatching) }, icon = Icons.Filled.Link)
                        }
                        Note(tr("Times in a guide without a UTC offset are read in this time zone. Re-import the guide after changing it."))
                    }
                    5 -> SettingsGroup(tr("Parental controls & profiles")) {
                        SettingRow(tr("Lock to profile chooser after inactivity"), { dlg = "lock" }, icon = Icons.Filled.Lock, value = if (st.inactivityLockMin == 0) tr("Off") else "${st.inactivityLockMin} min",
                            subtitle = tr("Never locks while something is playing."))
                        SettingRow(tr("Manage profiles, PINs and restrictions"), { nav.navigateTop(Routes.PROFILE_MANAGE) }, icon = Icons.Filled.ManageAccounts, value = tr("Open"))
                    }
                    6 -> {
                        SettingsGroup(tr("Storage")) {
                            SettingRow(tr("Cache (artwork and temporary files)"), { dlg = "clearcache" }, icon = Icons.Filled.CleaningServices, value = "%.1f MB".format(cache / 1048576.0))
                            SettingRow(tr("Playlists / Sources"), { nav.navigateTop(Routes.SOURCES) }, icon = Icons.Filled.PlaylistAdd, value = tr("Open"))
                            SettingRow(tr("Export backup (settings, profiles, sources, favourites, history)"), { exportLauncher.launch("kvod-tv-backup.json") }, icon = Icons.Filled.Backup, value = tr("Save"))
                            SettingRow(tr("Restore backup"), { importLauncher.launch(arrayOf("application/json", "*/*")) }, icon = Icons.Filled.Restore, value = tr("Choose file"))
                        }
                        Note(tr("Backups never contain addresses, usernames, passwords or PINs. Favourites, playlists, Continue Watching and history are saved by title and re-attached after the matching source is imported."))
                        msg?.let { Text(it, Modifier.padding(horizontal = 10.dp), style = MaterialTheme.typography.bodyLarge, color = LocalIptvColors.current.success) }
                    }
                    7 -> {
                        Note(tr("No analytics or crash-reporting SDKs are included. Passwords and account links are stored encrypted with Android Keystore and are removed from logs."))
                        SettingsGroup(tr("Privacy & diagnostics")) {
                            SettingRow(tr("Clear search history"), { vm.clearSearches() }, icon = Icons.Filled.History, value = tr("Clear"))
                            SettingRow(tr("Diagnostics and playback log (kept on this device, redacted)"), { dlg = "log" }, icon = Icons.Filled.Description, value = tr("View"))
                            SettingRow(tr("Playback capability report (decoders, HDR, audio output)"), { dlg = "caps" }, icon = Icons.Filled.Description, value = tr("View"))
                        }
                    }
                    8 -> {
                        SettingsGroup(tr("Artwork & descriptions (optional)")) {
                            SwitchRow(tr("Fill in missing artwork and descriptions from TMDB"), st.metadataEnabled && hasKey, { if (hasKey) vm.set(SettingKeys.METADATA_ENABLED, !st.metadataEnabled) else dlg = "metakey" }, icon = Icons.Filled.Image)
                            SettingRow(tr("Your TMDB API key"), { dlg = "metakey" }, icon = Icons.Filled.Key, value = if (hasKey) tr("Saved (encrypted)") else tr("Not set"))
                        }
                        Note(tr("Off by default. Needs your own free API key from themoviedb.org (nothing is bundled). When on, only the cleaned title and year of items that have no description or artwork are sent to api.themoviedb.org, once per item; results are stored on this device. Playlist data always takes priority."))
                        Note(com.iptv.firetv.data.MetadataRepository.ATTRIBUTION)
                    }
                    else -> SettingsGroup(tr("About KVOD TV")) {
                        SettingRow(tr("Version"), { }, icon = Icons.Filled.Tag, value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        SettingRow(tr("Open-source licences"), { dlg = "licenses" }, icon = Icons.Filled.Gavel, value = tr("View"))
                        SettingRow(tr("Reset app settings"), { dlg = "reset" }, icon = Icons.Filled.RestartAlt, value = tr("Reset"))
                    }
                }
            }
        }
    }

    when (dlg) {
        "lang" -> OptionDialog(tr("Language"), com.iptv.firetv.core.common.I18n.languages, st.language, { vm.set(SettingKeys.LANGUAGE, it) }, { dlg = "" })
        "accent" -> OptionDialog(tr("Accent colour"), AccentColor.entries.map { tr(it.label) to it }, st.accent, { vm.set(SettingKeys.ACCENT, it.name) }, { dlg = "" }, subtitle = tr("Theme colour keeps the colours of the chosen theme."))
        "ui" -> OptionDialog(tr("Interface size"), listOf(tr("Compact 85%") to 0.85f, tr("Standard 100%") to 1f, tr("Large 115%") to 1.15f), st.uiScale, { vm.set(SettingKeys.UI_SCALE, it) }, { dlg = "" })
        "text" -> OptionDialog(tr("Text size"), listOf(tr("Compact 90%") to 0.9f, tr("Standard 100%") to 1f, tr("Large 115%") to 1.15f, tr("Extra large 130%") to 1.3f), st.textScale, { vm.set(SettingKeys.TEXT_SCALE, it) }, { dlg = "" })
        "overlay" -> OptionDialog(tr("Hide overlay after"), listOf(3, 5, 8, 12).map { "$it seconds" to it }, st.overlaySeconds, { vm.set(SettingKeys.OVERLAY_SECONDS, it) }, { dlg = "" })
        "quality" -> OptionDialog(tr("Video quality"), com.iptv.firetv.core.model.QualityMode.entries.map { tr(it.label) to it }, st.quality, { vm.set(SettingKeys.QUALITY, it.name) }, { dlg = "" })
        "buffer" -> OptionDialog(tr("Buffering"), BufferProfile.entries.map { tr(it.label) to it }, st.buffer, { vm.set(SettingKeys.BUFFER, it.name) }, { dlg = "" })
        "retries" -> OptionDialog(tr("Automatic retries"), (0..5).map { "$it" to it }, st.maxRetries, { vm.set(SettingKeys.MAX_RETRIES, it) }, { dlg = "" })
        "zone" -> OptionDialog(tr("EPG time zone"), zones.map { it.first to it.second }, st.epgZone, { vm.set(SettingKeys.EPG_ZONE, it) }, { dlg = "" })
        "lock" -> OptionDialog(tr("Inactivity lock"), listOf(tr("Off") to 0, tr("5 minutes") to 5, tr("15 minutes") to 15, tr("30 minutes") to 30, tr("60 minutes") to 60), st.inactivityLockMin, { vm.set(SettingKeys.INACTIVITY_LOCK, it) }, { dlg = "" })
        "clearcache" -> ConfirmDialog(tr("Clear cache?"), tr("Cached artwork and temporary import files are deleted. Playlists, favourites and history are kept."), tr("Clear"), { vm.clearCache(); dlg = "" }, { dlg = "" })
        "reset" -> ConfirmDialog(tr("Reset app settings?"), tr("Appearance, playback and guide settings return to defaults. Sources, profiles, favourites and history are kept."), tr("Reset"), { vm.reset(); dlg = "" }, { dlg = "" }, destructive = true)
        "metakey" -> TvDialog({ dlg = ""; keyText = "" }, tr("TMDB API key")) {
            com.iptv.firetv.core.ui.TvTextField(keyText, { keyText = it }, "Paste or type your API key (v3)", Modifier.fillMaxWidth(), secret = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(tr("Save"), { vm.saveMetadataKey(keyText); keyText = ""; dlg = "" })
                if (hasKey) TvButton(tr("Remove key"), { vm.saveMetadataKey(null); keyText = ""; dlg = "" }, primary = false)
                TvButton(tr("Cancel"), { dlg = ""; keyText = "" }, primary = false)
            }
        }
        "caps" -> TvDialog({ dlg = "" }, tr("Playback capability report")) {
            val appCtx = androidx.compose.ui.platform.LocalContext.current
            var report by remember { mutableStateOf<String?>(null) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    runCatching { com.iptv.firetv.core.player.DeviceCapabilityDetector(appCtx).collect().also { r -> runCatching { r.saveTo(appCtx) } }.toText() }.getOrElse { "The device could not be queried: ${it.message}" }
                }
            }
            Column(Modifier.height(340.dp).verticalScroll(rememberScrollState())) { Text(report ?: tr("Reading the device\u2026"), style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { TvButton(tr("Close"), { dlg = "" }) }
        }
        "log" -> TvDialog({ dlg = "" }, tr("Diagnostics and playback log (redacted)")) {
            val lines = remember { DiagnosticsLog.snapshot() }
            Column(Modifier.height(340.dp).verticalScroll(rememberScrollState())) { if (lines.isEmpty()) Text(tr("Nothing logged yet."), style = MaterialTheme.typography.bodyLarge); lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) } }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { TvButton(tr("Clear"), { DiagnosticsLog.clear(); dlg = "" }, primary = false); TvButton(tr("Close"), { dlg = "" }) }
        }
        "licenses" -> TvDialog({ dlg = "" }, tr("Open-source licences")) {
            Column(Modifier.height(340.dp).verticalScroll(rememberScrollState())) {
                Text(tr("This app uses the following open-source software, each under the Apache License 2.0 unless noted:\n\n\u2022 AndroidX (Core, Activity, Lifecycle, Navigation, Compose, Room, DataStore, WorkManager)\n\u2022 AndroidX Media3 (ExoPlayer, HLS, DASH, RTSP, UI, OkHttp data source)\n\u2022 Kotlin and kotlinx.coroutines / kotlinx.serialization\n\u2022 Dagger Hilt\n\u2022 OkHttp and Okio\n\u2022 Coil\n\nThe bundled fonts Outfit, Work Sans, Instrument Sans and Lora are licensed under the SIL Open Font License 1.1 (texts are included in the app under assets/licenses/fonts).\n\nFull licence texts: https://www.apache.org/licenses/LICENSE-2.0"), style = MaterialTheme.typography.bodyMedium)
            }
            TvButton(tr("Close"), { dlg = "" })
        }
    }
}
