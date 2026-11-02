package com.iptv.firetv.feature.epg

import com.iptv.firetv.core.common.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.iptv.firetv.app.Routes
import com.iptv.firetv.core.database.ChannelEntity
import com.iptv.firetv.core.database.ProgramEntity
import com.iptv.firetv.core.ui.Artwork
import com.iptv.firetv.core.ui.Chip
import com.iptv.firetv.core.ui.FocusSurface
import com.iptv.firetv.core.ui.LocalIptvColors
import com.iptv.firetv.core.ui.ScreenFrame
import com.iptv.firetv.core.ui.SkeletonBox
import com.iptv.firetv.core.ui.StatePanel
import com.iptv.firetv.core.ui.TvButton
import com.iptv.firetv.core.ui.TvDialog
import com.iptv.firetv.core.ui.TvTextField
import com.iptv.firetv.core.ui.formatTime
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DpPerMinute = 5.dp   // 30 minutes = 150dp
private val ChannelCol = 180.dp
private val RowHeight = 64.dp

@Composable
fun EpgScreen(nav: NavHostController, vm: EpgViewModel = hiltViewModel()) {
    val g by vm.guide.collectAsStateWithLifecycle()
    val day by vm.dayOffset.collectAsStateWithLifecycle()
    val favOnly by vm.favoritesOnly.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sel by vm.selected.collectAsStateWithLifecycle()
    val selCh by vm.selectedChannel.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scroll = rememberScrollState()   // shared by the ruler and every row so they scroll together
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var searchDialog by remember { mutableStateOf(false) }
    val totalMinutes = ((g.windowEnd - g.windowStart) / 60_000L).toInt().coerceAtLeast(60)
    val totalWidth: Dp = DpPerMinute * totalMinutes
    val now = System.currentTimeMillis()

    fun jumpToNow() { scope.launch { val px = with(density) { (DpPerMinute * ((now - g.windowStart) / 60_000f - 20f).coerceAtLeast(0f)).toPx() }; scroll.animateScrollTo(px.toInt()) } }
    LaunchedEffect(g.windowStart, g.channels.size) { if (day == 0 && g.channels.isNotEmpty()) { kotlinx.coroutines.delay(150); val px = with(density) { (DpPerMinute * ((now - g.windowStart) / 60_000f - 20f).coerceAtLeast(0f)).toPx() }; scroll.scrollTo(px.toInt()) } }

    ScreenFrame {
        Column(Modifier.fillMaxSize()) {
            // One compact toolbar row: days scroll on the left, actions on the right. Search opens a dialog, so no row is taller than a chip.
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val fmt = remember { SimpleDateFormat("EEE d", Locale.getDefault()) }
                androidx.compose.foundation.lazy.LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
                    items(7) { d -> Chip(if (d == 0) tr("Today") else fmt.format(Date(System.currentTimeMillis() + d * 86_400_000L)), day == d, { vm.dayOffset.value = d }, dim = d >= g.availableDays && !g.loading) }
                }
                Chip(tr("Now"), false, { vm.dayOffset.value = 0; jumpToNow() })
                Chip(tr("Favorites"), favOnly, { vm.favoritesOnly.value = !favOnly })
                Chip(if (query.isBlank()) tr("Search") else tr("Search") + ": " + query.take(10), query.isNotBlank(), { searchDialog = true })
                Chip(tr("Live TV"), false, { nav.navigate(Routes.LIVE) })
            }
            // Details panel
            Box(Modifier.fillMaxWidth().height(88.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 8.dp)) {
                val p = sel
                if (p == null) Text(tr("Select a programme to see its details. Press OK to watch the channel."), Modifier.align(Alignment.CenterStart), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${p.title}${p.subTitle?.let { " \u2022 $it" } ?: ""}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${selCh?.name.orEmpty()}  \u2022  ${formatTime(ctx, p.startMs)}\u2013${formatTime(ctx, p.endMs)}${p.category?.let { "  \u2022  $it" } ?: ""}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        Text(p.description ?: "No description provided.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TvButton(tr("Watch channel"), { selCh?.let { vm.prepareQueue(g.channels); nav.navigate(Routes.player("live", it.id)) } }, icon = Icons.Filled.PlayArrow)
                }
            }
            Box(Modifier.fillMaxSize().padding(top = 8.dp)) {
                when {
                    g.loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(5) { SkeletonBox(Modifier.fillMaxWidth().height(RowHeight)) } }
                    g.channels.isEmpty() -> StatePanel(tr("No channels to show"), if (favOnly) "Star channels in Live TV to see them here." else "Import a playlist first.", Modifier.fillMaxSize()) { TvButton(tr("Open Live TV"), { nav.navigate(Routes.LIVE) }) }
                    !g.hasGuideData -> StatePanel(tr("Guide data is unavailable"), tr("No programmes were found for these channels on this day. Import an XMLTV guide in Playlists / Sources. Channels are matched by their tvg-id; check the EPG time zone in Settings if times look wrong."), Modifier.fillMaxSize()) {
                        TvButton(tr("Open sources"), { nav.navigate(Routes.SOURCES) })
                    }
                    else -> Column(Modifier.fillMaxSize()) {
                        // Time ruler
                        Row(Modifier.height(28.dp)) {
                            Box(Modifier.width(ChannelCol), contentAlignment = Alignment.CenterStart) { Text(g.zoneLabel, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            Box(Modifier.weight(1f).horizontalScroll(scroll, enabled = false)) {
                                Box(Modifier.width(totalWidth).height(28.dp)) {
                                    if (now in g.windowStart..g.windowEnd) Text(formatTime(ctx, now), Modifier.offset(x = DpPerMinute * ((now - g.windowStart) / 60_000f) - 24.dp).background(LocalIptvColors.current.live, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp),
                                        style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White)
                                    var t = g.windowStart - (g.windowStart % 1_800_000L)
                                    while (t < g.windowEnd) {
                                        if (t >= g.windowStart) Text(formatTime(ctx, t), Modifier.offset(x = DpPerMinute * ((t - g.windowStart) / 60_000f)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        t += 1_800_000L
                                    }
                                }
                            }
                        }
                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(g.channels, key = { it.id }) { ch ->
                                GuideRow(ch, g.programs[ch.id].orEmpty(), g.windowStart, g.windowEnd, totalWidth, now, scroll,
                                    onSelectProgram = { p -> vm.selected.value = p; vm.selectedChannel.value = ch },
                                    onPlay = { vm.prepareQueue(g.channels); nav.navigate(Routes.player("live", ch.id)) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (searchDialog) TvDialog({ searchDialog = false }, tr("Search channels")) {
        TvTextField(query, { vm.query.value = it }, tr("Channel name"), Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(tr("Done"), { searchDialog = false })
            TvButton(tr("Clear"), { vm.query.value = "" }, primary = false)
        }
    }
}

@Composable
private fun GuideRow(
    ch: ChannelEntity, programs: List<ProgramEntity>, start: Long, end: Long, totalWidth: Dp, now: Long,
    scroll: androidx.compose.foundation.ScrollState, onSelectProgram: (ProgramEntity) -> Unit, onPlay: () -> Unit
) {
    Row(Modifier.height(RowHeight)) {
        FocusSurface(onPlay, Modifier.width(ChannelCol - 8.dp).fillMaxHeight(), RoundedCornerShape(16.dp), 1.02f, restBorder = false) {
            Row(Modifier.padding(horizontal = 10.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Artwork(ch.logo, ch.name, Modifier.size(36.dp))
                Text(ch.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Box(Modifier.width(8.dp))
        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) { // enabled so focus can scroll rows into view
            Box(Modifier.width(totalWidth).fillMaxHeight()) {
                programs.forEach { p ->
                    val s = maxOf(p.startMs, start); val e = minOf(p.endMs, end)
                    if (e <= s) return@forEach
                    val x = DpPerMinute * ((s - start) / 60_000f)
                    val w = (DpPerMinute * ((e - s) / 60_000f) - 4.dp).coerceAtLeast(24.dp)
                    val current = now in p.startMs until p.endMs
                    FocusSurface(onPlay, Modifier.offset(x = x).width(w).fillMaxHeight(), RoundedCornerShape(14.dp), 1.02f,
                        containerColor = if (current) LocalIptvColors.current.nowTint else LocalIptvColors.current.elevated, onFocused = { onSelectProgram(p) }) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp).align(Alignment.CenterStart)) {
                            Text(p.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                            Text(formatTime(androidx.compose.ui.platform.LocalContext.current, p.startMs), style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // Current-time indicator
                if (now in start..end) Box(Modifier.offset(x = DpPerMinute * ((now - start) / 60_000f)).width(2.dp).fillMaxHeight().background(LocalIptvColors.current.live))
            }
        }
    }
}
