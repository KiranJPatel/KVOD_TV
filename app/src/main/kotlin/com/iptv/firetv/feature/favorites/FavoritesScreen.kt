package com.iptv.firetv.feature.favorites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.iptv.firetv.app.LibraryInbox
import com.iptv.firetv.app.Routes
import com.iptv.firetv.core.common.tr
import com.iptv.firetv.core.database.ChannelEntity
import com.iptv.firetv.core.database.PlaylistRow
import com.iptv.firetv.core.database.SeriesEntity
import com.iptv.firetv.core.database.VodItemEntity
import com.iptv.firetv.core.model.ContentKind
import com.iptv.firetv.core.ui.ActionCard
import com.iptv.firetv.core.ui.ActionTile
import com.iptv.firetv.core.ui.ChannelCard
import com.iptv.firetv.core.ui.Chip
import com.iptv.firetv.core.ui.ConfirmDialog
import com.iptv.firetv.core.ui.PosterCard
import com.iptv.firetv.core.ui.RegisterContentEntry
import com.iptv.firetv.core.ui.ScreenFrame
import com.iptv.firetv.core.ui.StatePanel
import com.iptv.firetv.core.ui.TvButton
import com.iptv.firetv.data.PlaylistRepository
import com.iptv.firetv.data.UserDataRepository
import com.iptv.firetv.feature.playlists.NamePlaylistDialog
import com.iptv.firetv.feature.playlists.PlaylistPickerDialog
import com.iptv.firetv.feature.player.ChannelQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FavoritesViewModel @Inject constructor(user: UserDataRepository, val queue: ChannelQueue, private val playlistRepo: PlaylistRepository) : ViewModel() {
    private fun <T> kotlinx.coroutines.flow.Flow<List<T>>.hold(): StateFlow<List<T>> = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<List<ChannelEntity>> = user.recentChannels(60).hold()
    val resume = user.continueWatching(40).hold()
    val channels: StateFlow<List<ChannelEntity>> = user.favoriteChannels().hold()
    val movies: StateFlow<List<VodItemEntity>> = user.favoriteMovies().hold()
    val series: StateFlow<List<SeriesEntity>> = user.favoriteSeries().hold()

    val playlists: StateFlow<List<PlaylistRow>> = playlistRepo.playlists.hold()
    /** The playlist whose contents are shown, or null for the list of playlists. */
    val openPlaylist = MutableStateFlow<Long?>(null)
    val plChannels: StateFlow<List<ChannelEntity>> = openPlaylist.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else playlistRepo.channels(id) }.hold()
    val plMovies: StateFlow<List<VodItemEntity>> = openPlaylist.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else playlistRepo.movies(id) }.hold()
    val plSeries: StateFlow<List<SeriesEntity>> = openPlaylist.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else playlistRepo.series(id) }.hold()

    fun createPlaylist(name: String) { viewModelScope.launch { playlistRepo.create(name)?.let { openPlaylist.value = it } } }
    fun renamePlaylist(id: Long, name: String) { viewModelScope.launch { playlistRepo.rename(id, name) } }
    fun deletePlaylist(id: Long) { viewModelScope.launch { playlistRepo.delete(id); openPlaylist.value = null } }
    fun removeFromPlaylist(id: Long, kind: ContentKind, itemId: String) { viewModelScope.launch { playlistRepo.remove(id, kind, itemId) } }

    /** Normal playback of a list: channel up/down follows list order. */
    fun startQueue(ids: List<String>) { queue.set(ids); queue.setShuffle(false) }
    /** Random playback of a list: returns the first random channel and leaves the player in random mode. */
    fun startRandom(ids: List<String>): String? { if (ids.isEmpty()) return null; queue.set(ids); queue.setShuffle(true); return queue.randomPick(null) }
}

