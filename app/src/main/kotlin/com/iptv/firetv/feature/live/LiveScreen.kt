package com.iptv.firetv.feature.live

import com.iptv.firetv.core.common.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.iptv.firetv.app.Routes
import com.iptv.firetv.core.database.ChannelEntity
import com.iptv.firetv.core.model.NowNext
import com.iptv.firetv.core.ui.Dimens
import com.iptv.firetv.core.ui.IconOnlyButton
import com.iptv.firetv.core.ui.Artwork
import com.iptv.firetv.core.ui.Chip
import com.iptv.firetv.core.ui.FavoriteButton
import com.iptv.firetv.core.ui.FocusSurface
import com.iptv.firetv.core.ui.LiveBadge
import com.iptv.firetv.core.ui.LocalIptvColors
import com.iptv.firetv.core.ui.OptionDialog
import com.iptv.firetv.core.ui.ProgressLine
import com.iptv.firetv.core.ui.RegisterContentEntry
import com.iptv.firetv.core.ui.RowIconButton
import com.iptv.firetv.core.model.ContentKind
import com.iptv.firetv.feature.playlists.PlaylistPickerDialog
import com.iptv.firetv.core.ui.ScreenFrame
import com.iptv.firetv.core.ui.SkeletonBox
import com.iptv.firetv.core.ui.StatePanel
import com.iptv.firetv.core.ui.TvButton
import com.iptv.firetv.core.ui.TvTextField
import com.iptv.firetv.core.ui.formatTime
import androidx.compose.ui.platform.LocalContext

