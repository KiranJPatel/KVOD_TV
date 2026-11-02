package com.iptv.firetv.feature.profiles

import com.iptv.firetv.core.common.tr
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.foundation.border
import com.iptv.firetv.core.ui.SettingsGroup
import com.iptv.firetv.core.ui.SettingRow
import com.iptv.firetv.core.ui.SwitchRow
import com.iptv.firetv.core.ui.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iptv.firetv.core.common.TextUtil
import com.iptv.firetv.core.database.GroupCount
import com.iptv.firetv.core.database.ProfileEntity
import com.iptv.firetv.core.security.Pin
import com.iptv.firetv.core.ui.Chip
import com.iptv.firetv.core.ui.ConfirmDialog
import com.iptv.firetv.core.ui.FocusSurface
import com.iptv.firetv.core.ui.LocalIptvColors
import com.iptv.firetv.core.ui.OptionDialog
import com.iptv.firetv.core.ui.PinPad
import com.iptv.firetv.core.ui.ScreenFrame
import com.iptv.firetv.core.ui.TvButton
import com.iptv.firetv.core.ui.TvDialog
import com.iptv.firetv.core.ui.TvTextField
import com.iptv.firetv.data.CatalogRepository
import com.iptv.firetv.data.PinResult
import com.iptv.firetv.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

val avatarColors = listOf(0xFF1688FF, 0xFFE5484D, 0xFF30A46C, 0xFFF5A524, 0xFF8E4EC6, 0xFF12A594, 0xFFE93D82, 0xFF7C8798).map { Color(it) }

@HiltViewModel
class ProfilesViewModel @Inject constructor(private val repo: ProfileRepository, catalog: CatalogRepository) : ViewModel() {
    val profiles: StateFlow<List<ProfileEntity>> = repo.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val active: StateFlow<ProfileEntity?> = repo.activeProfile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val groups: StateFlow<List<GroupCount>> = catalog.channelGroups().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    suspend fun select(id: Long, pin: String?) = repo.select(id, pin)
    suspend fun verify(id: Long, pin: String) = repo.verifyPin(id, pin)
    fun save(p: ProfileEntity, newPin: String? = null, clearPin: Boolean = false) { viewModelScope.launch { repo.save(p, newPin, clearPin) } }
    fun delete(id: Long) { viewModelScope.launch { repo.delete(id) } }
}

@Composable
fun Avatar(p: ProfileEntity, size: Dp = 120.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(avatarColors[p.avatar.mod(avatarColors.size)]), contentAlignment = Alignment.Center) {
        Text(p.name.take(1).uppercase(), style = if (size >= 100.dp) MaterialTheme.typography.displayMedium else MaterialTheme.typography.titleLarge, color = Color.White)
    }
}

@Composable
private fun PinDialog(title: String, message: String?, onSubmit: (String) -> Unit, onDismiss: () -> Unit, onForgot: (() -> Unit)? = null) {
    var pin by remember { mutableStateOf("") }
    TvDialog(onDismiss, title) {
        message?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PinPad(pin, { pin = it }, { onSubmit(pin); pin = "" }) }
        if (onForgot != null) TvButton(tr("Forgot PIN?"), onForgot, primary = false)
    }
}