@Composable
fun FavoritesScreen(nav: NavHostController, vm: FavoritesViewModel = hiltViewModel()) {
    val ch by vm.channels.collectAsStateWithLifecycle()
    val mv by vm.movies.collectAsStateWithLifecycle()
    val se by vm.series.collectAsStateWithLifecycle()
    val rec by vm.recent.collectAsStateWithLifecycle()
    val cont by vm.resume.collectAsStateWithLifecycle()
    val lists by vm.playlists.collectAsStateWithLifecycle()
    val open by vm.openPlaylist.collectAsStateWithLifecycle()
    val plCh by vm.plChannels.collectAsStateWithLifecycle()
    val plMv by vm.plMovies.collectAsStateWithLifecycle()
    val plSe by vm.plSeries.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(LibraryInbox.TAB_LIVE) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<ChannelEntity?>(null) }
    val tabFocus = remember { FocusRequester() }
    RegisterContentEntry(tabFocus)

    // Another screen (the menu) may ask for a particular tab.
    val pending by LibraryInbox.pendingTab.collectAsState()
    LaunchedEffect(pending) { pending?.let { tab = it; if (it != LibraryInbox.TAB_PLAYLISTS) vm.openPlaylist.value = null; LibraryInbox.pendingTab.value = null } }
    // Back closes an open playlist before leaving the Library.
    BackHandler(enabled = tab == LibraryInbox.TAB_PLAYLISTS && open != null) { vm.openPlaylist.value = null }

    fun toast(m: String) = android.widget.Toast.makeText(ctx, m, android.widget.Toast.LENGTH_SHORT).show()
    fun playRandom(ids: List<String>) { val id = vm.startRandom(ids); if (id != null) nav.navigate(Routes.player("live", id)) else toast(tr("No channels to play in this list.")) }

    ScreenFrame(title = tr("Library")) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Live TV (${ch.size})", tab == LibraryInbox.TAB_LIVE, { tab = LibraryInbox.TAB_LIVE }, focusRequester = tabFocus)
                Chip("Movies (${mv.size})", tab == LibraryInbox.TAB_MOVIES, { tab = LibraryInbox.TAB_MOVIES })
                Chip("Series (${se.size})", tab == LibraryInbox.TAB_SERIES, { tab = LibraryInbox.TAB_SERIES })
                Chip("Playlists (${lists.size})", tab == LibraryInbox.TAB_PLAYLISTS, { tab = LibraryInbox.TAB_PLAYLISTS })
                Chip("History", tab == LibraryInbox.TAB_HISTORY, { tab = LibraryInbox.TAB_HISTORY })
            }
            val cells = GridCells.Adaptive(if (tab == LibraryInbox.TAB_MOVIES || tab == LibraryInbox.TAB_SERIES) 176.dp else 204.dp)
            val pad = PaddingValues(8.dp)
            when (tab) {
                LibraryInbox.TAB_LIVE -> if (ch.isEmpty()) StatePanel(tr("Nothing here yet"), "Press the star next to a channel in Live TV, or Favorite in the player.", Modifier.fillMaxSize())
                else LazyVerticalGrid(cells, Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // The random card sits at the start of the channel list.
                    item(key = "random") { ActionCard(tr("Play random channel"), "${ch.size} " + tr("channels"), Icons.Filled.Shuffle, { playRandom(ch.map { it.id }) }) }
                    items(ch, key = { it.id }) { c -> ChannelCard(c.name, c.logo, c.groupName, { vm.startQueue(ch.map { it.id }); nav.navigate(Routes.player("live", c.id)) }, onMenu = { target = c }) }
                }
                LibraryInbox.TAB_MOVIES -> if (mv.isEmpty()) StatePanel(tr("Nothing here yet"), "Open a movie and choose Add to favorites.", Modifier.fillMaxSize())
                else LazyVerticalGrid(cells, Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(mv, key = { it.id }) { m -> PosterCard(m.title, m.poster, { nav.navigate(Routes.movie(m.id)) }) }
                }
                LibraryInbox.TAB_SERIES -> if (se.isEmpty()) StatePanel(tr("Nothing here yet"), "Open a series and choose Add to favorites.", Modifier.fillMaxSize())
                else LazyVerticalGrid(cells, Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(se, key = { it.id }) { s -> PosterCard(s.title, s.poster, { nav.navigate(Routes.series(s.id)) }) }
                }
                LibraryInbox.TAB_PLAYLISTS -> {
                    val current = lists.firstOrNull { it.id == open }
                    if (open == null || current == null) {
                        LazyVerticalGrid(GridCells.Adaptive(250.dp), Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            item(key = "new") { ActionTile(tr("New playlist"), tr("Make a list to watch later. You can have as many as you like."), Icons.Filled.Add, { creating = true }, Modifier.fillMaxWidth().height(170.dp), accent = true) }
                            items(lists, key = { it.id }) { pl ->
                                ActionTile(pl.name, "${pl.itemCount} item${if (pl.itemCount == 1) "" else "s"}", Icons.Filled.PlaylistPlay, { vm.openPlaylist.value = pl.id }, Modifier.fillMaxWidth().height(170.dp))
                            }
                        }
                    } else {
                        val first = remember { FocusRequester() }
                        LaunchedEffect(current.id) { runCatching { first.requestFocus() } }
                        val empty = plCh.isEmpty() && plMv.isEmpty() && plSe.isEmpty()
                        LazyVerticalGrid(GridCells.Adaptive(204.dp), Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "head") {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(current.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        TvButton(tr("Random channel"), { playRandom(plCh.map { it.id }) }, icon = Icons.Filled.Shuffle, enabled = plCh.isNotEmpty(), focusRequester = first)
                                        TvButton(tr("Rename"), { renaming = true }, icon = Icons.Filled.Edit, primary = false)
                                        TvButton(tr("Delete"), { deleting = true }, icon = Icons.Filled.Delete, primary = false, danger = true)
                                    }
                                    if (!empty) Text(tr("Tip: press the Menu button on a card to remove it from this playlist."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (empty) item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                                StatePanel(tr("This playlist is empty"), "Use the playlist button next to a channel, or Add to playlist in the player and on movie and series pages.", Modifier.fillMaxWidth())
                            }
                            items(plCh, key = { "c" + it.id }) { c ->
                                ChannelCard(c.name, c.logo, c.groupName, { vm.startQueue(plCh.map { it.id }); nav.navigate(Routes.player("live", c.id)) },
                                    onMenu = { vm.removeFromPlaylist(current.id, ContentKind.LIVE, c.id); toast("Removed ${c.name}") })
                            }
                            items(plMv, key = { "m" + it.id }) { m -> PosterCard(m.title, m.poster, { nav.navigate(Routes.movie(m.id)) }, onMenu = { vm.removeFromPlaylist(current.id, ContentKind.MOVIE, m.id); toast("Removed ${m.title}") }) }
                            items(plSe, key = { "s" + it.id }) { s -> PosterCard(s.title, s.poster, { nav.navigate(Routes.series(s.id)) }, onMenu = { vm.removeFromPlaylist(current.id, ContentKind.SERIES, s.id); toast("Removed ${s.title}") }) }
                        }
                        if (renaming) NamePlaylistDialog(tr("Rename playlist"), current.name, { vm.renamePlaylist(current.id, it); renaming = false }, { renaming = false })
                        if (deleting) ConfirmDialog(tr("Delete playlist?"), "\u201C${current.name}\u201D will be deleted. The channels, movies and series in it are not affected.", tr("Delete"), { deleting = false; vm.deletePlaylist(current.id) }, { deleting = false }, destructive = true)
                    }
                }
                else -> if (rec.isEmpty() && cont.isEmpty()) StatePanel(tr("Nothing here yet"), "Things you watch show up here.", Modifier.fillMaxSize())
                else LazyVerticalGrid(cells, Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(cont, key = { "c" + it.progress.itemId }) { c ->
                        val p = c.progress
                        com.iptv.firetv.core.ui.LandscapeCard(p.title, if (p.durationMs > p.positionMs) com.iptv.firetv.core.ui.formatDuration(p.durationMs - p.positionMs) + " left" else null, p.poster, if (p.durationMs > 0) p.positionMs.toFloat() / p.durationMs else null, { nav.navigate(c.route) })
                    }
                    items(rec, key = { "r" + it.id }) { c -> ChannelCard(c.name, c.logo, c.groupName, { vm.startQueue(rec.map { it.id }); nav.navigate(Routes.player("live", c.id)) }, onMenu = { target = c }) }
                }
            }
        }
    }
    if (creating) NamePlaylistDialog(tr("New playlist"), "", { vm.createPlaylist(it); creating = false }, { creating = false })
    target?.let { t -> PlaylistPickerDialog(ContentKind.LIVE, t.id, t.name, { target = null }) }
}
