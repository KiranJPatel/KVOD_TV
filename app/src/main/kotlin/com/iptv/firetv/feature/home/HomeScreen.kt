package com.iptv.firetv.feature.home

import com.iptv.firetv.core.common.tr
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.iptv.firetv.app.Routes
import com.iptv.firetv.core.database.SeriesEntity
import com.iptv.firetv.core.database.VodItemEntity
import com.iptv.firetv.core.ui.Artwork
import com.iptv.firetv.core.ui.ChannelCard
import com.iptv.firetv.core.ui.FocusSurface
import com.iptv.firetv.core.ui.LandscapeCard
import com.iptv.firetv.core.ui.LiveBadge
import com.iptv.firetv.core.ui.LocalIptvColors
import com.iptv.firetv.core.ui.PosterCard
import com.iptv.firetv.core.ui.ProgressLine
import com.iptv.firetv.core.ui.ScreenFrame
import com.iptv.firetv.core.ui.SkeletonRow
import com.iptv.firetv.core.ui.StatePanel
import com.iptv.firetv.core.ui.TvButton
import com.iptv.firetv.core.ui.TvRow
import com.iptv.firetv.core.ui.formatDuration

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.fillMaxHeight

@Composable
fun HomeScreen(nav: NavHostController, vm: HomeViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val override by vm.heroOverride.collectAsStateWithLifecycle()
    val visible by vm.visibleTabs.collectAsStateWithLifecycle()
    val showMovies = visible.first; val showSeries = visible.second
    val first = remember { FocusRequester() }
    LaunchedEffect(s.loading, s.hasSources) { if (!s.loading && !s.hasSources) runCatching { first.requestFocus() } }

    ScreenFrame {
        Column(Modifier.fillMaxSize()) {
            SourceStatus(s)
            when {
                s.loading -> SkeletonRow(Modifier.padding(top = 24.dp))
                !s.hasSources -> StatePanel(tr("Add your first playlist"), tr("KVOD TV plays content you are authorised to watch. Import your own M3U playlist, an Xtream-compatible account or an XMLTV guide."),
                    Modifier.fillMaxSize(), icon = Icons.Filled.PlaylistAdd) {
                    TvButton(tr("Add Your First Playlist"), { nav.navigate(Routes.SOURCES) }, icon = Icons.Filled.PlaylistAdd, focusRequester = first)
                    TvButton(tr("Try sample data"), { vm.loadSample() }, primary = false)
                }
                !s.hasContent -> StatePanel(tr("Your sources have no content yet"), tr("Open Playlists / Sources to refresh or check the status of each source."), Modifier.fillMaxSize()) {
                    TvButton(tr("Open sources"), { nav.navigate(Routes.SOURCES) })
                }
                else -> {
                    val hero = override ?: vm.defaultHero(s)
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (hero != null) item(key = "hero") { Hero(hero, onPlay = { nav.navigate(hero.route) }, onFav = { vm.toggleFavorite(hero) }) }
                        item(key = "live") { TvRow(tr("Live TV channels"), s.live, { it.id }) { c ->
                            ChannelCard(c.name, c.logo, s.nowNext[c.id]?.now?.title, { nav.navigate(Routes.player("live", c.id)) },
                                onFocused = { vm.focusHero(vm.heroOf(c, s.nowNext[c.id])) }, progress = s.nowNext[c.id]?.now?.progress(System.currentTimeMillis()))
                        } }
                        item(key = "cont") { TvRow(tr("Continue watching"), s.continueItems, { it.progress.itemId }) { c ->
                            val p = c.progress
                            LandscapeCard(p.title, remainingText(p.durationMs - p.positionMs), p.poster, if (p.durationMs > 0) p.positionMs.toFloat() / p.durationMs else null, { nav.navigate(c.route) })
                        } }
                        item(key = "fav") { TvRow(tr("Favorite channels"), s.favorites, { it.id }) { c -> ChannelCard(c.name, c.logo, s.nowNext[c.id]?.now?.title, { nav.navigate(Routes.player("live", c.id)) }) } }
                        item(key = "recent") { TvRow(tr("Recently watched"), s.recent, { it.id }) { c -> ChannelCard(c.name, c.logo, c.groupName, { nav.navigate(Routes.player("live", c.id)) }) } }
                        if (showMovies) item(key = "movies") { TvRow(tr("Movies"), s.movies, { it.id }) { m -> PosterCard(m.title, m.poster, { nav.navigate(Routes.movie(m.id)) }, onFocused = { vm.focusHero(vm.heroOf(m)) }) } }
                        if (showSeries) item(key = "series") { TvRow(tr("Series"), s.series, { it.id }) { x -> PosterCard(x.title, x.poster, { nav.navigate(Routes.series(x.id)) }, onFocused = { vm.focusHero(vm.heroOf(x)) }) } }
                        if (showMovies || showSeries) item(key = "added") { TvRow(tr("Recently added"), s.recentlyAdded.filter { (it is VodItemEntity && showMovies) || (it is SeriesEntity && showSeries) }, { (it as? VodItemEntity)?.id ?: (it as SeriesEntity).id }) { any ->
                            when (any) {
                                is VodItemEntity -> PosterCard(any.title, any.poster, { nav.navigate(Routes.movie(any.id)) })
                                is SeriesEntity -> PosterCard(any.title, any.poster, { nav.navigate(Routes.series(any.id)) })
                            }
                        } }
                    }
                }
            }
        }
    }
}

