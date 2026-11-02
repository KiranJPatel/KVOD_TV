package com.iptv.firetv.app

import com.iptv.firetv.core.common.tr
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iptv.firetv.core.ui.Dimens
import com.iptv.firetv.core.ui.FocusSurface
import com.iptv.firetv.core.ui.LocalIptvColors
import com.iptv.firetv.feature.epg.EpgScreen
import com.iptv.firetv.feature.favorites.FavoritesScreen
import com.iptv.firetv.feature.home.HomeScreen
import com.iptv.firetv.feature.live.LiveScreen
import com.iptv.firetv.feature.player.PlayerScreen
import com.iptv.firetv.feature.profiles.ProfileChooserScreen
import com.iptv.firetv.feature.profiles.ProfileManageScreen
import com.iptv.firetv.feature.search.SearchScreen
import com.iptv.firetv.feature.settings.SettingsScreen
import com.iptv.firetv.feature.sources.AddSourceChooserScreen
import com.iptv.firetv.feature.sources.AddSourceScreen
import com.iptv.firetv.feature.sources.LocalMediaScreen
import com.iptv.firetv.feature.sources.SourcesScreen
import com.iptv.firetv.feature.sources.WelcomeScreen
import com.iptv.firetv.feature.vod.MovieDetailScreen
import com.iptv.firetv.feature.vod.MoviesScreen
import com.iptv.firetv.feature.vod.SeriesDetailScreen
import com.iptv.firetv.feature.vod.SeriesScreen

fun NavHostController.navigateTop(route: String) = navigate(route) {
    popUpTo(Routes.HOME) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun IptvRoot(vm: MainViewModel) {
    val start by vm.startRoute.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val shared by ShareInbox.pendingUrl.collectAsStateWithLifecycle()
    val liveDot by vm.liveDot.collectAsStateWithLifecycle()
    val initial by vm.profileInitial.collectAsStateWithLifecycle()
    val inPip by PipState.active.collectAsStateWithLifecycle()
    val pendingSearch by SearchInbox.pendingQuery.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    // Visited top-level screens, newest last. Back walks this list, so it returns to the place the user came from
    // (not always Home), and each screen keeps its scroll position and filters through navigateTop's save/restore state.
    val history = remember { mutableStateListOf<String>() }
    var skipRecord by remember { mutableStateOf<String?>(null) }
    val contentEntry = remember { com.iptv.firetv.core.ui.ContentEntry() }
    LaunchedEffect(route) {
        val r = route ?: return@LaunchedEffect
        if (r !in topLevelRoutes) return@LaunchedEffect
        if (skipRecord == r) { skipRecord = null; return@LaunchedEffect }
        if (history.lastOrNull() != r) { history.remove(r); history.add(r); while (history.size > 12) history.removeAt(0) }
    }
    LaunchedEffect(pendingSearch, start) { if (pendingSearch != null && start != null) nav.navigate(Routes.SEARCH) { launchSingleTop = true } }

    LaunchedEffect(Unit) { vm.lockEvents.collect { history.clear(); nav.navigate(Routes.PROFILE_CHOOSE) { popUpTo(0) } } }
    LaunchedEffect(shared, start) { if (shared != null && start != null) nav.navigate(Routes.addSource("M3U_URL")) { launchSingleTop = true } }

    val s = start ?: return Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    val showRail = route in topLevelRoutes && !inPip

    androidx.compose.runtime.CompositionLocalProvider(com.iptv.firetv.core.ui.LocalTopBarVisible provides showRail, com.iptv.firetv.core.ui.LocalContentEntry provides contentEntry) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (showRail) TopBar(route, nav, liveDot, initial, settings?.showMoviesTab != false, settings?.showSeriesTab != false)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            NavHost(nav, startDestination = s) {
                composable(Routes.WELCOME) { WelcomeScreen(onDone = { nav.navigate(Routes.HOME) { popUpTo(0) } }, onAdd = { nav.navigate(Routes.addSource(it)) }) }
                composable(Routes.PROFILE_CHOOSE) { ProfileChooserScreen(onEntered = { nav.navigate(Routes.HOME) { popUpTo(0) } }, onManage = { nav.navigate(Routes.PROFILE_MANAGE) }) }
                composable(Routes.PROFILE_MANAGE) { ProfileManageScreen() }
                composable(Routes.HOME) { HomeScreen(nav) }
                composable(Routes.SEARCH) { SearchScreen(nav) }
                composable(Routes.LIVE) { LiveScreen(nav) }
                composable(Routes.MOVIES) { MoviesScreen(nav) }
                composable(Routes.SERIES) { SeriesScreen(nav) }
                composable(Routes.EPG) { EpgScreen(nav) }
                composable(Routes.FAVORITES) { FavoritesScreen(nav) }
                composable(Routes.SOURCES) { SourcesScreen(nav) }
                composable(Routes.LOCAL) { LocalMediaScreen(nav) }
                composable(Routes.SETTINGS) { SettingsScreen(nav) }
                composable(Routes.ADD_CHOOSER) { AddSourceChooserScreen(nav) }
                composable(Routes.ADD_SOURCE, arguments = listOf(navArgument("type") { type = NavType.StringType }, navArgument("editId") { type = NavType.LongType })) { AddSourceScreen(nav) }
                composable(Routes.PLAYER, arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType })) { PlayerScreen(nav) }
                composable(Routes.MOVIE_DETAIL) { MovieDetailScreen(nav) }
                composable(Routes.SERIES_DETAIL) { SeriesDetailScreen(nav) }
            }
        }
    }
    // Declared after the NavHost on purpose: the most recently registered back callback wins, so this one runs before NavHost's own
    // "pop one entry" handling (which would always return to Home, because tab switches replace each other on the stack).
    // Back on a top-level screen returns to the previously visited one; with nothing earlier the system takes over (leaves the app).
    BackHandler(enabled = showRail && history.size > 1 && history.lastOrNull() == route) {
        history.removeAt(history.lastIndex)
        val target = history.last()
        skipRecord = target
        nav.navigateTop(target)
    }

    }
}