@Composable
fun ProfileChooserScreen(onEntered: () -> Unit, onManage: () -> Unit, vm: ProfilesViewModel = hiltViewModel()) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pinFor by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinMsg by remember { mutableStateOf<String?>(null) }
    var forgot by remember { mutableStateOf<ProfileEntity?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(profiles.size) { runCatching { first.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterVertically)) {
        Text(tr("Who is watching?"), style = MaterialTheme.typography.displayMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            profiles.forEachIndexed { i, p ->
                FocusSurface({ if (p.pinHash == null) scope.launch { vm.select(p.id, null); onEntered() } else { pinFor = p; pinMsg = null } },
                    Modifier.width(210.dp).height(250.dp), RoundedCornerShape(28.dp), 1.06f, restBorder = false, containerColor = MaterialTheme.colorScheme.surface, focusRequester = if (i == 0) first else null) {
                    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                        Box { Avatar(p); if (p.pinHash != null) Icon(Icons.Filled.Lock, "PIN protected", Modifier.align(Alignment.BottomEnd).size(28.dp).background(MaterialTheme.colorScheme.surface, CircleShape).padding(6.dp), tint = MaterialTheme.colorScheme.onSurface) }
                        Text(p.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        Text(if (p.isOwner) "Owner" else if (p.kids) "Kids profile" else if (p.pinHash != null) "PIN locked" else "Profile", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        TvButton(tr("Manage profiles"), onManage, primary = false)
    }
    pinFor?.let { p ->
        PinDialog("PIN for ${p.name}", pinMsg, { pin ->
            scope.launch {
                when (val r = vm.select(p.id, pin)) {
                    PinResult.Ok -> { pinFor = null; onEntered() }
                    PinResult.Wrong -> pinMsg = "Incorrect PIN."
                    is PinResult.Locked -> pinMsg = "Too many attempts. Try again in ${r.seconds} s."
                }
            }
        }, { pinFor = null }, onForgot = { forgot = p; pinMsg = null })
    }
    forgot?.let { p ->
        if (p.isOwner) TvDialog({ forgot = null }, tr("Owner PIN cannot be reset here")) {
            Text(tr("The owner PIN protects every other profile, so it cannot be bypassed inside the app. To reset it, clear the app data in the system settings (Settings \u2192 Applications \u2192 KVOD TV \u2192 Clear data). This removes all profiles, sources and history."), style = MaterialTheme.typography.bodyLarge)
            TvButton(tr("OK"), { forgot = null })
        } else PinDialog("Owner PIN required", pinMsg, { pin ->
            scope.launch {
                val owner = profiles.firstOrNull { it.isOwner }
                if (owner == null || owner.pinHash == null || vm.verify(owner.id, pin) == PinResult.Ok) { vm.save(p, clearPin = true); forgot = null; pinFor = null }
                else pinMsg = "Incorrect owner PIN."
            }
        }, { forgot = null })
    }
}

/** Label on the left, controls on the right. */
@Composable
private fun FormRow(label: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(label), Modifier.width(130.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
fun ProfileManageScreen(vm: ProfilesViewModel = hiltViewModel()) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val owner = profiles.firstOrNull { it.isOwner }
    var unlocked by remember { mutableStateOf(false) }
    var pinMsg by remember { mutableStateOf<String?>(null) }
    var selId by remember { mutableStateOf<Long?>(null) }
    var pinDialog by remember { mutableStateOf(false) }
    var delDialog by remember { mutableStateOf(false) }
    var blockDialog by remember { mutableStateOf(false) }
    var audioDialog by remember { mutableStateOf(false) }
    var subDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val sel = profiles.firstOrNull { it.id == selId } ?: profiles.firstOrNull()
    LaunchedEffect(sel?.id) { name = sel?.name.orEmpty() }
    val langs = listOf("Automatic" to "", "English" to "en", "Hindi" to "hi", "Marathi" to "mr", "Tamil" to "ta", "Telugu" to "te", "Bengali" to "bn", "Spanish" to "es", "French" to "fr", "German" to "de", "Arabic" to "ar")

    if (owner?.pinHash != null && !unlocked) { PinDialog("Owner PIN", pinMsg, { pin -> scope.launch { if (vm.verify(owner.id, pin) == PinResult.Ok) unlocked = true else pinMsg = "Incorrect PIN." } }, { }); return }

    ScreenFrame(title = tr("Manage profiles")) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.width(280.dp).fillMaxHeight().clip(RoundedCornerShape(Dimens.PanelRadius)).background(MaterialTheme.colorScheme.surface).border(1.dp, LocalIptvColors.current.divider, RoundedCornerShape(Dimens.PanelRadius)).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                profiles.forEach { p -> Chip(p.name + if (p.isOwner) " (owner)" else "", p.id == sel?.id, { selId = p.id }, Modifier.fillMaxWidth()) }
                TvButton(tr("Add profile"), { vm.save(ProfileEntity(name = "Profile ${profiles.size + 1}", avatar = profiles.size)) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add, primary = false)
            }
            // One labelled row per topic: Left/Right moves inside a row, Up/Down moves between rows, and everything fits without scrolling.
            if (sel != null) Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(Dimens.PanelRadius)).background(MaterialTheme.colorScheme.surface).border(1.dp, LocalIptvColors.current.divider, RoundedCornerShape(Dimens.PanelRadius)).verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(sel, 72.dp)
                    TvTextField(name, { name = it }, tr("Name"), Modifier.weight(1f))
                    TvButton(tr("Save name"), { if (name.isNotBlank()) vm.save(sel.copy(name = name.trim())) }, Modifier.padding(top = 18.dp))
                }
                val blocked = sel.blockedGroups.split("|").filter { it.isNotBlank() }
                FormRow("Avatar colour") { avatarColors.indices.forEach { i -> Chip(tr("\u25CF"), sel.avatar == i, { vm.save(sel.copy(avatar = i)) }) } }
                SettingsGroup(tr("Restrictions")) {
                    SwitchRow(tr("Kids profile"), sel.kids, { vm.save(sel.copy(kids = !sel.kids, hideAdult = true)) }, icon = Icons.Filled.ChildCare, subtitle = tr("Hides adult content and blocked categories."))
                    SwitchRow(tr("Hide adult content"), sel.hideAdult || sel.kids, { if (!sel.kids) vm.save(sel.copy(hideAdult = !sel.hideAdult)) }, icon = Icons.Filled.VisibilityOff)
                    SettingRow(tr("Blocked categories"), { blockDialog = true }, icon = Icons.Filled.Block, value = "${blocked.size}")
                }
                SettingsGroup(tr("Language")) {
                    SettingRow(tr("Preferred audio language"), { audioDialog = true }, icon = Icons.Filled.Audiotrack, value = langs.firstOrNull { it.second == sel.audioLang }?.first ?: "Automatic")
                    SettingRow(tr("Preferred subtitle language"), { subDialog = true }, icon = Icons.Filled.Subtitles, value = langs.firstOrNull { it.second == sel.subtitleLang }?.first ?: "Automatic")
                }
                SettingsGroup(tr("PIN")) {
                    SettingRow(if (sel.pinHash == null) tr("Set PIN") else tr("Change PIN"), { pinDialog = true }, icon = Icons.Filled.Lock)
                    if (sel.pinHash != null) SettingRow(tr("Remove PIN"), { vm.save(sel, clearPin = true) }, icon = Icons.Filled.LockOpen)
                }
                if (!sel.isOwner) FormRow("Delete") { TvButton(tr("Delete profile"), { delDialog = true }, primary = false, danger = true, icon = Icons.Filled.Delete) }
                Text(tr("Restrictions are enforced when content is loaded (lists, search, Continue Watching and direct links), not only hidden in menus. Favourites, history and Continue Watching are separate for every profile."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val s = sel
    if (s != null) {
        if (pinDialog) { var first by remember { mutableStateOf<String?>(null) }
            PinDialog(if (first == null) "Choose a 4\u20136 digit PIN" else "Repeat the PIN", pinMsg, { pin ->
                if (!Pin.isValidFormat(pin)) pinMsg = "Use 4 to 6 digits."
                else if (first == null) { first = pin; pinMsg = null }
                else if (first == pin) { vm.save(s, newPin = pin); pinDialog = false; pinMsg = null } else { pinMsg = "PINs do not match."; first = null }
            }, { pinDialog = false; pinMsg = null }) }
        if (delDialog) ConfirmDialog("Delete ${s.name}?", tr("This profile's favourites, history and Continue Watching are deleted. This cannot be undone."), tr("Delete"), { vm.delete(s.id); delDialog = false; selId = null }, { delDialog = false }, destructive = true)
        if (audioDialog) OptionDialog(tr("Preferred audio language"), langs, s.audioLang, { vm.save(s.copy(audioLang = it)) }, { audioDialog = false })
        if (subDialog) OptionDialog(tr("Preferred subtitle language"), langs, s.subtitleLang, { vm.save(s.copy(subtitleLang = it)) }, { subDialog = false })
        if (blockDialog) TvDialog({ blockDialog = false }, tr("Restricted categories")) {
            val blocked = s.blockedGroups.split("|").filter { it.isNotBlank() }.toSet()
            Column(Modifier.height(380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (groups.isEmpty()) Text(tr("No categories yet. Import a playlist first."), style = MaterialTheme.typography.bodyLarge)
                groups.take(120).forEach { g -> SwitchRow(g.name, g.norm in blocked, {
                    val next = if (g.norm in blocked) blocked - g.norm else blocked + g.norm; vm.save(s.copy(blockedGroups = next.joinToString("|"))) }) }
            }
            TvButton(tr("Done"), { blockDialog = false })
        }
    }
}
