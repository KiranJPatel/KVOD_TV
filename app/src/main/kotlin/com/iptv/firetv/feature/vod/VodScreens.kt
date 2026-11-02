package com.iptv.firetv.feature.vod

import com.iptv.firetv.core.common.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.paging.compose.itemKey
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.iptv.firetv.app.Routes
import com.iptv.firetv.core.database.GroupCount
import com.iptv.firetv.core.ui.Artwork
import com.iptv.firetv.core.ui.Chip
import com.iptv.firetv.core.ui.FocusSurface
import com.iptv.firetv.core.ui.LocalIptvColors
import com.iptv.firetv.core.ui.PosterCard
import com.iptv.firetv.core.ui.ProgressLine
import com.iptv.firetv.core.ui.ScreenFrame
import com.iptv.firetv.core.model.ContentKind
import com.iptv.firetv.feature.playlists.PlaylistPickerDialog
import com.iptv.firetv.core.ui.SkeletonBox
import com.iptv.firetv.core.ui.StatePanel
import com.iptv.firetv.core.ui.TvButton
import com.iptv.firetv.core.ui.TvTextField
import com.iptv.firetv.core.ui.formatDuration
import androidx.compose.runtime.mutableStateOf

@Composable
private fun FilterBar(groups: List<GroupCount>, group: String?, onGroup: (String?) -> Unit, query: String, onQuery: (String) -> Unit, sort: Int, onSort: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            TvTextField(query, onQuery, tr("Search"), Modifier.width(360.dp))
            Chip("Sort: " + if (sort == 0) "Recently added" else "Name", false, { onSort(1 - sort) })
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
            item { Chip(tr("All"), group == null, { onGroup(null) }) }
            items(groups.take(60), key = { it.norm }) { g -> Chip("${g.name} (${g.count})", group == g.norm, { onGroup(g.norm) }) }
        }
    }
}

@Composable
fun MoviesScreen(nav: NavHostController, vm: MoviesViewModel = hiltViewModel()) {
    val items = vm.paged.collectAsLazyPagingItems()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val group by vm.group.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState(); val restore = remember { FocusRequester() }
    LaunchedEffect(items.itemCount) { items.itemSnapshotList.indexOfFirst { it?.id == vm.lastFocusedId }.takeIf { it >= 0 }?.let { grid.scrollToItem(it); kotlinx.coroutines.delay(60); runCatching { restore.requestFocus() } } }
    ScreenFrame {
        Column(Modifier.fillMaxSize()) {
            FilterBar(groups, group, { vm.group.value = it }, query, { vm.query.value = it }, sort, { vm.sort.value = it })
            when {
                items.itemCount == 0 && items.loadState.refresh is LoadState.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { repeat(6) { SkeletonBox(Modifier.width(168.dp).height(250.dp)) } }
                items.itemCount == 0 -> StatePanel(if (query.isNotBlank() || group != null) "No matching movies" else "No movies yet", tr("Movies appear when a playlist or Xtream source contains VOD entries."), Modifier.fillMaxSize()) {
                    TvButton(tr("Open sources"), { nav.navigate(Routes.SOURCES) }, icon = Icons.Filled.PlaylistAdd)
                }
                else -> LazyVerticalGrid(GridCells.Adaptive(176.dp), Modifier.fillMaxSize(), state = grid, contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(count = items.itemCount, key = items.itemKey { it.id }) { idx ->
                        val m = items[idx]
                        if (m != null) PosterCard(m.title, m.poster, { nav.navigate(Routes.movie(m.id)) }, if (m.id == vm.lastFocusedId) Modifier.focusRestore(restore) else Modifier, subtitle = listOfNotNull(m.year, m.rating).joinToString(" \u2022 ").ifEmpty { null }, onFocused = { vm.lastFocusedId = m.id })
                    }
                }
            }
        }
    }
}

private fun Modifier.focusRestore(r: FocusRequester): Modifier = this.focusRequester(r)