private fun remainingText(ms: Long): String? = if (ms <= 0) null else formatDuration(ms) + " left"

/** Small "sources up to date / need attention" line; search, clock and profile now live in the app-level top bar. */
@Composable
private fun SourceStatus(s: HomeState) {
    if (s.sourcesTotal <= 0) return
    val c = LocalIptvColors.current
    val ok = s.sourcesFailed == 0
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) c.success else c.warning))
        Text(if (ok) "${s.sourcesTotal} source${if (s.sourcesTotal == 1) "" else "s"} up to date" else "${s.sourcesFailed} source${if (s.sourcesFailed == 1) "" else "s"} need attention",
            Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Google TV-style hero: a soft backdrop that fades into the page, title and actions on the left. Changes crossfade
 * (200 ms) as focus moves along the rows. Live items show the channel logo and a "now" line with progress.
 */
@Composable
private fun Hero(h: HeroItem, onPlay: () -> Unit, onFav: () -> Unit) {
    val colors = LocalIptvColors.current
    val cs = MaterialTheme.colorScheme
    Crossfade(targetState = h, animationSpec = tween(200), label = "hero") { item ->
        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp).height(290.dp).clip(RoundedCornerShape(24.dp))) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(cs.primaryContainer, cs.background), center = Offset(1500f, 140f), radius = 1500f)))
            if (item.isLive) Artwork(item.image, item.title, Modifier.align(Alignment.CenterEnd).padding(end = 100.dp).size(150.dp).alpha(0.9f), ContentScale.Fit)
            else if (!item.image.isNullOrBlank()) Artwork(item.image, item.title, Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.62f).fillMaxHeight(), ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to cs.background, 0.42f to cs.background.copy(alpha = 0.92f), 0.8f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.65f to Color.Transparent, 1f to cs.background.copy(alpha = 0.85f))))
            Column(Modifier.fillMaxWidth(0.54f).fillMaxHeight().padding(start = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (item.isLive) LiveBadge()
                    Text(item.tag, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(item.title, style = MaterialTheme.typography.headlineMedium, color = cs.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.isLive && item.channelName != null) {
                    Text(listOfNotNull(item.channelName, item.nowLine).joinToString("  \u2022  "), style = MaterialTheme.typography.titleSmall, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.progress != null) ProgressLine(item.progress, Modifier.width(360.dp))
                }
                Text(item.description, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(item.action, onPlay, icon = Icons.Filled.PlayArrow)
                    if (item.favId != null) TvButton(tr("Favorite"), onFav, primary = false, icon = Icons.Filled.Star)
                }
            }
        }
    }
}
