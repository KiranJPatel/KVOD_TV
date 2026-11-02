package com.iptv.firetv.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import com.iptv.firetv.R
import com.iptv.firetv.core.common.tr
import com.iptv.firetv.core.ui.Dimens
import com.iptv.firetv.core.ui.FocusSurface
import com.iptv.firetv.core.ui.LocalContentEntry
import com.iptv.firetv.core.ui.LocalIptvColors
import com.iptv.firetv.core.ui.NavItem
import com.iptv.firetv.core.ui.rememberClock

private data class TopTab(val route: String, val label: String)

/** Primary destinations shown as pills. Movies and Series can be hidden in Settings > Menu & navigation. */
private fun topTabs(showMovies: Boolean, showSeries: Boolean): List<TopTab> = buildList {
    add(TopTab(Routes.HOME, "Home")); add(TopTab(Routes.LIVE, "Live TV"))
    if (showMovies) add(TopTab(Routes.MOVIES, "Movies"))
    if (showSeries) add(TopTab(Routes.SERIES, "Series"))
    add(TopTab(Routes.EPG, "Guide")); add(TopTab(Routes.FAVORITES, "Library"))
}

/**
 * Top bar: brand, centred pill tabs, then search, settings, menu, clock and profile. Every element is a normal focusable,
 * so D-pad Left/Right walks the bar. Down enters the content at the point the current screen registered
 * ([com.iptv.firetv.core.ui.RegisterContentEntry]) instead of whichever control happens to be geometrically nearest.
 */
@Composable
fun TopBar(current: String?, nav: NavHostController, liveDot: Boolean, profileInitial: String, showMovies: Boolean = true, showSeries: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val time by rememberClock()
    var menu by remember { mutableStateOf(false) }
    val entry = LocalContentEntry.current
    // Evaluated at focus-search time, so it always reflects the screen that is showing now.
    val enterDown = Modifier.focusProperties { down = entry.requester ?: FocusRequester.Default }
    Row(
        Modifier.fillMaxWidth().padding(start = Dimens.SafeH, end = Dimens.SafeH, top = Dimens.SafeV, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.ic_kvod_logo), null, Modifier.size(Dimens.CircleButton + 4.dp).clip(RoundedCornerShape(12.dp)))
            Text("KVOD TV", style = MaterialTheme.typography.titleLarge, color = cs.onBackground, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            topTabs(showMovies, showSeries).forEach { t ->
                val selected = current == t.route
                FocusSurface(
                    onClick = { if (!selected) nav.navigateTop(t.route) },
                    modifier = enterDown.height(Dimens.TabHeight), shape = RoundedCornerShape(Dimens.TabHeight / 2), scale = 1.04f, restBorder = false,
                    containerColor = if (selected) cs.onBackground else Color.Transparent,
                    focusedContainerColor = if (selected) cs.onBackground else LocalIptvColors.current.elevated
                ) { focused ->
                    Row(Modifier.align(Alignment.Center).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr(t.label), style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else null, maxLines = 1,
                            color = when { selected -> cs.background; focused -> cs.onSurface; else -> cs.onSurfaceVariant })
                        if (t.route == Routes.LIVE && liveDot) Box(Modifier.size(9.dp).background(LocalIptvColors.current.live, CircleShape))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            RoundTopButton({ nav.navigateTop(Routes.SEARCH) }, enterDown) { Icon(Icons.Filled.Search, tr("Search"), Modifier.size(Dimens.IconCircle), tint = cs.onSurface) }
            RoundTopButton({ nav.navigateTop(Routes.SETTINGS) }, enterDown) { Icon(Icons.Filled.Settings, tr("Settings"), Modifier.size(Dimens.IconCircle), tint = if (current == Routes.SETTINGS) cs.primary else cs.onSurface) }
            RoundTopButton({ menu = true }, enterDown) { Icon(Icons.Filled.Apps, tr("Menu"), Modifier.size(Dimens.IconCircle), tint = cs.onSurface) }
            Text(time, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            RoundTopButton({ nav.navigate(Routes.PROFILE_CHOOSE) }, enterDown, container = cs.primary) { Text(profileInitial, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = cs.onPrimary) }
        }
    }
    if (menu) MenuSheet({ menu = false }) { route, top ->
        menu = false
        if (top) nav.navigateTop(route) else nav.navigate(route)
    }
}

/** Right-hand side sheet with the less frequent destinations. Replaces the old stack of full-width buttons in a centred dialog. */
@Composable
private fun MenuSheet(onDismiss: () -> Unit, onGo: (route: String, topLevel: Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(horizontal = Dimens.SafeH, vertical = Dimens.SafeV), contentAlignment = Alignment.TopEnd) {
            val shape = RoundedCornerShape(Dimens.PanelRadius)
            Column(Modifier.width(380.dp).padding(top = 64.dp).shadow(24.dp, shape).clip(shape).background(cs.surface).border(1.dp, LocalIptvColors.current.divider, shape).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("Menu"), Modifier.padding(start = 12.dp, bottom = 8.dp), style = MaterialTheme.typography.headlineSmall, color = cs.onSurface)
                NavItem(tr("Playlists / Sources"), Icons.Filled.PlaylistAdd, false, { onGo(Routes.SOURCES, true) }, focusRequester = first)
                NavItem(tr("My playlists"), Icons.Filled.PlaylistPlay, false, { LibraryInbox.pendingTab.value = LibraryInbox.TAB_PLAYLISTS; onGo(Routes.FAVORITES, true) })
                NavItem(tr("Local media"), Icons.Filled.Folder, false, { onGo(Routes.LOCAL, true) })
                NavItem(tr("Profiles"), Icons.Filled.Person, false, { onGo(Routes.PROFILE_CHOOSE, false) })
                NavItem(tr("Settings"), Icons.Filled.Settings, false, { onGo(Routes.SETTINGS, true) })
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun RoundTopButton(onClick: () -> Unit, modifier: Modifier = Modifier, container: Color = LocalIptvColors.current.elevated, content: @Composable () -> Unit) {
    FocusSurface(onClick, modifier.size(Dimens.CircleButton), CircleShape, scale = 1.08f, containerColor = container, restBorder = false) {
        Box(Modifier.align(Alignment.Center)) { content() }
    }
}
