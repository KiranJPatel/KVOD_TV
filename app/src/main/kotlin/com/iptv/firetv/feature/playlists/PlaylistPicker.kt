package com.iptv.firetv.feature.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iptv.firetv.core.common.tr
import com.iptv.firetv.core.database.PlaylistRow
import com.iptv.firetv.core.model.ContentKind
import com.iptv.firetv.core.ui.SettingRow
import com.iptv.firetv.core.ui.TvButton
import com.iptv.firetv.core.ui.TvDialog
import com.iptv.firetv.core.ui.TvTextField
import com.iptv.firetv.data.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistPickerViewModel @Inject constructor(private val repo: PlaylistRepository) : ViewModel() {
    val playlists: StateFlow<List<PlaylistRow>> = repo.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun membership(kind: ContentKind, itemId: String): Flow<Set<Long>> = repo.playlistIdsContaining(kind, itemId)
    fun toggle(playlistId: Long, kind: ContentKind, itemId: String, done: (Boolean) -> Unit) { viewModelScope.launch { done(repo.toggle(playlistId, kind, itemId)) } }
    /** Creates the playlist and puts the item straight into it. */
    fun createAndAdd(name: String, kind: ContentKind, itemId: String, done: (String?) -> Unit) {
        viewModelScope.launch {
            val id = repo.create(name)
            if (id == null) done(null) else { repo.toggle(id, kind, itemId); done(name.trim().take(PlaylistRepository.MAX_NAME)) }
        }
    }
}

/**
 * "Add to playlist" sheet shared by Live TV, the player and the movie / series screens. Each row toggles the item in that playlist;
 * a new playlist can be created on the spot. Playlists are unlimited and belong to the active profile.
 */
@Composable
fun PlaylistPickerDialog(kind: ContentKind, itemId: String, itemTitle: String?, onDismiss: () -> Unit, vm: PlaylistPickerViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val lists by vm.playlists.collectAsStateWithLifecycle()
    val member by remember(kind, itemId) { vm.membership(kind, itemId) }.collectAsStateWithLifecycle(emptySet())
    var creating by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    fun toast(msg: String) = android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()

    TvDialog(onDismiss, tr("Add to playlist"), subtitle = itemTitle, icon = Icons.Filled.PlaylistAdd) {
        SettingRow(tr("New playlist\u2026"), { creating = true }, icon = Icons.Filled.Add, focusRequester = first)
        if (lists.isEmpty()) Text(tr("You have no playlists yet. Create one to save channels, movies and series to watch later."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Column(Modifier.height((lists.size.coerceAtMost(5) * 72).dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lists.forEach { pl ->
                val inIt = pl.id in member
                SettingRow(pl.name, { vm.toggle(pl.id, kind, itemId) { added -> toast(if (added) "Added to ${pl.name}" else "Removed from ${pl.name}") } }, icon = Icons.Filled.PlaylistPlay,
                    subtitle = "${pl.itemCount} item${if (pl.itemCount == 1) "" else "s"}") {
                    Icon(if (inIt) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (inIt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TvButton(tr("Done"), onDismiss, primary = false)
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    if (creating) NamePlaylistDialog(tr("New playlist"), "", { name -> vm.createAndAdd(name, kind, itemId) { n -> if (n != null) toast("Added to $n") }; creating = false }, { creating = false })
}

/** Small name entry dialog for creating or renaming a playlist. */
@Composable
fun NamePlaylistDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    TvDialog(onDismiss, title, icon = Icons.Filled.PlaylistAdd) {
        TvTextField(text, { text = it.take(PlaylistRepository.MAX_NAME) }, tr("Playlist name (for example Watch later)"), Modifier.fillMaxWidth(), focusRequester = field)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(tr("Save"), { if (text.isNotBlank()) onSave(text) }, enabled = text.isNotBlank())
            TvButton(tr("Cancel"), onDismiss, primary = false)
        }
    }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
}
