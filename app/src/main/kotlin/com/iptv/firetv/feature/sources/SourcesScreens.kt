package com.iptv.firetv.feature.sources

import com.iptv.firetv.core.common.tr
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.iptv.firetv.core.ui.ActionTile
import com.iptv.firetv.core.ui.IconBadge
import com.iptv.firetv.core.ui.RegisterContentEntry
import com.iptv.firetv.core.ui.SettingRow
import com.iptv.firetv.core.ui.SettingsGroup
import com.iptv.firetv.core.ui.SwitchRow
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.iptv.firetv.app.Routes
import com.iptv.firetv.core.database.SourceEntity
import com.iptv.firetv.core.model.SourceType
import com.iptv.firetv.core.ui.Chip
import com.iptv.firetv.core.ui.ConfirmDialog
import com.iptv.firetv.core.ui.Dimens
import com.iptv.firetv.core.ui.FocusSurface
import com.iptv.firetv.core.ui.LocalIptvColors
import com.iptv.firetv.core.ui.OptionDialog
import com.iptv.firetv.core.ui.PosterCard
import com.iptv.firetv.core.ui.ScreenFrame
import com.iptv.firetv.core.ui.SectionTitle
import com.iptv.firetv.core.ui.StatePanel
import com.iptv.firetv.core.ui.TvButton
import com.iptv.firetv.core.ui.TvDialog
import com.iptv.firetv.core.ui.TvTextField
import com.iptv.firetv.core.ui.formatTime

import com.iptv.firetv.data.CatalogRepository
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.DateFormat
import java.util.Date

private fun SourceType.icon(): ImageVector = when (this) {
    SourceType.M3U_URL -> Icons.Filled.Link
    SourceType.M3U_FILE -> Icons.Filled.FolderOpen
    SourceType.XTREAM -> Icons.Filled.Dns
    SourceType.XMLTV_URL -> Icons.Filled.CalendarMonth
    SourceType.XMLTV_FILE -> Icons.Filled.EventNote
    SourceType.LOCAL_MEDIA -> Icons.Filled.Folder
}