@Composable
fun SeriesScreen(nav: NavHostController, vm: SeriesViewModel = hiltViewModel()) {
    val items = vm.paged.collectAsLazyPagingItems()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val group by vm.group.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    ScreenFrame {
        Column(Modifier.fillMaxSize()) {
            FilterBar(groups, group, { vm.group.value = it }, query, { vm.query.value = it }, sort, { vm.sort.value = it })
            when {
                items.itemCount == 0 && items.loadState.refresh is LoadState.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { repeat(6) { SkeletonBox(Modifier.width(168.dp).height(250.dp)) } }
                items.itemCount == 0 -> StatePanel(if (query.isNotBlank() || group != null) "No matching series" else "No series yet", tr("Series appear when a source lists episodes (for example names like Show S01E02) or an Xtream series catalogue."), Modifier.fillMaxSize()) {
                    TvButton(tr("Open sources"), { nav.navigate(Routes.SOURCES) }, icon = Icons.Filled.PlaylistAdd)
                }
                else -> LazyVerticalGrid(GridCells.Adaptive(176.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(count = items.itemCount, key = items.itemKey { it.id }) { idx ->
                        val s = items[idx]
                        if (s != null) PosterCard(s.title, s.poster, { nav.navigate(Routes.series(s.id)) }, subtitle = listOfNotNull(s.year, s.rating).joinToString(" \u2022 ").ifEmpty { null }, onFocused = { vm.lastFocusedId = s.id })
                    }
                }
            }
        }
    }
}

@Composable
fun MovieDetailScreen(nav: NavHostController, vm: MovieDetailViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val play = remember { FocusRequester() }
    var pickPlaylist by remember { mutableStateOf(false) }
    LaunchedEffect(s.loaded) { if (s.loaded) runCatching { play.requestFocus() } }
    s.movie?.let { mv -> if (pickPlaylist) PlaylistPickerDialog(ContentKind.MOVIE, mv.id, mv.title, { pickPlaylist = false }) }
    Box(Modifier.fillMaxSize()) {
    s.movie?.backdrop?.let { b ->
        // Google TV-style backdrop: large artwork on the right fading into the page behind the text.
        Artwork(b, "", Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.72f).fillMaxHeight().graphicsLayer { alpha = 0.9f }, ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to MaterialTheme.colorScheme.background, 0.42f to MaterialTheme.colorScheme.background.copy(alpha = 0.94f), 0.85f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.7f to Color.Transparent, 1f to MaterialTheme.colorScheme.background)))
    }
    ScreenFrame {
        val m = s.movie
        when {
            !s.loaded -> SkeletonBox(Modifier.fillMaxSize())
            m == null -> StatePanel(tr("This title is not available"), tr("It may have been removed from the source or is restricted for the active profile."), Modifier.fillMaxSize()) { TvButton(tr("Back"), { nav.popBackStack() }, focusRequester = play) }
            else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                // Without a backdrop the poster stays on the left as before.
                if (m.backdrop == null) Artwork(m.poster, m.title, Modifier.width(300.dp).fillMaxHeight().background(LocalIptvColors.current.elevated, RoundedCornerShape(20.dp)), ContentScale.Fit)
                Column(Modifier.then(if (m.backdrop != null) Modifier.fillMaxWidth(0.55f) else Modifier.weight(1f)).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                    Text(m.title, style = MaterialTheme.typography.displayMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    MetaPills(listOfNotNull(m.year, m.durationSec?.let { formatDuration(it * 1000L) }, m.genre, m.rating?.let { "\u2605 $it" }))
                    Text(m.plot ?: "No description is available from this source.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 8, overflow = TextOverflow.Ellipsis)
                    if (s.attribution) Text(com.iptv.firetv.data.MetadataRepository.ATTRIBUTION, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val p = s.progress
                    if (p != null && !p.completed && p.positionMs > 10_000) ProgressLine(p.positionMs.toFloat() / p.durationMs, Modifier.width(360.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val resume = p != null && !p.completed && p.positionMs > 10_000
                        TvButton(if (resume) "Resume ${formatDuration(p!!.positionMs)}" else "Play", { nav.navigate(Routes.player("movie", m.id)) }, icon = Icons.Filled.PlayArrow, focusRequester = play)
                        TvButton(if (s.favorite) "In favorites" else "Add to favorites", { vm.toggleFavorite() }, primary = false, icon = if (s.favorite) Icons.Filled.Star else Icons.Filled.StarBorder)
                        TvButton(tr("Add to playlist"), { pickPlaylist = true }, primary = false, icon = Icons.Filled.PlaylistAdd)
                    }
                }
            }
        }
    }
    }
}

@Composable
fun SeriesDetailScreen(nav: NavHostController, vm: SeriesDetailViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val eps by vm.episodes.collectAsStateWithLifecycle()
    val prog by vm.progress.collectAsStateWithLifecycle()
    var season by remember { mutableIntStateOf(-1) }
    val seasons = eps.map { it.season }.distinct().sorted()
    val current = if (season in seasons) season else seasons.firstOrNull() ?: 1
    val first = remember { FocusRequester() }
    var pickPlaylist by remember { mutableStateOf(false) }
    s.series?.let { sr -> if (pickPlaylist) PlaylistPickerDialog(ContentKind.SERIES, sr.id, sr.title, { pickPlaylist = false }) }
    LaunchedEffect(eps.size) { vm.refreshProgress() }
    LaunchedEffect(s.loaded) { if (s.loaded) runCatching { first.requestFocus() } }
    ScreenFrame {
        val se = s.series
        when {
            !s.loaded -> SkeletonBox(Modifier.fillMaxSize())
            se == null -> StatePanel(tr("This series is not available"), tr("It may have been removed from the source or is restricted for the active profile."), Modifier.fillMaxSize()) { TvButton(tr("Back"), { nav.popBackStack() }, focusRequester = first) }
            else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Artwork(se.poster, se.title, Modifier.fillMaxWidth().height(360.dp).background(LocalIptvColors.current.elevated, RoundedCornerShape(16.dp)), ContentScale.Fit)
                    Text(se.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    MetaPills(listOfNotNull(se.year, se.genre, se.rating?.let { "\u2605 $it" }))
                    Text(se.plot ?: "No description is available from this source.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    if (s.attribution) Text(com.iptv.firetv.data.MetadataRepository.ATTRIBUTION, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TvButton(if (s.favorite) "In favorites" else "Add to favorites", { vm.toggleFavorite() }, primary = false, icon = if (s.favorite) Icons.Filled.Star else Icons.Filled.StarBorder)
                    TvButton(tr("Add to playlist"), { pickPlaylist = true }, primary = false, icon = Icons.Filled.PlaylistAdd)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        s.loadingEpisodes -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(5) { SkeletonBox(Modifier.fillMaxWidth().height(72.dp)) } }
                        s.error != null -> StatePanel(tr("Episodes could not be loaded"), s.error!!, Modifier.fillMaxWidth(), isError = true) { TvButton(tr("Retry"), { vm.retry() }, focusRequester = first) }
                        eps.isEmpty() -> StatePanel(tr("No episodes found"), tr("This source did not list any episodes for this series."), Modifier.fillMaxWidth()) { TvButton(tr("Back"), { nav.popBackStack() }, focusRequester = first) }
                        else -> {
                            val next = eps.firstOrNull { e -> prog[e.id]?.completed != true && e.season == current } ?: eps.first { it.season == current }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                seasons.forEach { n -> Chip("Season $n", n == current, { season = n }) }
                            }
                            TvButton(if ((prog[next.id]?.positionMs ?: 0) > 10_000) "Resume S${next.season}E${next.episode}" else "Play S${next.season}E${next.episode}", { nav.navigate(Routes.player("episode", next.id)) }, icon = Icons.Filled.PlayArrow, focusRequester = first)
                            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
                                items(eps.filter { it.season == current }, key = { it.id }) { e ->
                                    val p = prog[e.id]
                                    FocusSurface({ nav.navigate(Routes.player("episode", e.id)) }, Modifier.fillMaxWidth().height(76.dp), RoundedCornerShape(18.dp), 1.02f, restBorder = false, containerColor = androidx.compose.ui.graphics.Color.Transparent, focusedContainerColor = LocalIptvColors.current.elevated) {
                                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Text("E${e.episode}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                            Column(Modifier.weight(1f)) {
                                                Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                                Text(e.plot ?: e.durationSec?.let { formatDuration(it * 1000L) } ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (p != null && !p.completed && p.durationMs > 0) ProgressLine(p.positionMs.toFloat() / p.durationMs, Modifier.padding(top = 4.dp))
                                            }
                                            if (p?.completed == true) Text(tr("Watched"), style = MaterialTheme.typography.labelMedium, color = LocalIptvColors.current.success)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Year / duration / genre / rating as small pills (never shows an empty row). */
@Composable
private fun MetaPills(items: List<String>) {
    if (items.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { Text(it, Modifier.background(LocalIptvColors.current.elevated, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) }
    }
}
