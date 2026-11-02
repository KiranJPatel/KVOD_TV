package com.iptv.firetv.feature.search

import com.iptv.firetv.core.common.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.iptv.firetv.app.Routes
import com.iptv.firetv.core.ui.ChannelCard
import com.iptv.firetv.core.ui.Chip
import com.iptv.firetv.core.ui.FocusSurface
import com.iptv.firetv.core.ui.OnScreenKeyboard
import com.iptv.firetv.core.ui.PosterCard
import com.iptv.firetv.core.ui.ScreenFrame
import com.iptv.firetv.core.ui.SectionTitle
import com.iptv.firetv.core.ui.StatePanel
import com.iptv.firetv.core.ui.formatTime
import com.iptv.firetv.data.CatalogRepository
import com.iptv.firetv.data.SearchResults
import com.iptv.firetv.data.UserDataRepository
import com.iptv.firetv.feature.player.ChannelQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val catalog: CatalogRepository, private val user: UserDataRepository, val queue: ChannelQueue) : ViewModel() {
    val query = MutableStateFlow(com.iptv.firetv.app.SearchInbox.pendingQuery.value.also { com.iptv.firetv.app.SearchInbox.pendingQuery.value = null }.orEmpty())
    val types = MutableStateFlow(setOf("LIVE", "MOVIE", "SERIES", "EPG"))
    val recent: StateFlow<List<String>> = user.recentSearches().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Debounced; a new query cancels the previous lookup (flatMapLatest). Queries run on Room's IO executor via DAOs. */
    val results: StateFlow<SearchResults?> = combine(query.debounce(250).distinctUntilChanged(), types) { q, t -> q to t }.flatMapLatest { (q, t) ->
        flow { if (q.trim().length < 2) emit(null) else emit(catalog.search(q, -1, t)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun commit() { viewModelScope.launch { user.addSearch(query.value) } }
    fun clearHistory() { viewModelScope.launch { user.clearSearches() } }
    fun toggleType(t: String) { types.value = types.value.let { if (t in it && it.size > 1) it - t else it + t } }
}

@Composable
fun SearchScreen(nav: NavHostController, vm: SearchViewModel = hiltViewModel()) {
    val q by vm.query.collectAsStateWithLifecycle()
    val res by vm.results.collectAsStateWithLifecycle()
    val types by vm.types.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    val ctx = LocalContext.current
    // Voice search through the platform recognizer; the button only appears when a recognizer is installed (many Fire TV builds route voice through Alexa instead).
    val voiceIntent = remember { android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search channels, movies and series") }
    val voiceAvailable = remember { voiceIntent.resolveActivity(ctx.packageManager) != null }
    val voiceLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.query.value = it.take(80) }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val pending by com.iptv.firetv.app.SearchInbox.pendingQuery.collectAsStateWithLifecycle()
    LaunchedEffect(pending) { pending?.let { vm.query.value = it; com.iptv.firetv.app.SearchInbox.pendingQuery.value = null } }
    ScreenFrame(title = tr("Search")) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.width(470.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusSurface({ }, Modifier.fillMaxWidth().padding(bottom = 4.dp).then(Modifier), scale = 1f) {
                    Text(q.ifEmpty { "Type to search channels, movies, series and guide" }, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium, color = if (q.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }
                OnScreenKeyboard({ vm.query.value = q + it }, { vm.query.value = q.dropLast(1) }, { vm.query.value = "" }, firstFocus = first)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("LIVE" to "Live", "MOVIE" to "Movies", "SERIES" to "Series", "EPG" to "Guide").forEach { (k, l) -> Chip(tr(l), k in types, { vm.toggleType(k) }) }
                    if (voiceAvailable) Chip(tr("Voice"), false, { runCatching { voiceLauncher.launch(voiceIntent) } })
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                val r = res
                when {
                    r == null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (q.isEmpty()) "Recent searches" else "Keep typing\u2026 at least 2 characters", style = MaterialTheme.typography.titleMedium)
                        if (q.isEmpty()) {
                            recent.forEach { s -> Chip(s, false, { vm.query.value = s }) }
                            if (recent.isNotEmpty()) Chip(tr("Clear search history"), false, { vm.clearHistory() })
                        }
                    }
                    r.isEmpty -> StatePanel("No results for \u201C$q\u201D", tr("Try fewer letters or a different spelling. Restricted content is never shown."), Modifier.fillMaxSize())
                    else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (r.channels.isNotEmpty()) item {
                            SectionTitle(tr("Channels"))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(6.dp)) {
                                items(r.channels, key = { it.id }) { c -> ChannelCard(c.name, c.logo, c.groupName, { vm.commit(); vm.queue.set(r.channels.map { it.id }); nav.navigate(Routes.player("live", c.id)) }) }
                            }
                        }
                        if (r.movies.isNotEmpty()) item {
                            SectionTitle(tr("Movies"))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(6.dp)) { items(r.movies, key = { it.id }) { m -> PosterCard(m.title, m.poster, { vm.commit(); nav.navigate(Routes.movie(m.id)) }) } }
                        }
                        if (r.series.isNotEmpty()) item {
                            SectionTitle(tr("Series"))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(6.dp)) { items(r.series, key = { it.id }) { s -> PosterCard(s.title, s.poster, { vm.commit(); nav.navigate(Routes.series(s.id)) }) } }
                        }
                        if (r.programs.isNotEmpty()) {
                            item { SectionTitle(tr("On the guide")) }
                            items(r.programs, key = { it.program.id }) { h ->
                                FocusSurface({ vm.commit(); vm.queue.set(listOf(h.channel.id)); nav.navigate(Routes.player("live", h.channel.id)) }, Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(h.program.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                        Text("${h.channel.name}  \u2022  ${formatTime(ctx, h.program.startMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