/** Small tinted statistic (channels, movies, series, guide entries) shown at the top of the source details. */
@Composable
private fun StatTile(value: Int, label: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(Dimens.CardRadius)).background(LocalIptvColors.current.elevated).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = cs.onSurface)
        Text(tr(label), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun SourcesScreen(nav: NavHostController, vm: SourcesViewModel = hiltViewModel()) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val settings by vm.appSettings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var renameDialog by remember { mutableStateOf(false) }
    var removeDialog by remember { mutableStateOf(false) }
    var freqDialog by remember { mutableStateOf(false) }
    var diagDialog by remember { mutableStateOf(false) }
    var catDialog by remember { mutableStateOf(false) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val sel = sources.firstOrNull { it.id == selectedId } ?: sources.firstOrNull()
    val addFocus = remember { FocusRequester() }
    RegisterContentEntry(addFocus)
    val cs = MaterialTheme.colorScheme

    ScreenFrame(title = tr("Playlists / Sources")) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(400.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(tr("Add a source"), { nav.navigate(Routes.ADD_CHOOSER) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add, focusRequester = addFocus)
                if (sources.isEmpty()) Text(tr("No sources yet. Add a playlist, an Xtream account or a guide."), Modifier.padding(8.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                    items(sources, key = { it.id }) { s ->
                        val type = SourceType.valueOf(s.type)
                        val chosen = s.id == sel?.id
                        FocusSurface({ selectedId = s.id }, Modifier.fillMaxWidth().height(80.dp), RoundedCornerShape(Dimens.PanelRadius - 4.dp), 1.02f, restBorder = false,
                            containerColor = if (chosen) cs.primaryContainer else cs.surface, focusedContainerColor = if (chosen) cs.primaryContainer else LocalIptvColors.current.elevated) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                IconBadge(type.icon(), container = if (chosen) cs.primary else cs.primaryContainer, tint = if (chosen) cs.onPrimary else cs.onPrimaryContainer)
                                Column(Modifier.weight(1f)) {
                                    Text(s.name + if (settings?.activeSourceId == s.id) "  \u2022 " + tr("active") else "", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (chosen) cs.onPrimaryContainer else cs.onSurface)
                                    Text(statusLine(s, busy[s.id]), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = if (s.lastStatus == "FAILED") cs.error else if (chosen) cs.onPrimaryContainer.copy(alpha = 0.8f) else cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = 6.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (sel == null) StatePanel(tr("Nothing selected"), tr("Add a source to get started."), Modifier.fillMaxWidth())
                else {
                    val running = busy[sel.id]
                    val type = SourceType.valueOf(sel.type)
                    val last = sel.lastRefreshAt?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) } ?: tr("never")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconBadge(type.icon(), size = 64.dp)
                        Column(Modifier.weight(1f)) {
                            Text(sel.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onBackground)
                            Text(type.label + (sel.displayHost?.let { " \u2022 $it" } ?: ""), style = MaterialTheme.typography.titleSmall, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(sel.channelCount, "Channels", Modifier.weight(1f)); StatTile(sel.vodCount, "Movies", Modifier.weight(1f))
                        StatTile(sel.seriesCount, "Series", Modifier.weight(1f)); StatTile(sel.programCount, "Guide entries", Modifier.weight(1f))
                    }
                    Text("Status: ${if (!sel.enabled) "disabled" else sel.lastStatus ?: "not imported yet"}   \u2022   Last successful refresh: $last   \u2022   Auto refresh: " + if (sel.refreshHours <= 0) "off" else "every ${sel.refreshHours} h",
                        Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    sel.lastError?.let { Text(it, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyLarge, color = cs.error) }
                    running?.let { Text("Working: $it", Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyLarge, color = cs.primary) }
                    message?.let { Text(it, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyMedium, color = LocalIptvColors.current.success) }

                    SettingsGroup(tr("Update")) {
                        if (type != SourceType.LOCAL_MEDIA) SettingRow(tr("Refresh"), { vm.refresh(sel.id) }, icon = Icons.Filled.Refresh, enabled = running == null, subtitle = tr("Download the playlist or guide again now."))
                        if (type == SourceType.M3U_URL || type == SourceType.XTREAM || type == SourceType.XMLTV_URL) SettingRow(tr("Refresh frequency"), { freqDialog = true }, icon = Icons.Filled.Schedule,
                            value = if (sel.refreshHours <= 0) tr("Manual only") else "${sel.refreshHours} h")
                        if (type != SourceType.LOCAL_MEDIA) SettingRow(tr("Edit address / credentials"), { nav.navigate(Routes.addSource(type.name, sel.id)) }, icon = Icons.Filled.Edit)
                    }
                    SettingsGroup(tr("Use")) {
                        SwitchRow(tr("Enabled"), sel.enabled, { vm.setEnabled(sel.id, !sel.enabled) }, icon = Icons.Filled.PowerSettingsNew)
                        if (type != SourceType.LOCAL_MEDIA && type != SourceType.XMLTV_URL && type != SourceType.XMLTV_FILE) {
                            if (settings?.activeSourceId == sel.id) SettingRow(tr("Use all sources"), { vm.setActive(-1) }, icon = Icons.Filled.Tv, subtitle = tr("Only this source is shown right now."))
                            else SettingRow(tr("Use only this source"), { vm.setActive(sel.id) }, icon = Icons.Filled.Tv)
                        }
                        SettingRow(tr("Move up"), { vm.move(sel.id, -1) }, icon = Icons.Filled.ArrowUpward)
                        SettingRow(tr("Move down"), { vm.move(sel.id, 1) }, icon = Icons.Filled.ArrowDownward)
                    }
                    SettingsGroup(tr("Manage")) {
                        SettingRow(tr("Rename"), { renameDialog = true }, icon = Icons.Filled.DriveFileRenameOutline)
                        if (type == SourceType.M3U_URL || type == SourceType.M3U_FILE) SettingRow(tr("Category types"), { vm.loadCategories(sel.id); catDialog = true }, icon = Icons.Filled.Category)
                        SettingRow(tr("Diagnostics"), { vm.loadHistory(sel.id); diagDialog = true }, icon = Icons.Filled.BugReport)
                        SettingRow(tr("Remove source"), { removeDialog = true }, icon = Icons.Filled.Delete)
                    }
                }
            }
        }
    }
    val s = sel
    if (renameDialog && s != null) RenameDialog(s.name, { renameDialog = false }) { vm.rename(s.id, it); renameDialog = false }
    if (freqDialog && s != null) OptionDialog(tr("Refresh frequency"), listOf(tr("Manual only") to 0, tr("Every 6 hours") to 6, tr("Every 12 hours") to 12, tr("Daily") to 24, tr("Weekly") to 168), s.refreshHours, { vm.setRefreshHours(s.id, it) }, { freqDialog = false })
    if (diagDialog && s != null) TvDialog({ diagDialog = false }, tr("Import diagnostics")) {
        val ctx = LocalContext.current
        if (history.isEmpty()) Text(tr("No imports recorded yet."), style = MaterialTheme.typography.bodyLarge)
        history.forEach { j -> Text("${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(j.startedAt))} \u2022 ${j.status} \u2022 ${j.total} items, ${j.invalid} invalid, ${j.duplicates} duplicates" + (j.message?.let { " \u2022 $it" } ?: ""), style = MaterialTheme.typography.bodyMedium) }
        TvButton(tr("Close"), { diagDialog = false })
    }
    if (catDialog && s != null) TvDialog({ catDialog = false }, tr("Category types")) {
        Text(tr("Fix groups that were classified wrongly (for example a movie channel listed as a movie). Select a group to cycle Auto \u2192 Live \u2192 Movies \u2192 Series. Changes apply on the next refresh."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.height(300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (categories.isEmpty()) Text(tr("No categories yet. Import the playlist first."), style = MaterialTheme.typography.bodyLarge)
            categories.take(150).forEach { c ->
                FocusSurface({ vm.cycleCategory(s.id, c) }, Modifier.fillMaxWidth().height(Dimens.OptionRowHeight), RoundedCornerShape(12.dp), 1.01f) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.name} (${c.count})", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        Text(if (c.override == null) "Auto: " + c.origin.lowercase() else "Forced: " + c.override.lowercase(), style = MaterialTheme.typography.labelLarge, color = if (c.override == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(tr("Apply now (refresh)"), { vm.refresh(s.id); catDialog = false })
            TvButton(tr("Close"), { catDialog = false }, primary = false)
        }
    }
    if (removeDialog && s != null) TvDialog({ removeDialog = false }, "Remove \u201C${s.name}\u201D?") {
        Text(tr("The catalogue and cached guide data of this source are deleted. You can also remove favourites and watch history that point to it."), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val cancel = remember { FocusRequester() }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(tr("Cancel"), { removeDialog = false }, primary = false, focusRequester = cancel)
            TvButton(tr("Remove, keep favourites"), { vm.remove(s.id, false); removeDialog = false })
            TvButton(tr("Remove everything"), { vm.remove(s.id, true); removeDialog = false }, primary = false)
        }
        LaunchedEffect(Unit) { cancel.requestFocus() }
    }
}

private fun statusLine(s: SourceEntity, running: String?): String = when {
    running != null -> "Working\u2026 $running"
    !s.enabled -> "Disabled"
    s.lastStatus == "NEEDS_SETUP" -> "Needs setup \u2022 edit to add address or credentials"
    s.lastStatus == "FAILED" -> "Failed: ${s.lastError ?: "see diagnostics"}"
    s.lastStatus == "OK" -> "${SourceType.valueOf(s.type).label} \u2022 ${s.channelCount + s.vodCount + s.seriesCount + s.programCount} items"
    else -> SourceType.valueOf(s.type).label
}

/** Full-screen "Add a source" chooser: one tile per kind of source, with a sentence on what it is for. */
@Composable
fun AddSourceChooserScreen(nav: NavHostController, vm: SourcesViewModel = hiltViewModel()) {
    val first = remember { FocusRequester() }
    RegisterContentEntry(first)
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    fun pick(t: String) {
        if (t == "SAMPLE") { vm.addSample(); nav.popBackStack() }
        // The chooser is replaced by the form, so Back from the form returns to the sources list, not to this chooser.
        else nav.navigate(Routes.addSource(t)) { popUpTo(Routes.ADD_CHOOSER) { inclusive = true } }
    }
    ScreenFrame(title = tr("Add a source")) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(tr("What would you like to add? Playlists bring channels, movies and series; a guide adds programme information."), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val tiles = listOf(
                Triple("M3U_URL", "Playlist URL", "Load channels from a web address (M3U / M3U8)." ) to Icons.Filled.Link,
                Triple("M3U_FILE", "Playlist file", "Import a playlist from a USB drive or storage.") to Icons.Filled.FolderOpen,
                Triple("XTREAM", "Xtream account", "Sign in with the details your provider gave you.") to Icons.Filled.Dns,
                Triple("XMLTV_URL", "Guide URL", "Add programme information (XMLTV) from a web address.") to Icons.Filled.CalendarMonth,
                Triple("XMLTV_FILE", "Guide file", "Import an XMLTV guide file from storage.") to Icons.Filled.EventNote,
                Triple("SAMPLE", "Sample data", "Try the app with a fictitious demo playlist.") to Icons.Filled.Science
            )
            tiles.chunked(3).forEachIndexed { r, rowTiles ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowTiles.forEachIndexed { c, (t, icon) ->
                        ActionTile(tr(t.second), tr(t.third), icon, { pick(t.first) }, Modifier.weight(1f).height(190.dp), focusRequester = if (r == 0 && c == 0) first else null, accent = t.first == "M3U_URL")
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    TvDialog(onDismiss, tr("Rename source")) {
        TvTextField(text, { text = it }, tr("Name"), Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { TvButton(tr("Cancel"), onDismiss, primary = false); TvButton(tr("Save"), { onSave(text) }) }
    }
}

// ----------------------------------------------------------------------------------- add wizard

@Composable
fun AddSourceScreen(nav: NavHostController, vm: AddSourceViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.pickFile(uri) }
    val primaryFocus = remember { FocusRequester() }
    var phoneDialog by remember { mutableStateOf(false) }
    var removeDialog by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    // New URL / Xtream source: start in the address field so the user can type straight away. Otherwise focus the main action.
    LaunchedEffect(s.stage) { kotlinx.coroutines.delay(80); runCatching { if (s.stage == Stage.FORM && s.editId <= 0 && !s.isFileType) fieldFocus.requestFocus() else primaryFocus.requestFocus() } }
    androidx.activity.compose.BackHandler { if (s.stage == Stage.FORM || s.stage == Stage.DONE) { vm.cancel(); nav.popBackStack() } else vm.backToForm() }

    if (removeDialog) TvDialog({ removeDialog = false }, tr("Remove this source?")) {
        Text(tr("The catalogue and cached guide data of this source are deleted. You can also remove favourites and watch history that point to it."), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val cancel = remember { FocusRequester() }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(tr("Cancel"), { removeDialog = false }, Modifier.fillMaxWidth(), primary = false, focusRequester = cancel)
            TvButton(tr("Remove, keep favourites"), { removeDialog = false; vm.remove(false) { nav.popBackStack() } }, Modifier.fillMaxWidth(), primary = false)
            TvButton(tr("Remove everything"), { removeDialog = false; vm.remove(true) { nav.popBackStack() } }, Modifier.fillMaxWidth(), primary = false, danger = true)
        }
        LaunchedEffect(Unit) { cancel.requestFocus() }
    }
    if (phoneDialog) PhoneEntryDialog({ url, name -> vm.edit { it.copy(url = url, name = it.name.ifBlank { name }) }; phoneDialog = false }, { phoneDialog = false })
    ScreenFrame(title = (if (s.editId > 0) "Edit " else "Add ") + s.type.label) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.width(320.dp).fillMaxHeight().clip(RoundedCornerShape(Dimens.PanelRadius)).background(MaterialTheme.colorScheme.primaryContainer).padding(26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconBadge(s.type.icon(), size = 64.dp, container = MaterialTheme.colorScheme.primary, tint = MaterialTheme.colorScheme.onPrimary)
            Text(s.type.label, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(tr(when (s.type) {
                SourceType.M3U_URL -> "Load a playlist from a web address (M3U / M3U8)."
                SourceType.M3U_FILE -> "Import a playlist file from storage or a USB drive."
                SourceType.XTREAM -> "Connect the Xtream-compatible account your provider gave you."
                SourceType.XMLTV_URL -> "Add a programme guide from a web address."
                SourceType.XMLTV_FILE -> "Import a programme guide file (XMLTV)."
                else -> ""
            }), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
            Spacer(Modifier.weight(1f))
            Stepper(when (s.stage) { Stage.FORM -> 0; Stage.WORKING -> 1; Stage.PREVIEW -> 2; else -> 3 }, listOf("Source", if (s.isFileType) "Read" else "Test", "Preview", "Import"))
        }
        Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(Dimens.PanelRadius)).background(MaterialTheme.colorScheme.surface).border(1.dp, LocalIptvColors.current.divider, RoundedCornerShape(Dimens.PanelRadius)).verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            s.error?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) }
            when (s.stage) {
                Stage.FORM -> {
                    // Everything for one source type fits on one screen at 1280x720 dp: two fields per row, one action row.
                    val half = Modifier.weight(1f)
                    when {
                        s.isFileType -> {
                            TvTextField(s.name, { v -> vm.edit { it.copy(name = v) } }, tr("Display name (optional)"), Modifier.fillMaxWidth(0.5f))
                            Text(s.pickedName?.let { "Selected: $it" } ?: "Choose a ${if (s.type == SourceType.M3U_FILE) ".m3u / .m3u8 / .txt playlist" else ".xml / .xmltv guide"} from internal storage, a USB drive or a file provider app. No storage permission is needed.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TvButton(tr("Choose file"), { picker.launch(arrayOf("*/*")) }, icon = Icons.Filled.Folder, focusRequester = primaryFocus)
                        }
                        s.type == SourceType.XTREAM -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                TvTextField(s.url, { v -> vm.edit { it.copy(url = v) } }, tr("Server address (e.g. https://host:port)"), half, focusRequester = fieldFocus)
                                TvTextField(s.name, { v -> vm.edit { it.copy(name = v) } }, tr("Display name (optional)"), half)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                TvTextField(s.username, { v -> vm.edit { it.copy(username = v) } }, tr("Username"), half)
                                TvTextField(s.password, { v -> vm.edit { it.copy(password = v) } }, if (s.editId > 0) "Password (leave empty to keep)" else "Password", half, secret = !s.showSecrets)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Chip(if (s.showSecrets) "Hide password" else "Show password", false, { vm.edit { it.copy(showSecrets = !it.showSecrets) } })
                                Chip("Live format: " + s.liveExt, false, { vm.edit { it.copy(liveExt = if (it.liveExt == "m3u8") "ts" else "m3u8") } })
                            }
                        }
                        else -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                TvTextField(s.url, { v -> vm.edit { it.copy(url = v) } }, tr("Address (https://\u2026)"), half, secret = s.editId > 0 && !s.showSecrets, keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri, focusRequester = fieldFocus)
                                TvTextField(s.name, { v -> vm.edit { it.copy(name = v) } }, tr("Display name (optional)"), half)
                            }
                            TvTextField(s.userAgent, { v -> vm.edit { it.copy(userAgent = v) } }, tr("Custom user-agent (only if your provider requires it)"), Modifier.fillMaxWidth(0.5f))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Chip(tr("Enter with your phone (QR)"), false, { phoneDialog = true })
                                if (s.editId > 0) Chip(if (s.showSecrets) "Hide address" else "Show address", false, { vm.edit { it.copy(showSecrets = !it.showSecrets) } })
                            }
                        }
                    }
                    if (s.type == SourceType.XMLTV_URL || s.type == SourceType.XMLTV_FILE || s.type == SourceType.XTREAM) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Guide time offset"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Chip(tr("\u2212 30 min"), false, { vm.edit { it.copy(epgOffsetMin = (it.epgOffsetMin - 30).coerceAtLeast(-720)) } })
                        Text(if (s.epgOffsetMin == 0) "None" else (if (s.epgOffsetMin > 0) "+" else "\u2212") + "%d:%02d".format(Math.abs(s.epgOffsetMin) / 60, Math.abs(s.epgOffsetMin) % 60), style = MaterialTheme.typography.titleMedium)
                        Chip(tr("+ 30 min"), false, { vm.edit { it.copy(epgOffsetMin = (it.epgOffsetMin + 30).coerceAtMost(720)) } })
                    }
                    if (s.httpWarning) Text(tr("\u26A0 This address uses plain HTTP. Anything sent to or from it, including credentials, can be read on your network. Use HTTPS if your provider offers it."),
                        Modifier.fillMaxWidth().background(LocalIptvColors.current.warning.copy(alpha = 0.14f), RoundedCornerShape(12.dp)).padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = LocalIptvColors.current.warning)
                    if (s.type != SourceType.M3U_FILE && s.type != SourceType.XMLTV_FILE) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Refresh"), Modifier.padding(end = 6.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        listOf("Manual" to 0, "6 h" to 6, "12 h" to 12, "Daily" to 24, "Weekly" to 168).forEach { (l, h) -> Chip(l, s.refreshHours == h, { vm.edit { it.copy(refreshHours = h) } }) }
                    }
                    // One action row: main action first (it gets focus), Cancel next, destructive action at the far end.
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!s.isFileType) TvButton(if (s.type == SourceType.XTREAM) "Test connection" else "Test & preview", { if (s.type == SourceType.XTREAM) vm.testXtream() else vm.testUrl() }, focusRequester = primaryFocus)
                        TvButton(tr("Cancel"), { vm.cancel(); nav.popBackStack() }, primary = false)
                        Spacer(Modifier.weight(1f))
                        if (s.editId > 0) TvButton(tr("Remove this source"), { removeDialog = true }, primary = false, danger = true, icon = Icons.Filled.Delete)
                    }
                }
                Stage.WORKING, Stage.IMPORTING -> {
                    Text(if (s.stage == Stage.WORKING) "Please wait" else "Importing", style = MaterialTheme.typography.headlineSmall)
                    Text(s.progress, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.material3.LinearProgressIndicator(Modifier.width(520.dp), color = MaterialTheme.colorScheme.primary)
                    TvButton(tr("Cancel"), { vm.backToForm() }, primary = false, focusRequester = primaryFocus)
                }
                Stage.PREVIEW -> {
                    s.preview?.let { p ->
                        Text("${p.fileName} \u2022 ${p.sizeBytes / 1024} KB", style = MaterialTheme.typography.titleMedium)
                        Text("${p.stats.entries} entries: ${p.stats.live} live \u2022 ${p.stats.movies} movies \u2022 ${p.stats.series} series/episodes \u2022 ${p.stats.unknown} unclassified", style = MaterialTheme.typography.bodyLarge)
                        Text("${p.stats.groups.size} groups \u2022 ${p.duplicates} duplicates \u2022 ${p.stats.invalid} invalid entries \u2022 ${p.stats.epgUrls.size} guide reference(s)", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        p.sample.take(5).forEach { e -> Text("\u2022 ${e.name}  (${e.kind.name.lowercase()}${e.group?.let { ", $it" } ?: ""})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (p.issues.isNotEmpty()) {
                            Text("Issues found (first ${minOf(5, p.issues.size)} shown):", style = MaterialTheme.typography.labelLarge, color = LocalIptvColors.current.warning)
                            p.issues.take(5).forEach { Text("line ${it.line}: ${it.message}", style = MaterialTheme.typography.bodySmall, color = LocalIptvColors.current.warning) }
                        }
                        s.epgUrls.forEach { u -> Chip("Also add guide ${com.iptv.firetv.core.common.Redactor.url(u)}", false, { vm.addEpgSource(u) }) }
                    }
                    s.xmltv?.let { x -> Text("${x.fileName} \u2022 ${x.channels} channels \u2022 ${x.programmes} programmes" + (if (x.skipped > 0) " \u2022 ${x.skipped} skipped" else "") + (x.error?.let { " \u2022 warning: $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                        Text(tr("Times without a UTC offset are read in the EPG time zone chosen in Settings."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    s.account?.let { a -> Text("Connection OK" + (a.status?.let { " \u2022 status: $it" } ?: "") + (a.expiresAt?.let { " \u2022 expires " + DateFormat.getDateInstance().format(Date(it)) } ?: "") + (a.maxConnections?.let { " \u2022 max connections: $it" } ?: ""), style = MaterialTheme.typography.titleMedium, color = LocalIptvColors.current.success) }
                    ConsentRow(s.consent) { vm.edit { st -> st.copy(consent = !st.consent) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton(if (s.httpWarning) "Import anyway (HTTP)" else "Import", { vm.commit() }, enabled = s.consent, focusRequester = primaryFocus)
                        TvButton(tr("Retry"), { vm.backToForm() }, primary = false)
                        TvButton(tr("Cancel"), { vm.cancel(); nav.popBackStack() }, primary = false)
                    }
                }
                Stage.DONE -> {
                    Text(tr("Done"), style = MaterialTheme.typography.headlineSmall, color = LocalIptvColors.current.success)
                    Text(s.result.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    TvButton(tr("Finish"), { nav.popBackStack() }, focusRequester = primaryFocus)
                }
            }
        }
        }
    }
}

@Composable
private fun ConsentRow(checked: Boolean, onToggle: () -> Unit) =
    com.iptv.firetv.core.ui.CheckRow(tr("I confirm that I am authorised to access this content and want to import it."), checked, onToggle)

// ----------------------------------------------------------------------------------- local media & welcome

@HiltViewModel
class LocalMediaViewModel @Inject constructor(catalog: CatalogRepository, private val sources: com.iptv.firetv.data.SourceRepository) : ViewModel() {
    val items = catalog.movies("localmedia", "", 1).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun add(uri: android.net.Uri, title: String) { viewModelScope.launch { sources.addLocalMedia(uri, title) } }
}

@Composable
fun LocalMediaScreen(nav: NavHostController, vm: LocalMediaViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = runCatching { ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }.getOrNull() ?: "Video"
            vm.add(uri, name.substringBeforeLast('.'))
        }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    ScreenFrame(title = tr("Local media")) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TvButton(tr("Add a video file"), { picker.launch(arrayOf("video/*")) }, icon = Icons.Filled.Folder, focusRequester = first)
            if (items.isEmpty()) StatePanel(tr("No local videos"), tr("Pick files from internal storage or a USB drive. Access uses the system file picker; no storage permission is requested."), Modifier.fillMaxWidth())
            else androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(6.dp)) {
                items(items, key = { it.id }) { m -> PosterCard(m.title, m.poster, { nav.navigate(Routes.player("movie", m.id)) }) }
            }
            Text(tr("Remove the \u201CLocal media\u201D source in Playlists / Sources to clear this list."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun WelcomeScreen(onDone: () -> Unit, onAdd: (String) -> Unit, vm: SourcesViewModel = hiltViewModel()) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.iptv.firetv.R.drawable.ic_kvod_logo), null, Modifier.size(84.dp).clip(RoundedCornerShape(22.dp)))
            Text("KVOD TV", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text(tr("Your Entertainment. Your Way."), style = MaterialTheme.typography.headlineMedium)
        Text(tr("KVOD TV plays content you are authorised to watch. Bring your own playlist, Xtream-compatible account or XMLTV guide. Nothing is bundled and nothing is shown until you add a source."), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(820.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(tr("Add Your First Playlist"), { vm.finishOnboarding(); onAdd("M3U_URL") }, icon = Icons.Filled.Add, focusRequester = first)
            TvButton(tr("Use a file"), { vm.finishOnboarding(); onAdd("M3U_FILE") }, primary = false)
            TvButton(tr("Xtream account"), { vm.finishOnboarding(); onAdd("XTREAM") }, primary = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(tr("Try sample data (demo)"), { vm.finishOnboarding(); vm.addSample(); onDone() }, primary = false)
            TvButton(tr("Skip for now"), { vm.finishOnboarding(); onDone() }, primary = false)
        }
    }
}

/** Wizard progress: done steps are tinted, the current step is filled with the accent. */
@Composable
private fun Stepper(active: Int, labels: List<String>) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        labels.forEachIndexed { i, l ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(32.dp).background(when { i == active -> cs.primary; i < active -> cs.primary.copy(alpha = 0.35f); else -> cs.onPrimaryContainer.copy(alpha = 0.14f) }, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    Text(if (i < active) "\u2713" else "${i + 1}", style = MaterialTheme.typography.labelLarge, color = if (i == active) cs.onPrimary else cs.onPrimaryContainer)
                }
                Text(tr(l), style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer, fontWeight = if (i == active) androidx.compose.ui.text.font.FontWeight.Bold else null)
            }
        }
    }
}

/** Shows a QR code; the phone opens a one-time local page and sends the address back to the TV. */
@Composable
private fun PhoneEntryDialog(onResult: (url: String, name: String) -> Unit, onDismiss: () -> Unit) {
    var link by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val addr = com.iptv.firetv.core.network.LanEntryServer.lanAddress()
        if (addr == null) { failed = true; onDispose { } } else {
            val server = com.iptv.firetv.core.network.LanEntryServer(addr)
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            link = runCatching { server.start { sub -> handler.post { onResult(sub.url, sub.name) } } }.getOrElse { failed = true; null }
            onDispose { server.stop() }
        }
    }
    TvDialog(onDismiss, tr("Enter the address with your phone")) {
        val l = link
        if (failed || l == null) Text(tr("The TV is not connected to a home network (Wi-Fi or Ethernet), so the phone page cannot be started."), style = MaterialTheme.typography.bodyLarge)
        else {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                com.iptv.firetv.core.ui.QrCode(l, 240.dp)
                Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("1. Connect your phone to the same Wi-Fi.\n2. Scan the code and type the playlist address.\n3. Tap Send to TV."), style = MaterialTheme.typography.bodyLarge)
                    Text(tr("The page is available for 5 minutes, works once, and travels over your home network without encryption. Do not use it on shared or public networks."), style = MaterialTheme.typography.bodySmall, color = LocalIptvColors.current.warning)
                }
            }
        }
        TvButton(tr("Cancel"), onDismiss, primary = false)
    }
}