@Composable
fun LiveScreen(nav: NavHostController, vm: LiveViewModel = hiltViewModel()) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val items = vm.paged.collectAsLazyPagingItems()
    val nowNext by vm.nowNext.collectAsStateWithLifecycle()
    val bump by vm.guideBump.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val favs by vm.favoriteIds.collectAsStateWithLifecycle()
    val group by vm.group.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val settings by vm.appSettings.collectAsStateWithLifecycle()
    var sortDialog by remember { mutableStateOf(false) }
    var sourceDialog by remember { mutableStateOf(false) }
    var mapDialog by remember { mutableStateOf(false) }
    var playlistTarget by remember { mutableStateOf<ChannelEntity?>(null) }
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val catFocus = remember { FocusRequester() }
    // A playlist that was deleted while selected falls back to "All".
    LaunchedEffect(playlists, group) { val pid = playlistIdOf(group); if (pid != null && playlists.isNotEmpty() && playlists.none { it.id == pid }) vm.group.value = null }
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val restore = remember { FocusRequester() }
    LaunchedEffect(Unit) { vm.restartPreview() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.stopPreview() } }

    // Focus restoration: return to the channel that was focused before playback (if its page is loaded).
    LaunchedEffect(items.itemCount) {
        val idx = items.itemSnapshotList.indexOfFirst { it?.id == vm.lastFocusedId }
        // Only when coming back from playback: arriving from the top bar must start in the category list instead.
        if (idx >= 0 && vm.restoreFocusOnReturn) { listState.scrollToItem(idx); kotlinx.coroutines.delay(60); runCatching { restore.requestFocus() }; vm.restoreFocusOnReturn = false }
    }
    // Now/next for the channels that are loaded; refreshed every minute and immediately after a guide mapping change.
    LaunchedEffect(items.itemCount, group, bump) {
        while (true) { vm.loadNowNext(items.itemSnapshotList.items); kotlinx.coroutines.delay(60_000) }
    }

    ScreenFrame {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // Categories
            LazyColumn(Modifier.width(240.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                val total = groups.sumOf { it.count }
                item { GroupRow(tr("All"), total, group == null, catFocus) { vm.group.value = null } }
                item { GroupRow(tr("Favorites"), favs.size, group == GROUP_FAVORITES, catFocus) { vm.group.value = GROUP_FAVORITES } }
                item { GroupRow(tr("Recently watched"), null, group == GROUP_RECENT, catFocus) { vm.group.value = GROUP_RECENT } }
                items(playlists, key = { "pl" + it.id }) { pl -> GroupRow("\u25B6  " + pl.name, pl.itemCount, group == playlistGroup(pl.id), catFocus) { vm.group.value = playlistGroup(pl.id) } }
                items(groups, key = { it.norm }) { g -> GroupRow(g.name, g.count, group == g.norm, catFocus) { vm.group.value = g.norm } }
            }
            // Channel list
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    TvButton("", {
                        scope.launch {
                            val id = vm.playRandom()
                            if (id != null) { vm.restoreFocusOnReturn = false; nav.navigate(Routes.player("live", id)) } else android.widget.Toast.makeText(ctx, tr("No channels to play in this list."), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }, Modifier.width(Dimens.ButtonHeight), icon = Icons.Filled.Shuffle, contentDescription = tr("Play a random channel"))
                    TvTextField(query, { vm.query.value = it }, "", Modifier.weight(1f), leadingIcon = Icons.Filled.Search)
                    Chip("Sort: " + listOf("Playlist order", "Name", "Number")[(settings?.channelSort ?: 0).coerceIn(0, 2)], false, { sortDialog = true })
                    if (sources.count { it.type != "XMLTV_URL" && it.type != "XMLTV_FILE" } > 1) Chip(tr("Source"), false, { sourceDialog = true })
                }
                val refreshing = items.loadState.refresh is LoadState.Loading
                when {
                    items.itemCount == 0 && refreshing -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(6) { SkeletonBox(Modifier.fillMaxWidth().height(72.dp)) } }
                    items.itemCount == 0 -> StatePanel(if (query.isNotBlank()) "No matching channels" else if (group != null) "This category is empty" else "No channels yet",
                        if (group == GROUP_FAVORITES) "Use the star next to a channel to add it here." else "Add or refresh a playlist in Playlists / Sources.", Modifier.fillMaxSize()) {
                        if (group == null && query.isBlank()) TvButton(tr("Open sources"), { nav.navigate(Routes.SOURCES) }, icon = Icons.Filled.PlaylistAdd)
                    }
                    else -> LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                        items(count = items.itemCount, key = items.itemKey { it.id }) { idx ->
                            val c = items[idx]
                            if (c != null) ChannelRow(c, nowNext[c.id], c.id in favs, restore = if (c.id == vm.lastFocusedId) restore else null,
                                onFocus = { vm.selected.value = c; vm.lastFocusedId = c.id },
                                onPlay = { scope.launch { vm.buildQueue(); vm.restoreFocusOnReturn = true; nav.navigate(Routes.player("live", c.id)) } },
                                onFav = { vm.toggleFavorite(c.id) }, onPlaylist = { playlistTarget = c })
                        }
                    }
                }
            }
            // Details and preview (Settings > Menu & navigation > Channel preview pane)
            if (settings?.showPreviewPane != false) Column(Modifier.width(400.dp).fillMaxHeight().background2(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val c = selected
                if (c == null) Text(tr("Select a channel to see what is on."), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                else {
                    val nn = nowNext[c.id]
                    PreviewPane(c, vm, settings?.livePreview != false)
                    Text(c.name, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(c.groupName, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (nn?.now == null && nn?.next == null) Text(tr("No guide data for this channel. Import an XMLTV guide or map the channel in Sources."), Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TvButton(tr("Match guide channel"), { mapDialog = true }, Modifier.padding(horizontal = 20.dp), primary = false)
                    nn?.now?.let { p ->
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Text("Now \u2022 ${formatTime(ctx, p.startMs)}\u2013${formatTime(ctx, p.endMs)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(p.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            ProgressLine(p.progress(System.currentTimeMillis()), Modifier.padding(top = 6.dp))
                        }
                    }
                    nn?.next?.let { p ->
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Text("Next \u2022 ${formatTime(ctx, p.startMs)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(p.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    playlistTarget?.let { t -> PlaylistPickerDialog(ContentKind.LIVE, t.id, t.name, { playlistTarget = null }) }
    val mapTarget = selected
    if (mapDialog && mapTarget != null) GuideMapDialog(mapTarget, vm) { mapDialog = false }
    if (sortDialog) OptionDialog(tr("Sort channels"), listOf(tr("Playlist order") to 0, tr("Name") to 1, tr("Channel number") to 2), settings?.channelSort, { vm.setSort(it) }, { sortDialog = false })
    if (sourceDialog) OptionDialog(tr("Source"), listOf(tr("All sources") to -1L) + sources.filter { it.type != "XMLTV_URL" && it.type != "XMLTV_FILE" }.map { it.name to it.id }, settings?.activeSourceId, { vm.setSource(it) }, { sourceDialog = false })
}

@Composable
private fun Modifier.background2(): Modifier = this.then(Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)))

@Composable
private fun GroupRow(name: String, count: Int?, selected: Boolean, entry: FocusRequester, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    // The selected category is where focus lands when the user presses Down from the top bar (never the search field).
    if (selected) RegisterContentEntry(entry)
    FocusSurface(onClick, Modifier.fillMaxWidth().height(Dimens.ButtonHeight), RoundedCornerShape(Dimens.ButtonHeight / 2), 1.03f, restBorder = false,
        containerColor = if (selected) cs.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        focusedContainerColor = if (selected) cs.primaryContainer else LocalIptvColors.current.elevated, focusRequester = if (selected) entry else null) {
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (selected) cs.onPrimaryContainer else cs.onSurface,
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else null)
            if (count != null) Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChannelRow(c: ChannelEntity, nn: NowNext?, fav: Boolean, restore: FocusRequester?, onFocus: () -> Unit, onPlay: () -> Unit, onFav: () -> Unit, onPlaylist: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        FocusSurface(onPlay, Modifier.weight(1f).height(72.dp), RoundedCornerShape(16.dp), 1.02f, restBorder = false, containerColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = LocalIptvColors.current.elevated, onFocused = onFocus, focusRequester = restore) {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(76.dp, 43.dp).clip(RoundedCornerShape(10.dp)).background(LocalIptvColors.current.elevated)) { Artwork(c.logo, c.name, Modifier.fillMaxSize().padding(6.dp)) }
                Column(Modifier.weight(1f)) {
                    Text((c.number?.let { "$it  " } ?: "") + c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    Text(nn?.now?.title ?: c.groupName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    nn?.now?.let { p -> ProgressLine(p.progress(System.currentTimeMillis()), Modifier.padding(top = 4.dp)) }
                }
            }
        }
        FavoriteButton(fav, onFav)
        IconOnlyButton(Icons.Filled.PlaylistAdd, tr("Add to playlist"), onPlaylist)
    }
}

/** Small muted live preview of the focused channel; falls back to the channel logo while loading, when disabled, or on error. */
@Composable
private fun PreviewPane(c: ChannelEntity, vm: LiveViewModel, enabled: Boolean) {
    val player by vm.repo.player.collectAsStateWithLifecycle()
    val st by vm.repo.state.collectAsStateWithLifecycle()
    val pid by vm.previewId.collectAsStateWithLifecycle()
    val mine = enabled && pid == c.id
    val playing = mine && player != null && (st.status == com.iptv.firetv.core.player.PlaybackStatus.READY || st.status == com.iptv.firetv.core.player.PlaybackStatus.BUFFERING)
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)).background(androidx.compose.ui.graphics.Color.Black)) {
        if (playing) androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx -> androidx.media3.ui.PlayerView(ctx).apply { useController = false; isFocusable = false; isFocusableInTouchMode = false; setShutterBackgroundColor(android.graphics.Color.BLACK) } },
            update = { it.player = player }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize()
        ) else Artwork(c.logo, c.name, Modifier.fillMaxSize().padding(18.dp))
        LiveBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
        if (mine && !playing && st.status == com.iptv.firetv.core.player.PlaybackStatus.ERROR)
            Text(tr("Preview unavailable"), Modifier.align(Alignment.BottomStart).padding(10.dp), style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.White)
        else if (mine && !playing) androidx.compose.material3.CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary)
    }
}

/** Lets the user pick the XMLTV channel for a playlist channel. Nothing is ever mapped from weak name similarity. */
@Composable
private fun GuideMapDialog(c: ChannelEntity, vm: LiveViewModel, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf(c.name) }
    var results by remember { mutableStateOf(emptyList<com.iptv.firetv.core.database.EpgChannelEntity>()) }
    var current by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(c.id) { current = vm.currentMapping(c.id) }
    LaunchedEffect(q) { kotlinx.coroutines.delay(250); results = vm.searchGuideChannels(q) }
    com.iptv.firetv.core.ui.TvDialog(onDismiss, "Guide channel for ${c.name}") {
        Text(if (current != null) "Mapped to: $current" else "Using automatic matching (tvg-id).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TvTextField(q, { q = it }, tr("Search guide channels"), Modifier.fillMaxWidth())
        Column(Modifier.height(260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (results.isEmpty()) Text(tr("No guide channels found. Import an XMLTV guide first."), style = MaterialTheme.typography.bodyLarge)
            results.forEach { e ->
                FocusSurface({ vm.setMapping(c.id, e.epgId); onDismiss() }, Modifier.fillMaxWidth().height(Dimens.OptionRowHeight), RoundedCornerShape(12.dp), 1.01f) {
                    Text(e.displayNames.substringBefore('\n').ifBlank { e.epgId } + "  \u2022  " + e.epgId, Modifier.padding(horizontal = 16.dp).align(Alignment.CenterStart), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (current != null) TvButton(tr("Clear mapping"), { vm.setMapping(c.id, null); onDismiss() }, primary = false)
            TvButton(tr("Close"), onDismiss, primary = false)
        }
    }
}
